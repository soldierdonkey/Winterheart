//! --generate: writes mods.json from the jars in mods/ and the CurseForge launcher's metadata.

use crate::manifest::{self, Entry, Manifest, Source};
use serde_json::{json, Value};
use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::path::Path;

const LAUNCHER_FILE: &str = "minecraftinstance.json";

/// Folder names in mod-src/. Their jars are ours and stay in git.
fn own_mods(root: &Path) -> Vec<String> {
    let Ok(read) = fs::read_dir(root.join("mod-src")) else { return Vec::new() };
    read.filter_map(Result::ok)
        .filter(|e| e.path().is_dir())
        .filter_map(|e| e.file_name().into_string().ok())
        .filter(|n| !n.starts_with('_'))
        .collect()
}

/// True for <mod_id>-<version>.jar.
fn is_own(file: &str, own: &[String]) -> bool {
    own.iter().any(|id| {
        file.strip_prefix(id.as_str())
            .and_then(|rest| rest.strip_prefix('-'))
            .is_some_and(|version| version.starts_with(|c: char| c.is_ascii_digit()))
    })
}

/// Jars that .gitignore names one by one are not shipped with the pack.
fn unshipped(root: &Path) -> Vec<String> {
    fs::read_to_string(root.join(".gitignore"))
        .unwrap_or_default()
        .lines()
        .filter_map(|l| l.trim().strip_prefix("mods/"))
        .filter(|n| n.ends_with(".jar") && !n.contains('*'))
        .map(str::to_string)
        .collect()
}

/// The launcher's addons, keyed by file name on disk.
fn launcher_addons(root: &Path) -> Result<HashMap<String, Value>, String> {
    let path = root.join(LAUNCHER_FILE);
    let text = fs::read_to_string(&path).map_err(|e| format!("can't read {}: {e}", path.display()))?;
    let data: Value = serde_json::from_str(&text).map_err(|e| format!("{LAUNCHER_FILE}: {e}"))?;
    let addons = data["installedAddons"].as_array().ok_or_else(|| format!("{LAUNCHER_FILE} has no installedAddons"))?;
    Ok(addons
        .iter()
        .filter_map(|a| {
            let name = a["fileNameOnDisk"].as_str().or_else(|| a["installedFile"]["fileName"].as_str())?;
            Some((name.to_string(), a.clone()))
        })
        .collect())
}

/// SHA-1 -> (download URL, version page) for the hashes Modrinth knows.
fn modrinth(hashes: &[&str]) -> Result<HashMap<String, (String, String)>, String> {
    if hashes.is_empty() {
        return Ok(HashMap::new());
    }
    let response: Value = manifest::agent()
        .post("https://api.modrinth.com/v2/version_files")
        .send_json(json!({ "hashes": hashes, "algorithm": "sha1" }))
        .map_err(|e| format!("Modrinth lookup failed: {e}"))?
        .into_json()
        .map_err(|e| format!("Modrinth lookup failed: {e}"))?;
    let mut found = HashMap::new();
    for &hash in hashes {
        let version = &response[hash];
        let url = version["files"]
            .as_array()
            .and_then(|files| files.iter().find(|f| f["hashes"]["sha1"] == hash))
            .and_then(|f| f["url"].as_str());
        if let (Some(url), Some(project), Some(id)) = (url, version["project_id"].as_str(), version["id"].as_str()) {
            found.insert(hash.to_string(), (url.to_string(), format!("https://modrinth.com/mod/{project}/version/{id}")));
        }
    }
    Ok(found)
}

pub fn run(root: &Path) -> Result<(), String> {
    let path = root.join(manifest::FILE);
    let previous: BTreeMap<String, Entry> = if path.exists() {
        manifest::load(&path)?.mods.into_iter().map(|e| (e.file.clone(), e)).collect()
    } else {
        BTreeMap::new()
    };
    let mods = root.join("mods");
    let names = manifest::jars(&mods)?;
    if names.is_empty() {
        return Err("mods/ has no jars; refusing to write an empty manifest".into());
    }
    let addons = launcher_addons(root)?;
    let (own, skip) = (own_mods(root), unshipped(root));

    let mut entries: Vec<Entry> = Vec::new();
    // Entries that need Modrinth to decide their source.
    let mut unresolved: Vec<Entry> = Vec::new();
    for name in names.iter().filter(|n| !skip.contains(n)) {
        // "repo" is also how a jar is pinned to git by hand, so it survives regeneration.
        if is_own(name, &own) || previous.get(name).is_some_and(|e| e.source == Source::Repo) {
            entries.push(Entry::repo(name));
            continue;
        }
        let jar = mods.join(name);
        let sha1 = manifest::sha1_file(&jar).map_err(|e| format!("can't read {name}: {e}"))?;
        if let Some(old) = previous.get(name).filter(|e| e.sha1.as_deref() == Some(sha1.as_str())) {
            entries.push(old.clone());
            continue;
        }
        let size = fs::metadata(&jar).map_err(|e| format!("can't read {name}: {e}"))?.len();
        let mut entry = Entry { sha1: Some(sha1.clone()), size: Some(size), source: Source::Manual, ..Entry::repo(name) };
        // Only trust launcher data that describes these exact bytes (algo 1 is SHA-1).
        let addon = addons.get(name).filter(|a| {
            a["installedFile"]["hashes"].as_array().is_some_and(|h| h.iter().any(|h| h["type"] == 1 && h["value"] == sha1.as_str()))
        });
        if let Some(addon) = addon {
            entry.project_id = addon["addonID"].as_u64();
            entry.file_id = addon["installedFile"]["id"].as_u64();
            entry.page = addon["webSiteURL"].as_str().zip(entry.file_id).map(|(site, id)| format!("{site}/files/{id}"));
            let url = addon["installedFile"]["downloadUrl"].as_str();
            if addon["allowModDistribution"] != false && url.is_some() {
                entry.source = Source::Curseforge;
                entry.url = url.map(str::to_string);
                entries.push(entry);
                continue;
            }
        }
        unresolved.push(entry);
    }

    let hashes: Vec<&str> = unresolved.iter().filter_map(|e| e.sha1.as_deref()).collect();
    let found = modrinth(&hashes)?;
    let mut unknown = Vec::new();
    for mut entry in unresolved {
        if let Some((url, page)) = entry.sha1.as_ref().and_then(|h| found.get(h)) {
            entry.source = Source::Modrinth;
            entry.url = Some(url.clone());
            entry.page = Some(page.clone());
        } else if entry.page.is_none() {
            unknown.push(entry.file.clone());
            continue;
        }
        entries.push(entry);
    }
    if !unknown.is_empty() {
        return Err(format!(
            "no download source for: {}\nInstall them through the CurseForge app, or add {{\"file\": \"...\", \"source\": \"repo\"}} to {} and commit the jar.",
            unknown.join(", "),
            manifest::FILE
        ));
    }
    entries.sort_by_key(|e| e.file.to_lowercase());

    for entry in &entries {
        match previous.get(&entry.file) {
            None => println!("+ {} ({:?})", entry.file, entry.source),
            Some(old) if old.sha1 != entry.sha1 || old.source != entry.source => println!("~ {} ({:?})", entry.file, entry.source),
            Some(_) => {}
        }
    }
    for name in previous.keys().filter(|n| !entries.iter().any(|e| &e.file == *n)) {
        println!("- {name}");
    }
    let count = |s| entries.iter().filter(|e| e.source == s).count();
    let summary = format!(
        "{}: {} mods ({} curseforge, {} modrinth, {} manual, {} repo)",
        manifest::FILE,
        entries.len(),
        count(Source::Curseforge),
        count(Source::Modrinth),
        count(Source::Manual),
        count(Source::Repo)
    );
    let text = serde_json::to_string_pretty(&Manifest { mods: entries }).map_err(|e| e.to_string())?;
    fs::write(&path, text + "\n").map_err(|e| format!("can't write {}: {e}", path.display()))?;
    println!("{summary}");
    Ok(())
}
