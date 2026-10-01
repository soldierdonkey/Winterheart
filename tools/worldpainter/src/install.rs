//! Install the exported NovoAtlas datapack straight into the modpack instance (via Paxi) and back out.
use std::fs;
use std::path::{Path, PathBuf};

pub const PACK_NAME: &str = "winterheart_novoatlas";
const KUBEJS_OVERWORLD: &str = "kubejs/data/minecraft/dimension/overworld.json";
pub const BACKUP_DIR: &str = "backups";

/// Walk up from `start` looking for a Minecraft instance root (has `mods` and `config`).
pub fn find_instance_root(start: &Path) -> Option<PathBuf> {
    start.ancestors().take(6).find(|d| d.join("mods").is_dir() && d.join("config").is_dir()).map(Path::to_path_buf)
}

pub fn detect_root() -> Option<PathBuf> {
    std::env::current_dir()
        .ok()
        .and_then(|d| find_instance_root(&d))
        .or_else(|| std::env::current_exe().ok().and_then(|e| find_instance_root(&e)))
}

fn copy_dir(from: &Path, to: &Path) -> std::io::Result<()> {
    fs::create_dir_all(to)?;
    for entry in fs::read_dir(from)? {
        let entry = entry?;
        let target = to.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            copy_dir(&entry.path(), &target)?;
        } else {
            fs::copy(entry.path(), target)?;
        }
    }
    Ok(())
}

fn novoatlas_present(root: &Path) -> bool {
    fs::read_dir(root.join("mods"))
        .map(|rd| rd.flatten().any(|e| e.file_name().to_string_lossy().to_lowercase().contains("novoatlas") && !e.file_name().to_string_lossy().ends_with(".disabled")))
        .unwrap_or(false)
}

fn stamp() -> u64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

/// Copy `pack` (the exported datapack folder) to `<root>/config/paxi/datapacks/`, optionally moving the KubeJS
/// overworld override (which would conflict) into `backup_dir`.
pub fn install(pack: &Path, root: &Path, disable_kubejs: bool, backup_dir: &Path) -> Result<String, String> {
    if !pack.join("pack.mcmeta").is_file() {
        return Err(format!("{} is not an exported datapack", pack.display()));
    }
    let dest = root.join("config/paxi/datapacks").join(PACK_NAME);
    if dest.exists() {
        fs::remove_dir_all(&dest).map_err(|e| format!("couldn't replace old install: {e}"))?;
    }
    copy_dir(pack, &dest).map_err(|e| format!("copy failed: {e}"))?;

    let mut notes = vec![format!("Installed to {}", dest.strip_prefix(root).unwrap_or(&dest).display())];
    let kubejs = root.join(KUBEJS_OVERWORLD);
    if kubejs.is_file() {
        if disable_kubejs {
            fs::create_dir_all(backup_dir).map_err(|e| e.to_string())?;
            let backup = backup_dir.join(format!("kubejs_overworld_{}.json", stamp()));
            fs::copy(&kubejs, &backup).map_err(|e| e.to_string())?;
            fs::remove_file(&kubejs).map_err(|e| e.to_string())?;
            notes.push(format!("moved kubejs overworld.json to {}", backup.display()));
        } else {
            notes.push("WARNING: kubejs overworld.json still exists and may override this pack".into());
        }
    }
    if !novoatlas_present(root) {
        notes.push("WARNING: no NovoAtlas jar found in mods/".into());
    }
    notes.push("applies to NEW worlds only".into());
    Ok(notes.join("; "))
}

/// Write the spawn point into Starter Structure's config (`config/starterstructure.json5`), in place,
/// leaving comments and every other setting untouched.
pub fn sync_spawn(root: &Path, x: i32, y: i32, z: i32) -> Result<String, String> {
    let path = root.join("config/starterstructure.json5");
    let text = fs::read_to_string(&path).map_err(|e| format!("can't read {}: {e}", path.display()))?;
    let set = |line: &str, key: &str, val: &str| -> Option<String> {
        let t = line.trim_start();
        t.strip_prefix(&format!("\"{key}\":")).map(|_| format!("{}\"{key}\": {val},", &line[..line.len() - t.len()]))
    };
    let wanted = [("shouldUseSpawnCoordinates", "true".to_string()), ("spawnXCoordinate", x.to_string()), ("spawnYCoordinate", y.to_string()), ("spawnZCoordinate", z.to_string())];
    let mut found = 0;
    let out: Vec<String> = text
        .lines()
        .map(|l| {
            for (k, v) in &wanted {
                if let Some(n) = set(l, k, v) {
                    found += 1;
                    return n;
                }
            }
            l.to_string()
        })
        .collect();
    if found != wanted.len() {
        return Err("starterstructure.json5 is missing spawn coordinate keys".into());
    }
    let mut joined = out.join("\n");
    if text.ends_with('\n') {
        joined.push('\n');
    }
    if joined != text {
        fs::write(&path, joined).map_err(|e| e.to_string())?;
    }
    Ok(format!("spawn set to ({x}, {y}, {z})"))
}

/// Remove the installed pack and restore the most recent KubeJS overworld backup, if any.
pub fn uninstall(root: &Path, backup_dir: &Path) -> Result<String, String> {
    let dest = root.join("config/paxi/datapacks").join(PACK_NAME);
    let mut notes = vec![];
    if dest.exists() {
        fs::remove_dir_all(&dest).map_err(|e| e.to_string())?;
        notes.push("removed installed datapack".to_string());
    } else {
        notes.push("datapack wasn't installed".to_string());
    }
    let newest = fs::read_dir(backup_dir)
        .ok()
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.file_name().to_string_lossy().starts_with("kubejs_overworld_"))
        .max_by_key(|e| e.file_name());
    if let Some(b) = newest {
        let target = root.join(KUBEJS_OVERWORLD);
        if target.exists() {
            notes.push("kubejs overworld.json already present; backup left in place".into());
        } else {
            fs::create_dir_all(target.parent().unwrap()).map_err(|e| e.to_string())?;
            fs::copy(b.path(), &target).map_err(|e| e.to_string())?;
            notes.push("restored kubejs overworld.json".into());
        }
    }
    Ok(notes.join("; "))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn spawn_sync_edits_only_spawn_keys() {
        let base = std::env::current_dir().unwrap().join("target").join("fake_spawn");
        let _ = fs::remove_dir_all(&base);
        fs::create_dir_all(base.join("config")).unwrap();
        let orig = "{\n\t// c\n\t\"forceExactSpawn\": true,\n\t\"shouldUseSpawnCoordinates\": false,\n\t\"spawnXCoordinate\": 5,\n\t\"spawnYCoordinate\": 0,\n\t\"spawnZCoordinate\": 9\n}\n";
        fs::write(base.join("config/starterstructure.json5"), orig).unwrap();
        sync_spawn(&base, 0, 72, 0).unwrap();
        let now = fs::read_to_string(base.join("config/starterstructure.json5")).unwrap();
        assert_eq!(now, "{\n\t// c\n\t\"forceExactSpawn\": true,\n\t\"shouldUseSpawnCoordinates\": true,\n\t\"spawnXCoordinate\": 0,\n\t\"spawnYCoordinate\": 72,\n\t\"spawnZCoordinate\": 0,\n}\n");
    }

    #[test]
    fn install_and_uninstall_round_trip() {
        let base = std::env::current_dir().unwrap().join("target").join("fake_instance");
        let _ = fs::remove_dir_all(&base);
        let root = base.join("inst");
        for d in ["mods", "config/paxi/datapacks", "kubejs/data/minecraft/dimension"] {
            fs::create_dir_all(root.join(d)).unwrap();
        }
        fs::write(root.join("mods/novoatlas-forge-test.jar"), b"x").unwrap();
        fs::write(root.join(KUBEJS_OVERWORLD), b"{\"original\":true}").unwrap();
        let pack = base.join("pack");
        fs::create_dir_all(pack.join("data/x")).unwrap();
        fs::write(pack.join("pack.mcmeta"), b"{}").unwrap();
        fs::write(pack.join("data/x/f.json"), b"{}").unwrap();
        let backups = base.join("backups");

        assert_eq!(find_instance_root(&root.join("config")), Some(root.clone()));
        let msg = install(&pack, &root, true, &backups).unwrap();
        assert!(!msg.contains("WARNING"), "{msg}");
        assert!(root.join("config/paxi/datapacks").join(PACK_NAME).join("data/x/f.json").is_file());
        assert!(!root.join(KUBEJS_OVERWORLD).exists());

        // reinstall replaces cleanly
        install(&pack, &root, true, &backups).unwrap();

        uninstall(&root, &backups).unwrap();
        assert!(!root.join("config/paxi/datapacks").join(PACK_NAME).exists());
        assert_eq!(fs::read_to_string(root.join(KUBEJS_OVERWORLD)).unwrap(), "{\"original\":true}");
    }
}
