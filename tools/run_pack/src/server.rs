//! Installs and prepares the Forge dedicated server in server/.

use crate::Ctx;
use std::fs;
use std::io::{self, IsTerminal, Write};
use std::os::unix::fs::symlink;
use std::path::{Path, PathBuf};
use std::process::Command;

const EULA_URL: &str = "https://aka.ms/MinecraftEULA";
const MAVEN: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge";
/// Instance dirs the server reads, copied into server/ on every launch (see sync_shared).
const SHARED_DIRS: [&str; 4] = ["config", "defaultconfigs", "datapacks", "tacz"];
const EXCLUDE_FILE: &str = "tools/run_pack/server_exclude.txt";

/// Forge version number like 47.4.22, and Minecraft version like 1.20.1.
fn forge_version(ctx: &Ctx) -> Result<(String, String), String> {
    let inst = &ctx.instance;
    let ver = match &ctx.forge_version {
        Some(v) => Some(v.strip_prefix("forge-").unwrap_or(v).to_string()),
        None => inst.pointer("/baseModLoader/forgeVersion").and_then(|v| v.as_str()).map(String::from),
    };
    let ver = ver.filter(|v| !v.is_empty()).ok_or("Can't tell the Forge version: set FORGE_VERSION (e.g. forge-47.4.22)")?;
    let mc = inst.get("gameVersion").and_then(|v| v.as_str()).filter(|v| !v.is_empty()).unwrap_or("1.20.1");
    Ok((ver, mc.to_string()))
}

/// The Forge args file, relative to server/.
pub fn args_file(ctx: &Ctx) -> Result<String, String> {
    let (ver, mc) = forge_version(ctx)?;
    Ok(format!("libraries/net/minecraftforge/forge/{mc}-{ver}/unix_args.txt"))
}

/// Download the Forge installer and run --installServer into server/ (once per version).
pub fn install(ctx: &Ctx, java: &str) -> Result<(), String> {
    let dir = ctx.server_dir();
    let (ver, mc) = forge_version(ctx)?;
    let rel = args_file(ctx)?;
    if dir.join(&rel).exists() {
        return Ok(());
    }
    let inst_dir = dir.join(".installer");
    fs::create_dir_all(&inst_dir).map_err(|e| format!("Can't create {}: {e}", inst_dir.display()))?;
    let name = format!("forge-{mc}-{ver}-installer.jar");
    let jar = inst_dir.join(&name);
    if !jar.exists() {
        let url = format!("{MAVEN}/{mc}-{ver}/{name}");
        eprintln!("Downloading {url}");
        let tmp = inst_dir.join(format!("{name}.part"));
        // The Forge maven answers 403 to some default User-Agents.
        let ok = Command::new("curl")
            .args(["-fsSL", "--connect-timeout", "60", "-A", "winterheart-run-pack/1", "-o"])
            .arg(&tmp)
            .arg(&url)
            .status()
            .is_ok_and(|s| s.success());
        if !ok {
            return Err(format!("Downloading {url} failed"));
        }
        fs::rename(&tmp, &jar).map_err(|e| e.to_string())?;
    }
    eprintln!("Installing Forge {mc}-{ver} server into server/ (downloads the vanilla server and libraries)");
    let log = fs::File::create(inst_dir.join("install.log")).map_err(|e| e.to_string())?;
    let status = Command::new(java)
        .arg("-jar")
        .arg(&jar)
        .arg("--installServer")
        .arg(&dir)
        .current_dir(&dir)
        .stdout(log.try_clone().map_err(|e| e.to_string())?)
        .stderr(log)
        .status();
    let code = status.as_ref().ok().and_then(|s| s.code()).unwrap_or(-1);
    if code != 0 || !dir.join(&rel).exists() {
        return Err(format!("Forge server install failed (exit {code}), see server/.installer/install.log"));
    }
    Ok(())
}

fn load_excludes(ctx: &Ctx) -> Vec<glob::Pattern> {
    fs::read_to_string(ctx.game_dir.join(EXCLUDE_FILE))
        .unwrap_or_default()
        .lines()
        .map(|l| l.split('#').next().unwrap().trim())
        .filter(|l| !l.is_empty())
        .filter_map(|l| glob::Pattern::new(l).ok())
        .collect()
}

fn is_symlink(p: &Path) -> bool {
    p.symlink_metadata().is_ok_and(|m| m.file_type().is_symlink())
}

fn link_mods(ctx: &Ctx) -> Result<(), String> {
    let dst = ctx.server_dir().join("mods");
    if is_symlink(&dst) {
        fs::remove_file(&dst).ok();
    }
    fs::create_dir_all(&dst).map_err(|e| e.to_string())?;
    // Only remove what we own (symlinks); a real jar dropped into server/mods stays.
    for e in fs::read_dir(&dst).map_err(|e| e.to_string())?.flatten() {
        if is_symlink(&e.path()) {
            fs::remove_file(e.path()).ok();
        }
    }
    let pats = load_excludes(ctx);
    let mut names: Vec<String> = fs::read_dir(ctx.game_dir.join("mods"))
        .map_err(|e| e.to_string())?
        .flatten()
        .map(|e| e.file_name().to_string_lossy().into_owned())
        .filter(|n| n.ends_with(".jar"))
        .collect();
    names.sort();
    let (mut linked, mut skipped) = (0, 0);
    for name in names {
        if pats.iter().any(|p| p.matches(&name)) {
            skipped += 1;
            continue;
        }
        symlink(Path::new("../../mods").join(&name), dst.join(&name)).map_err(|e| format!("Linking {name}: {e}"))?;
        linked += 1;
    }
    eprintln!("server/mods: {linked} mods linked, {skipped} client-only skipped ({EXCLUDE_FILE})");
    Ok(())
}

fn sync_shared(ctx: &Ctx) -> Result<(), String> {
    // Copied, not symlinked: some mods call Files.createDirectories(config), which throws
    // FileAlreadyExistsException on a symlink. A copy also keeps the server from writing into tracked config/.
    for d in SHARED_DIRS {
        let src = ctx.game_dir.join(d);
        let dst = ctx.server_dir().join(d);
        if !src.is_dir() {
            continue;
        }
        if is_symlink(&dst) {
            fs::remove_file(&dst).ok();
        }
        let status = Command::new("rsync")
            .args(["-a", "--delete"])
            .arg(format!("{}/", src.display()))
            .arg(format!("{}/", dst.display()))
            .status();
        if !status.as_ref().is_ok_and(|s| s.success()) {
            let code = status.ok().and_then(|s| s.code()).unwrap_or(-1);
            return Err(format!("Copying {d}/ into server/{d} failed (rsync exit {code})"));
        }
    }
    Ok(())
}

fn set_properties(path: &Path, values: &[(&str, String)]) -> Result<(), String> {
    let text = fs::read_to_string(path).unwrap_or_default();
    let mut lines: Vec<String> = text.lines().map(String::from).collect();
    let mut todo: Vec<(&str, &String)> = values.iter().map(|(k, v)| (*k, v)).collect();
    for line in lines.iter_mut() {
        if line.starts_with('#') || !line.contains('=') {
            continue;
        }
        let key = line.split('=').next().unwrap().to_string();
        if let Some(i) = todo.iter().position(|(k, _)| *k == key) {
            let (k, v) = todo.remove(i);
            *line = format!("{k}={v}");
        }
    }
    lines.extend(todo.iter().map(|(k, v)| format!("{k}={v}")));
    fs::write(path, lines.join("\n") + "\n").map_err(|e| e.to_string())
}

fn eula(dir: &Path, accept: bool) -> Result<(), String> {
    let path = dir.join("eula.txt");
    if fs::read_to_string(&path).is_ok_and(|t| t.contains("eula=true")) {
        return Ok(());
    }
    if !accept {
        if !io::stdin().is_terminal() {
            return Err(format!("The server needs the Minecraft EULA ({EULA_URL}) accepted: rerun with --accept-eula"));
        }
        eprint!("Running a Minecraft server means agreeing to the Minecraft EULA ({EULA_URL}). Agree? [y/N] ");
        io::stderr().flush().ok();
        let mut ans = String::new();
        io::stdin().read_line(&mut ans).ok();
        if !matches!(ans.trim().to_lowercase().as_str(), "y" | "yes") {
            return Err("EULA not accepted, not starting the server".into());
        }
    }
    fs::write(&path, format!("# Accepted through run-pack.sh ({EULA_URL})\neula=true\n")).map_err(|e| e.to_string())
}

/// Link mods, copy config etc. into server/, set server.properties, JVM args and the EULA.
/// Returns the Forge args file relative to server/.
pub fn prepare(ctx: &Ctx, world: Option<&str>, password: &str, port: u16, accept_eula: bool) -> Result<String, String> {
    let dir: PathBuf = ctx.server_dir();
    let rel = args_file(ctx)?;
    if !dir.join(&rel).exists() {
        return Err("Forge server not installed".into());
    }
    eula(&dir, accept_eula)?;
    link_mods(ctx)?;
    sync_shared(ctx)?;
    set_properties(
        &dir.join("server.properties"),
        &[
            ("online-mode", "false".into()), // run-pack's client logs in offline
            ("enable-rcon", "true".into()),
            ("rcon.port", port.to_string()),
            ("rcon.password", password.into()),
            ("broadcast-rcon-to-ops", "false".into()),
            ("level-name", world.unwrap_or("world").into()),
        ],
    )?;
    fs::write(
        dir.join("user_jvm_args.txt"),
        format!(
            "# Written by run-pack.sh --server on every launch; set MEMORY_MB to change.\n-Xmx{}m\n-Xms256m\n-Duser.language=en\n",
            ctx.memory_mb()
        ),
    )
    .map_err(|e| e.to_string())?;
    Ok(rel)
}
