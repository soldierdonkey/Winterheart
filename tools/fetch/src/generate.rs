//! --generate: writes mods.json from mods/, resourcepacks/ and the CurseForge launcher's metadata.

use crate::manifest::{self, Entry, Folder, Manifest, Source, FOLDERS, MODS};
use serde_json::{json, Value};
use std::collections::{BTreeMap, HashMap};
use std::fs;
use std::path::Path;
use std::process::Command;

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

/// Files that .gitignore names one by one are not shipped with the pack.
fn unshipped(root: &Path, folder: Folder) -> Vec<String> {
    let prefix = format!("{}/", folder.dir);
    fs::read_to_string(root.join(".gitignore"))
        .unwrap_or_default()
        .lines()
        .filter_map(|l| l.trim().strip_prefix(prefix.as_str()))
        .filter(|n| n.ends_with(folder.suffix) && !n.contains('*'))
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

/// A "repo" file that .gitignore hides would never reach anyone else.
fn warn_ignored(root: &Path, kept: &[String]) {
    // check-ignore prints the ignored paths; tracked files are never reported.
    let Ok(output) = Command::new("git").arg("-C").arg(root).args(["check-ignore", "--"]).args(kept).output() else { return };
    for path in String::from_utf8_lossy(&output.stdout).lines() {
        println!("warning: {path} is a repo file but .gitignore hides it; add \"!{path}\" to .gitignore");
    }
}

/// One folder's new entries.
#[derive(Default)]
struct Scan {
    previous: BTreeMap<String, Entry>,
    entries: Vec<Entry>,
    /// Entries that need Modrinth to decide their source.
    unresolved: Vec<Entry>,
}

fn scan(root: &Path, folder: Folder, old: &[Entry], addons: &HashMap<String, Value>) -> Result<Scan, String> {
    let mut scan = Scan { previous: old.iter().map(|e| (e.file.clone(), e.clone())).collect(), ..Scan::default() };
    let names = manifest::files(root, folder)?;
    if names.is_empty() && (folder == MODS || !old.is_empty()) {
        return Err(format!("{}/ has no {} files; refusing to empty the manifest", folder.dir, folder.suffix));
    }
    let own = if folder == MODS { own_mods(root) } else { Vec::new() };
    let skip = unshipped(root, folder);
    for name in names.iter().filter(|n| !skip.contains(n)) {
        // "repo" is also how a file is pinned to git by hand, so it survives regeneration.
        if is_own(name, &own) || scan.previous.get(name).is_some_and(|e| e.source == Source::Repo) {
            scan.entries.push(Entry::repo(name));
            continue;
        }
        let path = root.join(folder.dir).join(name);
        let sha1 = manifest::sha1_file(&path).map_err(|e| format!("can't read {name}: {e}"))?;
        if let Some(old) = scan.previous.get(name).filter(|e| e.sha1.as_deref() == Some(sha1.as_str())) {
            scan.entries.push(old.clone());
            continue;
        }
        let size = fs::metadata(&path).map_err(|e| format!("can't read {name}: {e}"))?.len();
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
                scan.entries.push(entry);
                continue;
            }
        }
        scan.unresolved.push(entry);
    }
    Ok(scan)
}

fn report(folder: Folder, scan: &Scan) {
    for entry in &scan.entries {
        match scan.previous.get(&entry.file) {
            None => println!("+ {}/{} ({:?})", folder.dir, entry.file, entry.source),
            Some(old) if old.sha1 != entry.sha1 || old.source != entry.source => {
                println!("~ {}/{} ({:?})", folder.dir, entry.file, entry.source)
            }
            Some(_) => {}
        }
    }
    for name in scan.previous.keys().filter(|n| !scan.entries.iter().any(|e| &e.file == *n)) {
        println!("- {}/{name}", folder.dir);
    }
    let count = |s| scan.entries.iter().filter(|e| e.source == s).count();
    println!(
        "{}: {} files ({} curseforge, {} modrinth, {} manual, {} repo)",
        folder.dir,
        scan.entries.len(),
        count(Source::Curseforge),
        count(Source::Modrinth),
        count(Source::Manual),
        count(Source::Repo)
    );
}

pub fn run(root: &Path) -> Result<(), String> {
    let path = root.join(manifest::FILE);
    let previous = if path.exists() { manifest::load(&path)? } else { Manifest::default() };
    let addons = launcher_addons(root)?;
    let mut scans = Vec::new();
    for folder in FOLDERS {
        scans.push((folder, scan(root, folder, previous.entries(folder), &addons)?));
    }

    let hashes: Vec<&str> = scans.iter().flat_map(|(_, s)| &s.unresolved).filter_map(|e| e.sha1.as_deref()).collect();
    let found = modrinth(&hashes)?;
    let mut unknown = Vec::new();
    for (folder, scan) in &mut scans {
        for mut entry in std::mem::take(&mut scan.unresolved) {
            if let Some((url, page)) = entry.sha1.as_ref().and_then(|h| found.get(h)) {
                entry.source = Source::Modrinth;
                entry.url = Some(url.clone());
                entry.page = Some(page.clone());
            } else if entry.page.is_none() {
                unknown.push(format!("{}/{}", folder.dir, entry.file));
                continue;
            }
            scan.entries.push(entry);
        }
        scan.entries.sort_by_key(|e| e.file.to_lowercase());
    }
    if !unknown.is_empty() {
        return Err(format!(
            "no download source for: {}\nInstall them through the CurseForge app, or add {{\"file\": \"...\", \"source\": \"repo\"}} to {} and commit the file.",
            unknown.join(", "),
            manifest::FILE
        ));
    }

    let mut next = Manifest::default();
    let mut kept = Vec::new();
    for (folder, scan) in scans {
        report(folder, &scan);
        kept.extend(scan.entries.iter().filter(|e| e.source == Source::Repo).map(|e| format!("{}/{}", folder.dir, e.file)));
        if folder == MODS {
            next.mods = scan.entries;
        } else {
            next.resourcepacks = scan.entries;
        }
    }
    let text = serde_json::to_string_pretty(&next).map_err(|e| e.to_string())?;
    fs::write(&path, text + "\n").map_err(|e| format!("can't write {}: {e}", path.display()))?;
    warn_ignored(root, &kept);
    Ok(())
}
