//! Default command and --check: brings mods/ and resourcepacks/ in line with mods.json.

use crate::manifest::{self, sha1_file, Entry, Folder, Source, FOLDERS, MODS};
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
/// Records what this tool installed (key -> sha1), so it only ever removes its own files.
const STATE: &str = ".cache/fetch-mods/installed.json";
/// Where replaced and removed files go instead of being deleted.
const REPLACED: &str = ".cache/fetch-mods/replaced";

type State = BTreeMap<String, String>;

/// A manifest entry and the folder it belongs in.
#[derive(Clone, Copy)]
struct Item<'a> {
    folder: Folder,
    entry: &'a Entry,
}

impl Item<'_> {
    fn key(&self) -> String {
        key(self.folder, &self.entry.file)
    }

    fn label(&self) -> String {
        format!("{}/{}", self.folder.dir, self.entry.file)
    }

    fn target(&self, root: &Path) -> PathBuf {
        root.join(self.folder.dir).join(&self.entry.file)
    }
}

/// Name in the state file and under the replaced folder. Mods keep their bare file name.
fn key(folder: Folder, file: &str) -> String {
    if folder == MODS {
        file.to_string()
    } else {
        format!("{}/{file}", folder.dir)
    }
}

fn matches(path: &Path, entry: &Entry) -> bool {
    fs::metadata(path).is_ok_and(|m| m.is_file() && Some(m.len()) == entry.size)
        && sha1_file(path).is_ok_and(|h| Some(h) == entry.sha1)
}

fn context<T>(result: io::Result<T>, what: &str, path: &Path) -> Result<T, String> {
    result.map_err(|e| format!("can't {what} {}: {e}", path.display()))
}

fn set_aside(root: &Path, folder: Folder, file: &str) -> Result<(), String> {
    let to = root.join(REPLACED).join(key(folder, file));
    let dir = to.parent().unwrap_or(root);
    context(fs::create_dir_all(dir), "create", dir)?;
    let from = root.join(folder.dir).join(file);
    context(fs::rename(&from, &to), "move", &from)
}

/// Copies a verified file into its folder and the cache, going through a temporary name.
fn install(opts: &Options, item: Item, from: &Path) -> Result<(), String> {
    let cached = cache_path(opts, item);
    if from != cached {
        copy(from, &cached)?;
    }
    let target = item.target(&opts.root);
    if target.exists() && !matches(&target, item.entry) {
        set_aside(&opts.root, item.folder, &item.entry.file)?;
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

fn cache_path(opts: &Options, item: Item) -> PathBuf {
    opts.cache.join(format!("{}{}", item.entry.sha1.as_deref().unwrap_or_default(), item.folder.suffix))
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

fn download(agent: &ureq::Agent, opts: &Options, item: Item) -> Result<(), String> {
    let entry = item.entry;
    let url = encode(entry.url.as_deref().unwrap_or_default());
    let cached = cache_path(opts, item);
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
                return install(opts, item, &cached);
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

/// Downloads in parallel and returns the keys of the failures.
fn download_all(opts: &Options, items: &[Item]) -> Vec<String> {
    let agent = manifest::agent();
    let next = AtomicUsize::new(0);
    let done = AtomicUsize::new(0);
    let failures = Mutex::new(Vec::new());
    thread::scope(|scope| {
        for _ in 0..THREADS.min(items.len()) {
            scope.spawn(|| {
                while let Some(&item) = items.get(next.fetch_add(1, Ordering::SeqCst)) {
                    let result = download(&agent, opts, item);
                    let n = done.fetch_add(1, Ordering::SeqCst) + 1;
                    match result {
                        Ok(()) => println!("[{n}/{}] {}", items.len(), item.label()),
                        Err(e) => {
                            println!("[{n}/{}] FAILED {}: {e}", items.len(), item.label());
                            failures.lock().unwrap().push(item.key());
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

/// Waits until every manual entry shows up in the downloads folder or in its own folder.
fn watch(opts: &Options, mut missing: Vec<Item>, state: &mut State) -> Result<(), String> {
    // Hash of each candidate, redone only when its size or modification time changes.
    let mut seen: HashMap<PathBuf, (u64, Option<SystemTime>, String)> = HashMap::new();
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
        candidates.extend(missing.iter().map(|i| i.target(&opts.root)));
        for path in candidates {
            // The extension check skips partial downloads (.crdownload, .part, .download).
            if !path.extension().is_some_and(|x| x.eq_ignore_ascii_case("jar") || x.eq_ignore_ascii_case("zip")) {
                continue;
            }
            let Ok(meta) = fs::metadata(&path) else { continue };
            if !missing.iter().any(|i| i.entry.size == Some(meta.len())) {
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
            if let Some(i) = missing.iter().position(|i| i.entry.sha1.as_deref() == Some(hash.as_str())) {
                let item = missing.remove(i);
                if path == item.target(&opts.root) {
                    copy(&path, &cache_path(opts, item))?;
                } else {
                    install(opts, item, &path)?;
                }
                state.insert(item.key(), hash);
                println!("  [x] {} ({} left)", item.label(), missing.len());
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

/// Returns false when a folder is still out of sync afterwards.
pub fn run(opts: &Options) -> Result<bool, String> {
    let manifest = manifest::load(&opts.root.join(manifest::FILE))?;
    let items: Vec<Item> = FOLDERS
        .iter()
        .flat_map(|&folder| manifest.entries(folder).iter().map(move |entry| Item { folder, entry }))
        .collect();
    let mut state = load_state(&opts.root);

    let mut present = Vec::new();
    let mut missing = Vec::new();
    let mut missing_repo = Vec::new();
    for &item in &items {
        let path = item.target(&opts.root);
        if item.entry.source == Source::Repo {
            // git owns these, and local rebuilds change their bytes, so only presence is checked.
            if !path.is_file() {
                missing_repo.push(item);
            }
        } else if matches(&path, item.entry) {
            present.push(item);
        } else {
            missing.push(item);
        }
    }
    // Unlisted files: stale if this tool installed those exact bytes, otherwise the user's own.
    let mut stale = Vec::new();
    for folder in FOLDERS {
        for name in manifest::files(&opts.root, folder)? {
            if manifest.entries(folder).iter().any(|e| e.file == name) {
                continue;
            }
            let path = opts.root.join(folder.dir).join(&name);
            if state.get(&key(folder, &name)).is_some_and(|h| sha1_file(&path).is_ok_and(|actual| &actual == h)) {
                stale.push((folder, name));
            } else {
                println!("warning: {}/{name} is not in {} (left alone)", folder.dir, manifest::FILE);
            }
        }
    }

    for item in &missing_repo {
        let label = item.label();
        println!("missing: {label} is tracked in git; restore it with: git checkout -- \"{label}\"");
    }

    if opts.check {
        for item in &missing {
            let why = if item.target(&opts.root).exists() { "differs" } else { "missing" };
            println!("{why}: {}", item.label());
        }
        for (folder, name) in &stale {
            println!("stale: {}/{name}", folder.dir);
        }
        let ok = missing.is_empty() && missing_repo.is_empty() && stale.is_empty();
        println!("{}", if ok { "All files match the manifest." } else { "Out of sync with the manifest; run ./fetch-mods.sh" });
        return Ok(ok);
    }

    for folder in FOLDERS {
        let dir = opts.root.join(folder.dir);
        context(fs::create_dir_all(&dir), "create", &dir)?;
    }
    context(fs::create_dir_all(&opts.cache), "create", &opts.cache)?;
    for (folder, name) in &stale {
        set_aside(&opts.root, *folder, name)?;
        println!("removed {}/{name} (moved to {REPLACED}/{})", folder.dir, key(*folder, name));
    }
    state.retain(|k, _| items.iter().any(|i| &i.key() == k));
    for &item in &present {
        state.insert(item.key(), item.entry.sha1.clone().unwrap_or_default());
        if !matches(&cache_path(opts, item), item.entry) {
            copy(&item.target(&opts.root), &cache_path(opts, item))?;
        }
    }

    let mut downloads = Vec::new();
    let mut manual = Vec::new();
    let mut cached = 0;
    for item in missing {
        if matches(&cache_path(opts, item), item.entry) {
            install(opts, item, &cache_path(opts, item))?;
            state.insert(item.key(), item.entry.sha1.clone().unwrap_or_default());
            cached += 1;
        } else if item.entry.source == Source::Manual {
            manual.push(item);
        } else {
            downloads.push(item);
        }
    }
    println!("{} already present, {cached} from cache, {} to download, {} manual", present.len(), downloads.len(), manual.len());
    let failures = download_all(opts, &downloads);
    for item in downloads.iter().filter(|i| !failures.contains(&i.key())) {
        state.insert(item.key(), item.entry.sha1.clone().unwrap_or_default());
    }
    save_state(&opts.root, &state)?;

    if !manual.is_empty() {
        println!("\n{} file(s) need a manual download:", manual.len());
        for item in &manual {
            println!("  [ ] {}\n      {}", item.label(), item.entry.page.as_deref().unwrap_or_default());
        }
        let interactive = io::stdin().is_terminal();
        if !opts.wait || !interactive {
            println!("Download them into their folders or run ./fetch-mods.sh in a terminal.");
            return Ok(false);
        }
        if opts.open {
            print!("Open these in your browser? [Y/n] ");
            io::stdout().flush().map_err(|e| e.to_string())?;
            let mut answer = String::new();
            let read = io::stdin().lock().read_line(&mut answer).map_err(|e| e.to_string())?;
            if read > 0 && !answer.trim().to_lowercase().starts_with('n') {
                manual.iter().filter_map(|i| i.entry.page.as_deref()).for_each(open_page);
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
        println!("All files match the manifest.");
    }
    Ok(ok)
}
