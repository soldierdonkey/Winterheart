//! Keeps mods/ and resourcepacks/ in step with mods.json. See docs/features/fetch-mods.md.

mod fetch;
mod generate;
mod manifest;

use std::path::PathBuf;
use std::process::ExitCode;

const HELP: &str = "\
Usage: ./fetch-mods.sh [options]
  (no options)       download missing mods and resource packs and verify them
  --check            report what is out of sync and change nothing (exit 1 if anything is)
  --generate         rewrite mods.json from mods/, resourcepacks/ and the launcher's minecraftinstance.json
  --downloads <dir>  folder watched for manual downloads (default: ~/Downloads)
  --no-open          don't offer to open manual download pages in the browser
  --no-wait          don't wait for manual downloads; list them and exit 1
  --help             show this text

Environment: FETCH_MODS_CACHE overrides the download cache (default: ~/.cache/winterheart-mods).";

pub struct Options {
    pub root: PathBuf,
    pub cache: PathBuf,
    pub downloads: PathBuf,
    pub check: bool,
    pub open: bool,
    pub wait: bool,
}

fn home() -> Result<PathBuf, String> {
    std::env::var_os("HOME")
        .or_else(|| std::env::var_os("USERPROFILE"))
        .map(PathBuf::from)
        .ok_or_else(|| "neither HOME nor USERPROFILE is set".to_string())
}

fn run() -> Result<bool, String> {
    let mut args = std::env::args().skip(1);
    let (mut check, mut generate, mut open, mut wait, mut downloads) = (false, false, true, true, None);
    while let Some(arg) = args.next() {
        match arg.as_str() {
            "--check" => check = true,
            "--generate" => generate = true,
            "--no-open" => open = false,
            "--no-wait" => wait = false,
            "--downloads" => downloads = Some(PathBuf::from(args.next().ok_or("--downloads needs a folder")?)),
            "--help" | "-h" => {
                println!("{HELP}");
                return Ok(true);
            }
            other => return Err(format!("unknown option {other} (see --help)")),
        }
    }
    if check && generate {
        return Err("--check and --generate can't be combined".into());
    }
    let root = match std::env::var_os("FETCH_MODS_GAME_DIR") {
        Some(dir) => PathBuf::from(dir),
        None => std::env::current_dir().map_err(|e| e.to_string())?,
    };
    if generate {
        return generate::run(&root).map(|()| true);
    }
    let cache = match std::env::var_os("FETCH_MODS_CACHE") {
        Some(dir) => PathBuf::from(dir),
        None => home()?.join(".cache").join("winterheart-mods"),
    };
    let downloads = match downloads {
        Some(dir) => dir,
        None => home()?.join("Downloads"),
    };
    fetch::run(&Options { root, cache, downloads, check, open, wait })
}

fn main() -> ExitCode {
    match run() {
        Ok(true) => ExitCode::SUCCESS,
        Ok(false) => ExitCode::FAILURE,
        Err(message) => {
            eprintln!("fetch-mods: {message}");
            ExitCode::FAILURE
        }
    }
}
