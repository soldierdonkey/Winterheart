//! The mods.json format, and the helpers both commands use.

use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use std::fs::{self, File};
use std::io;
use std::path::Path;
use std::time::Duration;

pub const FILE: &str = "mods.json";
const USER_AGENT: &str = "winterheart-fetch-mods";

#[derive(Serialize, Deserialize, Clone, Copy, PartialEq, Eq, Debug)]
#[serde(rename_all = "lowercase")]
pub enum Source {
    /// Downloaded from the CurseForge CDN.
    Curseforge,
    /// Downloaded from the Modrinth CDN.
    Modrinth,
    /// Downloaded by the user in a browser.
    Manual,
    /// Tracked in git.
    Repo,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Entry {
    pub file: String,
    pub source: Source,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub size: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub page: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub project_id: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub file_id: Option<u64>,
}

impl Entry {
    pub fn repo(file: &str) -> Entry {
        Entry { file: file.to_string(), source: Source::Repo, sha1: None, size: None, url: None, page: None, project_id: None, file_id: None }
    }

    /// Rejects entries that could write outside mods/ or can't be verified.
    fn validate(&self) -> Result<(), String> {
        let name = &self.file;
        if name.contains(['/', '\\', ':']) || name.starts_with('.') || !name.ends_with(".jar") {
            return Err(format!("{FILE}: bad file name {name:?}"));
        }
        if self.source == Source::Repo {
            return Ok(());
        }
        let hex = |h: &String| h.len() == 40 && h.bytes().all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b));
        if !self.sha1.as_ref().is_some_and(hex) || self.size.is_none() {
            return Err(format!("{FILE}: {name} needs a lowercase sha1 and a size"));
        }
        let needed = if self.source == Source::Manual { &self.page } else { &self.url };
        if !needed.as_ref().is_some_and(|u| u.starts_with("https://")) {
            return Err(format!("{FILE}: {name} needs an https url (manual entries: page)"));
        }
        Ok(())
    }
}

#[derive(Serialize, Deserialize, Default)]
pub struct Manifest {
    pub mods: Vec<Entry>,
}

pub fn load(path: &Path) -> Result<Manifest, String> {
    let text = fs::read_to_string(path).map_err(|e| format!("can't read {}: {e}", path.display()))?;
    let manifest: Manifest = serde_json::from_str(&text).map_err(|e| format!("{}: {e}", path.display()))?;
    for entry in &manifest.mods {
        entry.validate()?;
    }
    Ok(manifest)
}

pub fn sha1_file(path: &Path) -> io::Result<String> {
    let mut hasher = Sha1::new();
    io::copy(&mut File::open(path)?, &mut hasher)?;
    Ok(hasher.finalize().iter().map(|b| format!("{b:02x}")).collect())
}

/// Names of the files in mods/ that end in .jar, sorted.
pub fn jars(dir: &Path) -> Result<Vec<String>, String> {
    let read = match fs::read_dir(dir) {
        Ok(read) => read,
        Err(e) if e.kind() == io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(e) => return Err(format!("can't list {}: {e}", dir.display())),
    };
    let mut names: Vec<String> = read
        .filter_map(Result::ok)
        .filter(|e| e.path().is_file())
        .filter_map(|e| e.file_name().into_string().ok())
        .filter(|n| n.ends_with(".jar") && !n.starts_with('.'))
        .collect();
    names.sort();
    Ok(names)
}

pub fn agent() -> ureq::Agent {
    ureq::AgentBuilder::new()
        .user_agent(USER_AGENT)
        .timeout_connect(Duration::from_secs(30))
        .timeout_read(Duration::from_secs(60))
        .build()
}
