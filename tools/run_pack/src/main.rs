//! run-pack: launch the Winterheart client from the shell, in offline mode, without the CurseForge app, in the
//! foreground or hidden for headless smoke tests. Started through ./run-pack.sh.
//! See docs/features/run-pack.md.

mod launch;

use regex::{Regex, RegexBuilder};
use serde_json::Value;
use std::fs;
use std::os::unix::fs::MetadataExt;
use std::os::unix::process::{CommandExt, ExitStatusExt};
use std::path::{Path, PathBuf};
use std::process::{exit, Child, Command, ExitStatus, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread::sleep;
use std::time::{Duration, Instant};

const USAGE: &str = "\
Usage: ./run-pack.sh [--username NAME] [--world NAME] [--dry-run] [-- extra game args]
       ./run-pack.sh --headless [--world NAME] [--until REGEX] [--timeout SECS] [--keep-running]

  --world NAME     join single-player world NAME (a folder in saves/) on start
  --headless       run in the background with the window hidden; output to logs/run-pack-headless.out
  --until REGEX    headless: wait for REGEX in logs/latest.log (default: \"<user> joined the game\" with --world)
  --timeout SECS   headless: give up after SECS (default 600 with a REGEX, else no limit)
  --keep-running   headless: leave the game running once REGEX is seen instead of stopping it

Env overrides:
  MC_INSTALL     CurseForge install dir (default ~/Documents/curseforge/minecraft/Install)
  FORGE_VERSION  e.g. forge-47.4.22 (default: baseModLoader from minecraftinstance.json)
  JAVA           java binary (default: CurseForge's java-runtime-gamma, Java 17)
  MEMORY_MB      heap size (default: allocatedMemory from minecraftinstance.json, else 7040)";

/// Paths and settings shared by main and the launch code.
pub struct Ctx {
    pub game_dir: PathBuf,
    pub mc_install: PathBuf,
    pub instance: Value,
    pub forge_version: Option<String>,
    memory_env: Option<String>,
}

impl Ctx {
    pub fn memory_mb(&self) -> String {
        if let Some(m) = &self.memory_env {
            return m.clone();
        }
        match self.instance.get("allocatedMemory") {
            Some(Value::Number(n)) if n.as_f64() != Some(0.0) => n.to_string(),
            _ => "7040".into(),
        }
    }
    fn rel<'a>(&self, p: &'a Path) -> std::borrow::Cow<'a, str> {
        match p.strip_prefix(&self.game_dir) {
            Ok(r) => r.to_string_lossy(),
            Err(_) => p.to_string_lossy(),
        }
    }
}

fn env(key: &str) -> Option<String> {
    std::env::var(key).ok().filter(|v| !v.is_empty())
}

macro_rules! die {
    ($code:expr, $($arg:tt)*) => {{ eprintln!($($arg)*); exit($code) }};
}

#[derive(Default)]
struct Opts {
    username: String,
    world: Option<String>,
    dry_run: bool,
    headless: bool,
    until: Option<String>,
    timeout: Option<u64>,
    keep_running: bool,
    extra: Vec<String>,
}

fn parse_args() -> Opts {
    let mut o = Opts {
        username: "Dev".into(),
        ..Default::default()
    };
    let mut args = std::env::args().skip(1);
    while let Some(a) = args.next() {
        let mut val = || args.next().unwrap_or_else(|| die!(2, "{a} needs a value"));
        match a.as_str() {
            "--username" => o.username = val(),
            "--world" => o.world = Some(val()),
            "--until" => o.until = Some(val()),
            "--timeout" => {
                let v = val();
                o.timeout = Some(
                    v.parse()
                        .unwrap_or_else(|_| die!(2, "--timeout takes whole seconds")),
                );
            }
            "--dry-run" => o.dry_run = true,
            "--headless" => o.headless = true,
            "--keep-running" => o.keep_running = true,
            "--" => {
                o.extra = args.collect();
                break;
            }
            "-h" | "--help" => {
                println!("{USAGE}");
                exit(0);
            }
            _ => die!(2, "Unknown option: {a}"),
        }
    }
    if !o.headless && (o.until.is_some() || o.timeout.is_some() || o.keep_running) {
        die!(
            2,
            "--until, --timeout and --keep-running only apply with --headless"
        );
    }
    o
}

fn game_dir() -> PathBuf {
    if let Some(d) = env("RUN_PACK_GAME_DIR") {
        return PathBuf::from(d);
    }
    // <game>/tools/run_pack/target/<profile>/run-pack
    let exe = std::env::current_exe()
        .and_then(fs::canonicalize)
        .unwrap_or_default();
    exe.ancestors()
        .nth(5)
        .map(Path::to_path_buf)
        .unwrap_or_else(|| die!(1, "Can't find the instance dir: set RUN_PACK_GAME_DIR"))
}

/// Quote an argument for a shell, for --dry-run.
fn shell_quote(s: &str) -> String {
    let safe = |c: char| c.is_ascii_alphanumeric() || "@%+=:,./-_".contains(c);
    if !s.is_empty() && s.chars().all(safe) {
        s.to_string()
    } else {
        format!("'{}'", s.replace('\'', r"'\''"))
    }
}

fn java_version(java: &str) -> String {
    Command::new(java)
        .arg("-version")
        .output()
        .map(|o| {
            String::from_utf8_lossy(&o.stderr)
                .lines()
                .next()
                .unwrap_or("")
                .to_string()
        })
        .unwrap_or_else(|e| die!(1, "Can't run {java}: {e}"))
}

fn exit_code(s: ExitStatus) -> i32 {
    s.code().unwrap_or_else(|| 128 + s.signal().unwrap_or(0))
}

/// Watches the game's latest.log and crash-reports/ for this run.
struct Watch {
    log: PathBuf,
    crash_dir: PathBuf,
    old_inode: Option<u64>,
    old_crash: Option<String>,
    until: Option<Regex>,
}

impl Watch {
    fn inode(&self) -> Option<u64> {
        fs::metadata(&self.log).ok().map(|m| m.ino())
    }
    fn latest_crash(&self) -> Option<String> {
        fs::read_dir(&self.crash_dir)
            .ok()?
            .flatten()
            .filter_map(|e| {
                Some((
                    e.metadata().ok()?.modified().ok()?,
                    e.file_name().to_string_lossy().into_owned(),
                ))
            })
            .max()
            .map(|(_, n)| n)
    }
    /// latest.log, but only once the game has rotated it, so the previous run's log can't match.
    fn new_log(&self) -> Option<String> {
        if self.inode() == self.old_inode {
            return None;
        }
        fs::read(&self.log)
            .ok()
            .map(|b| String::from_utf8_lossy(&b).into_owned())
    }
    /// The first log line matching the ready pattern.
    fn ready_line(&self) -> Option<String> {
        let re = self.until.as_ref()?;
        let log = self.new_log()?;
        log.lines().find(|l| re.is_match(l)).map(String::from)
    }
}

/// macOS can't run the client without a window, so the window exists but the app is kept hidden.
/// NSRunningApplication.hide needs no Accessibility permission.
fn hide_app(pid: u32) {
    let script = r#"ObjC.import("AppKit");
    function run(a) {
      var app = $.NSRunningApplication.runningApplicationWithProcessIdentifier(parseInt(a[0]));
      if (!app.isNil() && !app.isHidden) app.hide;
    }"#;
    let _ = Command::new("osascript")
        .args(["-l", "JavaScript", "-e", script, &pid.to_string()])
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status();
}

fn kill(pid: u32, sig: i32) {
    unsafe {
        libc::kill(pid as i32, sig);
    }
}

/// True once the game is gone, checking once a second for up to `secs`.
fn wait_exit(child: &mut Child, secs: u64) -> bool {
    for _ in 0..secs {
        if !matches!(child.try_wait(), Ok(None)) {
            return true;
        }
        sleep(Duration::from_secs(1));
    }
    !matches!(child.try_wait(), Ok(None))
}

fn stop_game(child: &mut Child) {
    if !matches!(child.try_wait(), Ok(None)) {
        return;
    }
    let pid = child.id();
    println!("Stopping the game (pid {pid})");
    // There is no command listener to ask it to quit, so it gets SIGTERM. That can segfault in GLFW
    // teardown and make macOS show a "Java quit unexpectedly" dialog; the world is saved by the shutdown hook first.
    kill(pid, libc::SIGTERM);
    if wait_exit(child, 60) {
        return;
    }
    eprintln!("Still running after 60s, killing it");
    kill(pid, libc::SIGKILL);
    let _ = child.wait();
}

fn summary(w: &Watch, ctx: &Ctx) {
    if let Some(log) = w.new_log() {
        let errors = log.lines().filter(|l| l.contains("/ERROR]")).count();
        println!("{}: {errors} ERROR lines", ctx.rel(&w.log));
    }
    if let Some(crash) = w.latest_crash() {
        if Some(&crash) != w.old_crash.as_ref() {
            println!("Crash report: {}/{crash}", ctx.rel(&w.crash_dir));
        }
    }
}

fn main() {
    let o = parse_args();
    let game_dir = game_dir();
    let instance = fs::read_to_string(game_dir.join("minecraftinstance.json"))
        .ok()
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or(Value::Null);
    let home = env("HOME").unwrap_or_default();
    let ctx = Ctx {
        mc_install: env("MC_INSTALL")
            .map(PathBuf::from)
            .unwrap_or_else(|| Path::new(&home).join("Documents/curseforge/minecraft/Install")),
        game_dir,
        instance,
        forge_version: env("FORGE_VERSION"),
        memory_env: env("MEMORY_MB"),
    };

    // ---- Launch ----
    if !ctx.mc_install.join("versions").is_dir() {
        die!(
            1,
            "CurseForge install not found at {} (set MC_INSTALL)",
            ctx.mc_install.display()
        );
    }
    let java = env("JAVA").unwrap_or_else(|| {
        let j = ctx
            .mc_install
            .join("java/java-runtime-gamma/Contents/Home/bin/java");
        if j.is_file() {
            j.display().to_string()
        } else {
            "java".into()
        }
    });

    let mut cmd = vec![java.clone()];
    cmd.extend(launch::client_args(&ctx, &o.username).unwrap_or_else(|e| die!(1, "{e}")));
    if let Some(w) = &o.world {
        cmd.extend(["--quickPlaySingleplayer".into(), w.clone()]);
    }
    cmd.extend(o.extra.iter().cloned());
    if o.dry_run {
        println!(
            "{}",
            cmd.iter()
                .map(|a| shell_quote(a))
                .collect::<Vec<_>>()
                .join(" ")
        );
        exit(0);
    }
    let running = Command::new("pgrep")
        .args(["-f", "--", &format!("--gameDir {}", ctx.game_dir.display())])
        .stdout(Stdio::null())
        .status()
        .is_ok_and(|s| s.success());
    if running {
        die!(
            1,
            "This instance is already running (CurseForge or another run-pack.sh). Close it first."
        );
    }
    let out = ctx.game_dir.join("logs/run-pack-headless.out");

    // Ready pattern: what --headless waits for.
    let until = o.until.clone().or_else(|| {
        o.world
            .as_ref()
            .map(|_| format!("{} joined the game", o.username))
    });
    let until_re = until.as_ref().map(|u| {
        RegexBuilder::new(u)
            .build()
            .unwrap_or_else(|e| die!(2, "Bad --until pattern: {e}"))
    });
    let mut watch = Watch {
        log: ctx.game_dir.join("logs/latest.log"),
        crash_dir: ctx.game_dir.join("crash-reports"),
        old_inode: None,
        old_crash: None,
        until: until_re,
    };
    watch.old_inode = watch.inode();
    watch.old_crash = watch.latest_crash();

    println!(
        "Launching {} (offline) with {}",
        o.username,
        java_version(&java)
    );

    let mut command = Command::new(&cmd[0]);
    command.args(&cmd[1..]).current_dir(&ctx.game_dir);

    if !o.headless {
        let e = command.exec();
        die!(1, "Can't run {}: {e}", cmd[0]);
    }

    // ---- Headless ----
    // The app is hidden once a second until the game is ready, because showing the window makes GLFW
    // activate the app again.
    let timeout = o.timeout.unwrap_or(if until.is_some() { 600 } else { 0 });
    fs::create_dir_all(ctx.game_dir.join("logs")).ok();
    let out_f =
        fs::File::create(&out).unwrap_or_else(|e| die!(1, "Can't write {}: {e}", out.display()));
    let mut child = command
        .stdin(Stdio::null())
        .stdout(out_f.try_clone().unwrap())
        .stderr(out_f)
        .process_group(0) // keep the terminal's Ctrl-C away from the game; we stop it ourselves
        .spawn()
        .unwrap_or_else(|e| die!(1, "Can't run {}: {e}", cmd[0]));
    let pid = child.id();
    println!(
        "Headless: pid {pid}, output in {}, game log in {}",
        ctx.rel(&out),
        ctx.rel(&watch.log)
    );
    if let Some(u) = &until {
        println!("Waiting for /{u}/ (timeout {timeout}s)");
    }

    let interrupted = Arc::new(AtomicBool::new(false));
    {
        let flag = interrupted.clone();
        ctrlc::set_handler(move || flag.store(true, Ordering::SeqCst)).ok();
    }
    let check_interrupt = |child: &mut Child| {
        if interrupted.load(Ordering::SeqCst) {
            println!();
            stop_game(child);
            summary(&watch, &ctx);
            exit(130);
        }
    };

    enum Status {
        Ready,
        Timeout,
        Exited(i32),
    }
    let start = Instant::now();
    let status = loop {
        check_interrupt(&mut child);
        if let Ok(Some(s)) = child.try_wait() {
            let code = exit_code(s);
            println!("Game exited with code {code}");
            break Status::Exited(code);
        }
        hide_app(pid);
        if let Some(line) = watch.ready_line() {
            println!("Ready after {}s: {line}", start.elapsed().as_secs());
            break Status::Ready;
        }
        if timeout > 0 && start.elapsed().as_secs() >= timeout {
            eprintln!("Timed out after {timeout}s");
            break Status::Timeout;
        }
        for _ in 0..10 {
            if interrupted.load(Ordering::SeqCst) {
                break;
            }
            sleep(Duration::from_millis(100));
        }
    };

    match status {
        Status::Ready => {
            if o.keep_running {
                println!("Left running, hidden (pid {pid}). Click its Dock icon to show it; stop it with: kill {pid}");
            } else {
                stop_game(&mut child);
            }
            summary(&watch, &ctx);
            exit(0);
        }
        Status::Timeout => {
            stop_game(&mut child);
            summary(&watch, &ctx);
            exit(124);
        }
        Status::Exited(code) => {
            summary(&watch, &ctx);
            // A clean exit with nothing to wait for is a success; otherwise the game died before it was ready.
            exit(if until.is_none() && code == 0 { 0 } else { 1 });
        }
    }
}
