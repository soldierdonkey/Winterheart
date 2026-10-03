//! Minecraft resource lookup: the instance's enabled resource packs (from options.txt, highest priority first)
//! followed by the vanilla client jar. Nothing is hardcoded about which pack provides what.

use std::cell::RefCell;
use std::collections::HashSet;
use std::fs::File;
use std::io::Read;
use std::path::{Path, PathBuf};
use zip::ZipArchive;

enum Source {
    Dir(PathBuf),
    Zip(RefCell<ZipArchive<File>>),
}

struct Named {
    name: String,
    source: Source,
}

impl Named {
    fn read(&self, path: &str) -> Option<Vec<u8>> {
        match &self.source {
            Source::Dir(dir) => std::fs::read(dir.join(path)).ok(),
            Source::Zip(zip) => {
                let mut zip = zip.borrow_mut();
                let mut file = zip.by_name(path).ok()?;
                let mut buf = Vec::with_capacity(file.size() as usize);
                file.read_to_end(&mut buf).ok()?;
                Some(buf)
            }
        }
    }
}

pub struct Resources {
    sources: Vec<Named>,
    pub log: Vec<String>,
}

impl Resources {
    pub fn discover(root: &Path) -> Resources {
        let mut sources = Vec::new();
        let mut log = Vec::new();
        let packs_dir = root.join("resourcepacks");
        let options = std::fs::read_to_string(root.join("options.txt")).unwrap_or_default();

        let mut names: Vec<String> = Vec::new();
        for line in options.lines() {
            if let Some(rest) = line.strip_prefix("resourcePacks:") {
                match serde_json::from_str::<Vec<String>>(rest) {
                    Ok(v) => names = v,
                    Err(e) => log.push(format!("could not parse resourcePacks in options.txt: {e}")),
                }
            }
        }

        // options.txt lists packs lowest priority first, so walk it backwards
        let mut seen = HashSet::new();
        for name in names.iter().rev() {
            let file = name.strip_prefix("file/").unwrap_or(name);
            if !seen.insert(file.to_string()) {
                continue;
            }
            let path = packs_dir.join(file);
            if path.is_dir() {
                sources.push(Named { name: file.to_string(), source: Source::Dir(path) });
            } else if path.is_file() {
                match File::open(&path).ok().and_then(|f| ZipArchive::new(f).ok()) {
                    Some(zip) => sources.push(Named { name: file.to_string(), source: Source::Zip(RefCell::new(zip)) }),
                    None => log.push(format!("could not open pack {file}")),
                }
            }
        }

        match find_vanilla_jar(root) {
            Some(jar) => match File::open(&jar).ok().and_then(|f| ZipArchive::new(f).ok()) {
                Some(zip) => sources.push(Named { name: "vanilla".into(), source: Source::Zip(RefCell::new(zip)) }),
                None => log.push(format!("could not open {}", jar.display())),
            },
            None => log.push("vanilla client jar not found; only resource packs will be searched".into()),
        }

        Resources { sources, log }
    }

    /// First match in priority order, with the name of the pack it came from.
    pub fn find(&self, path: &str) -> Option<(String, Vec<u8>)> {
        self.sources.iter().find_map(|s| s.read(path).map(|b| (s.name.clone(), b)))
    }

    /// Every source that has the file, highest priority first (fonts merge across packs).
    pub fn read_all(&self, path: &str) -> Vec<(String, Vec<u8>)> {
        self.sources.iter().filter_map(|s| s.read(path).map(|b| (s.name.clone(), b))).collect()
    }
}

fn find_vanilla_jar(root: &Path) -> Option<PathBuf> {
    // CurseForge keeps game versions in <minecraft>/Install/versions, two levels above the instance
    let version = std::fs::read_to_string(root.join("minecraftinstance.json"))
        .ok()
        .and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok())
        .and_then(|v| v["gameVersion"].as_str().map(str::to_string))
        .unwrap_or_else(|| "1.20.1".into());

    let mut bases: Vec<PathBuf> = Vec::new();
    if let Some(mc) = root.parent().and_then(|p| p.parent()) {
        bases.push(mc.join("Install").join("versions"));
    }
    if let Ok(home) = std::env::var("HOME") {
        bases.push(Path::new(&home).join("Library/Application Support/minecraft/versions"));
        bases.push(Path::new(&home).join(".minecraft/versions"));
    }
    bases
        .into_iter()
        .map(|b| b.join(&version).join(format!("{version}.jar")))
        .find(|p| p.is_file())
}
