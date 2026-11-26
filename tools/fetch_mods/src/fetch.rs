//! Default command and --check: brings mods/ in line with mods.json.

use crate::manifest::{self, sha1_file, Entry, Source};
use crate::Options;
use std::collections::{BTreeMap, HashMap};
use std::fs::{self, File};
use std::io::{self, BufRead, IsTerminal, Write};
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, SystemTime};

const THREADS: usize = 8;
const ATTEMPTS: u32 = 3;
/// Records what this tool put in mods/ (file -> sha1), so it only ever removes its own files.
const STATE: &str = ".cache/fetch-mods/installed.json";
/// Where replaced and removed jars go instead of being deleted.
const REPLACED: &str = ".cache/fetch-mods/replaced";

type State = BTreeMap<String, String>;

fn matches(path: &Path, entry: &Entry) -> bool {
    fs::metadata(path).is_ok_and(|m| m.is_file() && Some(m.len()) == entry.size)
        && sha1_file(path).is_ok_and(|h| Some(h) == entry.sha1)
}

fn context<T>(result: io::Result<T>, what: &str, path: &Path) -> Result<T, String> {
    result.map_err(|e| format!("can't {what} {}: {e}", path.display()))
}

fn set_aside(root: &Path, name: &str) -> Result<(), String> {
    let dir = root.join(REPLACED);
    context(fs::create_dir_all(&dir), "create", &dir)?;
    let from = root.join("mods").join(name);
    context(fs::rename(&from, dir.join(name)), "move", &from)
}

/// Copies a verified file into mods/ and the cache, going through a temporary name.
fn install(opts: &Options, entry: &Entry, from: &Path) -> Result<(), String> {
    let cached = cache_path(opts, entry);
    if from != cached {
        copy(from, &cached)?;
    }
    let target = opts.root.join("mods").join(&entry.file);
    if target.exists() && !matches(&target, entry) {
        set_aside(&opts.root, &entry.file)?;
    }
    copy(&cached, &target)
}

fn copy(from: &Path, to: &Path) -> Result<(), String> {
    let mut part = to.as_os_str().to_owned();
    part.push(".part");
    let part = PathBuf::from(part);
    context(fs::copy(from, &part), "copy", from)?;
    context(fs::rename(&part, to), "write", to)
}

fn cache_path(opts: &Options, entry: &Entry) -> PathBuf {
    opts.cache.join(format!("{}.jar", entry.sha1.as_deref().unwrap_or_default()))
}

/// Percent-encodes what CurseForge leaves raw in its URLs (spaces, brackets, non-ASCII).
fn encode(url: &str) -> String {
    url.bytes()
        .map(|b| {
            if b.is_ascii_alphanumeric() || b"-._~:/?&=%+!$'()*,;@".contains(&b) {
                (b as char).to_string()
            } else {
                format!("%{b:02X}")
            }
        })
        .collect()
}

fn download(agent: &ureq::Agent, opts: &Options, entry: &Entry) -> Result<(), String> {
    let url = encode(entry.url.as_deref().unwrap_or_default());
    let cached = cache_path(opts, entry);
    let part = cached.with_extension("download");
    let mut error = String::new();
    for attempt in 1..=ATTEMPTS {
        let result = agent.get(&url).call().map_err(|e| e.to_string()).and_then(|response| {
            let mut file = context(File::create(&part), "create", &part)?;
            io::copy(&mut response.into_reader(), &mut file).map_err(|e| e.to_string())?;
            Ok(())
        });
        match result {
            Ok(()) if matches(&part, entry) => {
                context(fs::rename(&part, &cached), "write", &cached)?;
                return install(opts, entry, &cached);
            }
            Ok(()) => error = "downloaded file does not match the manifest's size and sha1".into(),
            Err(e) => error = e,
        }
        if attempt < ATTEMPTS {
            thread::sleep(Duration::from_secs(u64::from(attempt) * 2));
        }
    }
    let _ = fs::remove_file(&part);
    Err(error)
}

/// Downloads in parallel and returns the failures.
fn download_all(opts: &Options, entries: &[&Entry]) -> Vec<String> {
    let agent = manifest::agent();
    let next = AtomicUsize::new(0);
    let done = AtomicUsize::new(0);
    let failures = Mutex::new(Vec::new());
    thread::scope(|scope| {
        for _ in 0..THREADS.min(entries.len()) {
            scope.spawn(|| {
                while let Some(entry) = entries.get(next.fetch_add(1, Ordering::SeqCst)) {
                    let result = download(&agent, opts, entry);
                    let n = done.fetch_add(1, Ordering::SeqCst) + 1;
                    match result {
                        Ok(()) => println!("[{n}/{}] {}", entries.len(), entry.file),
                        Err(e) => {
                            println!("[{n}/{}] FAILED {}: {e}", entries.len(), entry.file);
                            failures.lock().unwrap().push(entry.file.clone());
                        }
                    }
                }
            });
        }
    });
    failures.into_inner().unwrap()
}

fn open_page(url: &str) {
    let mut command = if cfg!(target_os = "macos") {
        Command::new("open")
    } else if cfg!(windows) {
        let mut c = Command::new("rundll32");
        c.arg("url.dll,FileProtocolHandler");
        c
    } else {
        Command::new("xdg-open")
    };
    if !command.arg(url).status().is_ok_and(|s| s.success()) {
        println!("Couldn't open {url}");
    }
}

/// Waits until every manual entry shows up in the downloads folder or in mods/.
fn watch(opts: &Options, mut missing: Vec<&Entry>, state: &mut State) -> Result<(), String> {
    // Hash of each candidate, redone only when its size or modification time changes.
    let mut seen: HashMap<PathBuf, (u64, Option<SystemTime>, String)> = HashMap::new();
    let mods = opts.root.join("mods");
    println!("Watching {} (Ctrl+C to stop) ...", opts.downloads.display());
    while !missing.is_empty() {
        let read = fs::read_dir(&opts.downloads).map_err(|e| {
            let hint = if e.kind() == io::ErrorKind::PermissionDenied {
                "\nAllow your terminal to access it (macOS: System Settings > Privacy & Security > Files and Folders), or pass --downloads <dir>."
            } else {
                ""
            };
            format!("can't list {}: {e}{hint}", opts.downloads.display())
        })?;
        let mut candidates: Vec<PathBuf> = read.filter_map(Result::ok).map(|e| e.path()).collect();
        candidates.extend(missing.iter().map(|e| mods.join(&e.file)));
        for path in candidates {
            // The extension check skips partial downloads (.crdownload, .part, .download).
            if !path.extension().is_some_and(|x| x.eq_ignore_ascii_case("jar")) {
                continue;
            }
            let Ok(meta) = fs::metadata(&path) else { continue };
            if !missing.iter().any(|e| e.size == Some(meta.len())) {
                continue;
            }
            let stamp = (meta.len(), meta.modified().ok());
            let hash = match seen.get(&path) {
                Some((len, time, hash)) if (*len, *time) == stamp => hash.clone(),
                _ => {
                    let Ok(hash) = sha1_file(&path) else { continue };
                    seen.insert(path.clone(), (stamp.0, stamp.1, hash.clone()));
                    hash
                }
            };
            if let Some(i) = missing.iter().position(|e| e.sha1.as_deref() == Some(hash.as_str())) {
                let entry = missing.remove(i);
                if path == mods.join(&entry.file) {
                    copy(&path, &cache_path(opts, entry))?;
                } else {
                    install(opts, entry, &path)?;
                }
                state.insert(entry.file.clone(), hash);
                println!("  [x] {} ({} left)", entry.file, missing.len());
            }
        }
        if !missing.is_empty() {
            thread::sleep(Duration::from_secs(1));
        }
    }
    Ok(())
}

fn load_state(root: &Path) -> State {
    fs::read_to_string(root.join(STATE)).ok().and_then(|t| serde_json::from_str(&t).ok()).unwrap_or_default()
}

fn save_state(root: &Path, state: &State) -> Result<(), String> {
    let path = root.join(STATE);
    let dir = path.parent().unwrap_or(root);
    context(fs::create_dir_all(dir), "create", dir)?;
    let text = serde_json::to_string_pretty(state).map_err(|e| e.to_string())?;
    context(fs::write(&path, text), "write", &path)
}

/// Returns false when mods/ is still out of sync afterwards.
pub fn run(opts: &Options) -> Result<bool, String> {
    let manifest = manifest::load(&opts.root.join(manifest::FILE))?;
    let mods = opts.root.join("mods");
    let listed = |name: &str| manifest.mods.iter().any(|e| e.file == name);
    let mut state = load_state(&opts.root);

    let mut present = Vec::new();
    let mut missing = Vec::new();
    let mut missing_repo = Vec::new();
    for entry in &manifest.mods {
        let path = mods.join(&entry.file);
        if entry.source == Source::Repo {
            // git owns these, and local rebuilds change their bytes, so only presence is checked.
            if !path.is_file() {
                missing_repo.push(entry);
            }
        } else if matches(&path, entry) {
            present.push(entry);
        } else {
            missing.push(entry);
        }
    }
    // Unlisted jars: stale if this tool installed those exact bytes, otherwise the user's own.
    let (stale, extra): (Vec<String>, Vec<String>) = manifest::jars(&mods)?
        .into_iter()
        .filter(|n| !listed(n))
        .partition(|n| state.get(n).is_some_and(|h| sha1_file(&mods.join(n)).is_ok_and(|actual| &actual == h)));

    for name in &extra {
        println!("warning: mods/{name} is not in {} (left alone)", manifest::FILE);
    }
    for entry in &missing_repo {
        println!("missing: mods/{} is tracked in git; restore it with: git checkout -- \"mods/{}\"", entry.file, entry.file);
    }

    if opts.check {
        for entry in &missing {
            let why = if mods.join(&entry.file).exists() { "differs" } else { "missing" };
            println!("{why}: mods/{}", entry.file);
        }
        for name in &stale {
            println!("stale: mods/{name}");
        }
        let ok = missing.is_empty() && missing_repo.is_empty() && stale.is_empty();
        println!("{}", if ok { "mods/ matches the manifest." } else { "mods/ is out of sync; run ./fetch-mods.sh" });
        return Ok(ok);
    }

    context(fs::create_dir_all(&mods), "create", &mods)?;
    context(fs::create_dir_all(&opts.cache), "create", &opts.cache)?;
    for name in &stale {
        set_aside(&opts.root, name)?;
        println!("removed mods/{name} (moved to {REPLACED}/)");
    }
    state.retain(|name, _| listed(name));
    for entry in &present {
        state.insert(entry.file.clone(), entry.sha1.clone().unwrap_or_default());
        if !matches(&cache_path(opts, entry), entry) {
            copy(&mods.join(&entry.file), &cache_path(opts, entry))?;
        }
    }

    let mut downloads = Vec::new();
    let mut manual = Vec::new();
    let mut cached = 0;
    for entry in missing {
        if matches(&cache_path(opts, entry), entry) {
            install(opts, entry, &cache_path(opts, entry))?;
            state.insert(entry.file.clone(), entry.sha1.clone().unwrap_or_default());
            cached += 1;
        } else if entry.source == Source::Manual {
            manual.push(entry);
        } else {
            downloads.push(entry);
        }
    }
    println!("{} already present, {cached} from cache, {} to download, {} manual", present.len(), downloads.len(), manual.len());
    let failures = download_all(opts, &downloads);
    for entry in downloads.iter().filter(|e| !failures.contains(&e.file)) {
        state.insert(entry.file.clone(), entry.sha1.clone().unwrap_or_default());
    }
    save_state(&opts.root, &state)?;

    if !manual.is_empty() {
        println!("\n{} mod(s) need a manual download:", manual.len());
        for entry in &manual {
            println!("  [ ] {}\n      {}", entry.file, entry.page.as_deref().unwrap_or_default());
        }
        let interactive = io::stdin().is_terminal();
        if !opts.wait || !interactive {
            println!("Download them into mods/ or run ./fetch-mods.sh in a terminal.");
            return Ok(false);
        }
        if opts.open {
            print!("Open these in your browser? [Y/n] ");
            io::stdout().flush().map_err(|e| e.to_string())?;
            let mut answer = String::new();
            let read = io::stdin().lock().read_line(&mut answer).map_err(|e| e.to_string())?;
            if read > 0 && !answer.trim().to_lowercase().starts_with('n') {
                manual.iter().filter_map(|e| e.page.as_deref()).for_each(open_page);
            }
        }
        watch(opts, manual, &mut state)?;
        save_state(&opts.root, &state)?;
    }

    if !failures.is_empty() {
        println!("{} download(s) failed; run ./fetch-mods.sh again.", failures.len());
    }
    let ok = failures.is_empty() && missing_repo.is_empty();
    if ok {
        println!("mods/ matches the manifest.");
    }
    Ok(ok)
}
