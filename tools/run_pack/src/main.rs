//! run-pack: launch the Winterheart client from the shell, in offline mode, without the CurseForge app, in the
//! foreground or hidden for headless smoke tests, and run commands in its single-player world.
//! Started through ./run-pack.sh.
//! See docs/features/run-pack.md.

mod launch;
mod rcon;

use regex::{Regex, RegexBuilder};
use serde_json::Value;
use std::fs;
use std::io::{Read, Write};
use std::os::unix::fs::{MetadataExt, OpenOptionsExt};
use std::os::unix::process::{CommandExt, ExitStatusExt};
use std::path::{Path, PathBuf};
use std::process::{exit, Child, Command, ExitStatus, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::thread::sleep;
use std::time::{Duration, Instant};

const USAGE: &str = "\
Usage: ./run-pack.sh [--username NAME] [--world NAME] [--dry-run] [-- extra game args]
       ./run-pack.sh --headless [--world NAME] [--until REGEX] [--timeout SECS] [--keep-running] [--run CMD]...
       ./run-pack.sh --send CMD [--send CMD]...
       ./run-pack.sh --console

  --world NAME     join single-player world NAME (a folder in saves/) on start
  --headless       run in the background with the window hidden; output to logs/run-pack-headless.out
  --until REGEX    headless: wait for REGEX in logs/latest.log (default: \"<user> joined the game\" with --world)
  --timeout SECS   headless: give up after SECS (default 600 with a REGEX, else no limit)
  --keep-running   headless: leave the game running once REGEX is seen instead of stopping it
  --run CMD        once a world is loaded, run CMD in it (repeatable; no leading / needed)
  --send CMD       run CMD in the world of a game run-pack started, print its output, then exit (repeatable)
  --console        interactive command prompt to that world; Ctrl-D quits
Commands run with permission 4 (cheats not needed). `quit` closes the game.

Env overrides:
  MC_INSTALL     CurseForge install dir (default ~/Documents/curseforge/minecraft/Install)
  FORGE_VERSION  e.g. forge-47.4.22 (default: baseModLoader from minecraftinstance.json)
  JAVA           java binary (default: CurseForge's java-runtime-gamma, Java 17)
  MEMORY_MB      heap size (default: allocatedMemory from minecraftinstance.json, else 7040)";

macro_rules! die {
    ($code:expr, $($arg:tt)*) => {{ eprintln!($($arg)*); exit($code) }};
}

/// Port of winterheart-core's single-player command listener (remote/DevRcon.java). Soulrend uses 25576.
const RCON_PORT: u16 = 25577;
/// Env var that turns the listener on: `<port>:<password>`.
const RCON_ENV: &str = "WINTERHEART_RCON";

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
    fn password_file(&self) -> PathBuf {
        self.game_dir.join("local/run-pack/rcon-password")
    }
    fn password(&self) -> String {
        fs::read_to_string(self.password_file())
            .unwrap_or_default()
            .trim()
            .to_string()
    }
    /// The RCON password is generated once into local/ (gitignored).
    fn ensure_password(&self) -> String {
        let pw = self.password();
        if !pw.is_empty() {
            return pw;
        }
        let path = self.password_file();
        let mut bytes = [0u8; 16];
        fs::File::open("/dev/urandom")
            .and_then(|mut f| f.read_exact(&mut bytes))
            .unwrap_or_else(|e| die!(1, "Can't read /dev/urandom: {e}"));
        let pw: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
        fs::create_dir_all(path.parent().unwrap()).ok();
        fs::OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .open(&path)
            .and_then(|mut f| f.write_all(format!("{pw}\n").as_bytes()))
            .unwrap_or_else(|e| die!(1, "Can't write {}: {e}", path.display()));
        pw
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

#[derive(Default)]
struct Opts {
    username: String,
    world: Option<String>,
    dry_run: bool,
    headless: bool,
    until: Option<String>,
    timeout: Option<u64>,
    keep_running: bool,
    console: bool,
    run: Vec<String>,
    send: Vec<String>,
    extra: Vec<String>,
}

fn parse_args() -> Opts {
    let mut o = Opts {
        username: "Developer".into(),
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
            "--run" => o.run.push(val()),
            "--send" => o.send.push(val()),
            "--console" => o.console = true,
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
    if !o.send.is_empty() && o.console {
        die!(2, "Use either --send or --console");
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

/// Wait until the ready pattern is in the log (if any) and the listener answers, then send the commands.
fn run_when_ready(w: &Watch, ctx: &Ctx, cmds: &[String], limit: u64) -> bool {
    let start = Instant::now();
    while start.elapsed().as_secs() < limit {
        if (w.until.is_none() || w.ready_line().is_some()) && rcon::port_open(RCON_PORT) {
            return rcon::run_commands(RCON_PORT, &ctx.password(), cmds, false) == 0;
        }
        sleep(Duration::from_secs(1));
    }
    eprintln!("--run: no world loaded after {limit}s, commands not sent");
    false
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

fn stop_game(child: &mut Child, ctx: &Ctx) {
    if !matches!(child.try_wait(), Ok(None)) {
        return;
    }
    let pid = child.id();
    println!("Stopping the game (pid {pid})");
    // First ask it to close itself (Minecraft.stop(), like the window's close button). Only possible while a
    // world is loaded, since the listener lives on the integrated server.
    if rcon::port_open(RCON_PORT)
        && rcon::run_commands(RCON_PORT, &ctx.password(), &["quit".into()], true) == 0
        && wait_exit(child, 60)
    {
        return;
    }
    // Otherwise SIGTERM. That can segfault in GLFW teardown and make macOS show a "Java quit unexpectedly"
    // dialog; the world is saved by the shutdown hook first.
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

    // ---- On-demand commands to a running game ----
    if !o.send.is_empty() || o.console {
        if o.headless || o.world.is_some() || !o.run.is_empty() || o.dry_run || !o.extra.is_empty()
        {
            die!(2, "--send and --console talk to a game that is already running; they can't be combined with launch options");
        }
        let pw = ctx.password();
        if pw.is_empty() {
            die!(
                1,
                "No RCON password yet ({}): start the game with run-pack.sh first",
                ctx.password_file().display()
            );
        }
        if !rcon::port_open(RCON_PORT) {
            die!(1, "No game listening on port {RCON_PORT}: start one with ./run-pack.sh --world NAME (CurseForge launches have no listener)");
        }
        exit(if o.console {
            rcon::repl(RCON_PORT, &pw, "Winterheart")
        } else {
            rcon::run_commands(RCON_PORT, &pw, &o.send, false)
        });
    }

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

    let pw = ctx.ensure_password();
    let mut command = Command::new(&cmd[0]);
    command
        .args(&cmd[1..])
        .current_dir(&ctx.game_dir)
        .env(RCON_ENV, format!("{RCON_PORT}:{pw}"));

    if !o.headless {
        if o.run.is_empty() {
            let e = command.exec();
            die!(1, "Can't run {}: {e}", cmd[0]);
        }
        // --run is sent from a helper thread while the game runs in the foreground; Ctrl-C reaches the game.
        ctrlc::set_handler(|| {}).ok();
        let mut child = command
            .spawn()
            .unwrap_or_else(|e| die!(1, "Can't run {}: {e}", cmd[0]));
        std::thread::scope(|s| {
            s.spawn(|| run_when_ready(&watch, &ctx, &o.run, 600));
            let status = child.wait().unwrap_or_else(|e| die!(1, "{e}"));
            exit(exit_code(status));
        });
        unreachable!();
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
            stop_game(child, &ctx);
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

    let mut run_rc = 0;
    if matches!(status, Status::Ready)
        && !o.run.is_empty()
        && !run_when_ready(&watch, &ctx, &o.run, 60)
    {
        run_rc = 1;
    }
    check_interrupt(&mut child);

    match status {
        Status::Ready => {
            if o.keep_running {
                println!("Left running, hidden (pid {pid}). Click its Dock icon to show it. Commands: ./run-pack.sh --console; stop it with: ./run-pack.sh --send quit");
            } else {
                stop_game(&mut child, &ctx);
            }
            summary(&watch, &ctx);
            exit(run_rc);
        }
        Status::Timeout => {
            stop_game(&mut child, &ctx);
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
