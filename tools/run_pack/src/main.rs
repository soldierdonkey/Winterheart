//! run-pack: launch the Winterheart instance from the shell, in offline mode, without the CurseForge app, as a
//! client or as a Forge dedicated server, and send commands to a running server. Started through ./run-pack.sh.
//! See docs/features/run-pack.md.

mod launch;
mod rcon;
mod server;

use regex::{Regex, RegexBuilder};
use serde_json::Value;
use std::fs;
use std::io::Read;
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
       ./run-pack.sh --server [--world NAME] [--accept-eula] [--run CMD]...
       ./run-pack.sh [--server] --headless [--world NAME] [--until REGEX] [--timeout SECS] [--keep-running] [--run CMD]...
       ./run-pack.sh --send CMD [--send CMD]...      (dedicated server only)
       ./run-pack.sh --console                       (dedicated server only)

  --world NAME     client: join single-player world NAME on start; server: level-name (a world in server/)
  --server         run a Forge dedicated server in server/ (installed on first use; no window)
  --accept-eula    server: accept the Minecraft EULA without asking (needed once, when not interactive)
  --headless       run in the background (client window hidden); output to logs/run-pack-{headless,server}.out
  --until REGEX    headless: wait for REGEX in the game's latest.log
                   (default: \"<user> joined the game\" with a client --world, \"Done (\" for a server)
  --timeout SECS   headless: give up after SECS (default 600 with a REGEX, else no limit)
  --keep-running   headless: leave the game running once REGEX is seen instead of stopping it
  --run CMD        server: once it is ready, run CMD (repeatable; no leading / needed)
  --send CMD       run CMD in an already running dedicated server and print its output, then exit (repeatable)
  --console        interactive command prompt to an already running dedicated server
The client has no command listener (Soulrend's needs its soulrend_dev_utils mod), so --run, --send and
--console only work with the dedicated server.

Env overrides:
  MC_INSTALL     CurseForge install dir (default ~/Documents/curseforge/minecraft/Install)
  FORGE_VERSION  e.g. forge-47.4.22 (default: baseModLoader from minecraftinstance.json)
  JAVA           java binary (default: CurseForge's java-runtime-gamma, Java 17)
  MEMORY_MB      heap size (default: allocatedMemory from minecraftinstance.json, else 7040)";

const SERVER_RCON_PORT: u16 = 25575;

/// Paths and settings shared by the launch, server and RCON code.
pub struct Ctx {
    pub game_dir: PathBuf,
    pub mc_install: PathBuf,
    pub instance: Value,
    pub forge_version: Option<String>,
    memory_env: Option<String>,
}

impl Ctx {
    pub fn server_dir(&self) -> PathBuf {
        self.game_dir.join("server")
    }
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
    server: bool,
    accept_eula: bool,
    until: Option<String>,
    timeout: Option<u64>,
    keep_running: bool,
    console: bool,
    run: Vec<String>,
    send: Vec<String>,
    extra: Vec<String>,
}

fn parse_args() -> Opts {
    let mut o = Opts { username: "Dev".into(), ..Default::default() };
    let mut args = std::env::args().skip(1);
    while let Some(a) = args.next() {
        let mut val = || args.next().unwrap_or_else(|| die!(2, "{a} needs a value"));
        match a.as_str() {
            "--username" => o.username = val(),
            "--world" => o.world = Some(val()),
            "--until" => o.until = Some(val()),
            "--timeout" => {
                let v = val();
                o.timeout = Some(v.parse().unwrap_or_else(|_| die!(2, "--timeout takes whole seconds")));
            }
            "--run" => o.run.push(val()),
            "--send" => o.send.push(val()),
            "--dry-run" => o.dry_run = true,
            "--headless" => o.headless = true,
            "--server" => o.server = true,
            "--accept-eula" => o.accept_eula = true,
            "--keep-running" => o.keep_running = true,
            "--console" => o.console = true,
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
        die!(2, "--until, --timeout and --keep-running only apply with --headless");
    }
    if !o.server && !o.run.is_empty() {
        die!(2, "--run needs --server: the client has no command listener");
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
    let exe = std::env::current_exe().and_then(fs::canonicalize).unwrap_or_default();
    exe.ancestors().nth(5).map(Path::to_path_buf).unwrap_or_else(|| die!(1, "Can't find the instance dir: set RUN_PACK_GAME_DIR"))
}

fn read_password(ctx: &Ctx) -> String {
    fs::read_to_string(ctx.password_file()).unwrap_or_default().trim().to_string()
}

/// The RCON password is generated once into local/ (gitignored).
fn ensure_password(ctx: &Ctx) {
    let path = ctx.password_file();
    if !read_password(ctx).is_empty() {
        return;
    }
    let mut bytes = [0u8; 16];
    fs::File::open("/dev/urandom")
        .and_then(|mut f| f.read_exact(&mut bytes))
        .unwrap_or_else(|e| die!(1, "Can't read /dev/urandom: {e}"));
    let hex: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
    fs::create_dir_all(path.parent().unwrap()).ok();
    fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(&path)
        .and_then(|mut f| std::io::Write::write_all(&mut f, format!("{hex}\n").as_bytes()))
        .unwrap_or_else(|e| die!(1, "Can't write {}: {e}", path.display()));
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
        .map(|o| String::from_utf8_lossy(&o.stderr).lines().next().unwrap_or("").to_string())
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
            .filter_map(|e| Some((e.metadata().ok()?.modified().ok()?, e.file_name().to_string_lossy().into_owned())))
            .max()
            .map(|(_, n)| n)
    }
    /// latest.log, but only once the game has rotated it, so the previous run's log can't match.
    fn new_log(&self) -> Option<String> {
        if self.inode() == self.old_inode {
            return None;
        }
        fs::read(&self.log).ok().map(|b| String::from_utf8_lossy(&b).into_owned())
    }
    /// The first log line matching the ready pattern.
    fn ready_line(&self) -> Option<String> {
        let re = self.until.as_ref()?;
        let log = self.new_log()?;
        log.lines().find(|l| re.is_match(l)).map(String::from)
    }
    fn ready(&self) -> bool {
        self.until.is_none() || self.ready_line().is_some()
    }
}

/// Wait until the ready pattern is in the log and the command port answers, then send the commands.
fn run_when_ready(w: &Watch, ctx: &Ctx, cmds: &[String], limit: u64) -> bool {
    let start = Instant::now();
    while start.elapsed().as_secs() < limit {
        if w.ready() && rcon::port_open(SERVER_RCON_PORT) {
            return rcon::run_commands(SERVER_RCON_PORT, &read_password(ctx), cmds, false) == 0;
        }
        sleep(Duration::from_secs(1));
    }
    eprintln!("--run: the game wasn't ready after {limit}s, commands not sent");
    false
}

/// Client: macOS can't run it without a window, so the window exists but the app is kept hidden.
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

fn stop_game(child: &mut Child, server: bool) {
    if !matches!(child.try_wait(), Ok(None)) {
        return;
    }
    let pid = child.id();
    println!("Stopping the game (pid {pid})");
    if server && rcon::port_open(SERVER_RCON_PORT) {
        let pw = fs::read_to_string(game_dir().join("local/run-pack/rcon-password")).unwrap_or_default();
        if rcon::run_commands(SERVER_RCON_PORT, pw.trim(), &["stop".into()], true) == 0 && wait_exit(child, 60) {
            return;
        }
    }
    // Client: there is no command listener to ask it to quit, so it gets SIGTERM. That can segfault in GLFW
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
        mc_install: env("MC_INSTALL").map(PathBuf::from).unwrap_or_else(|| Path::new(&home).join("Documents/curseforge/minecraft/Install")),
        game_dir,
        instance,
        forge_version: env("FORGE_VERSION"),
        memory_env: env("MEMORY_MB"),
    };

    // ---- RCON: on-demand commands (dedicated server only) ----
    if !o.send.is_empty() || o.console {
        if o.server || o.headless || o.world.is_some() || !o.run.is_empty() || o.dry_run {
            die!(2, "--send and --console talk to a game that is already running; they can't be combined with launch options");
        }
        if read_password(&ctx).is_empty() {
            die!(1, "No RCON password yet ({}): start the game with run-pack.sh first", ctx.password_file().display());
        }
        if !rcon::port_open(SERVER_RCON_PORT) {
            die!(1, "No running dedicated server found: start one with ./run-pack.sh --server");
        }
        let pw = read_password(&ctx);
        exit(if o.console {
            rcon::repl(SERVER_RCON_PORT, &pw, "server")
        } else {
            rcon::run_commands(SERVER_RCON_PORT, &pw, &o.send, false)
        });
    }

    // ---- Launch ----
    if o.server && !o.extra.is_empty() {
        die!(2, "Extra game args after -- only apply to the client");
    }
    if !ctx.mc_install.join("versions").is_dir() {
        die!(1, "CurseForge install not found at {} (set MC_INSTALL)", ctx.mc_install.display());
    }
    let java = env("JAVA").unwrap_or_else(|| {
        let j = ctx.mc_install.join("java/java-runtime-gamma/Contents/Home/bin/java");
        if j.is_file() { j.display().to_string() } else { "java".into() }
    });

    let (run_dir, log, crash_dir, out, pid_file, cmd): (PathBuf, PathBuf, PathBuf, PathBuf, Option<PathBuf>, Vec<String>);
    if o.server {
        let sd = ctx.server_dir();
        if o.dry_run {
            let args = server::args_file(&ctx).unwrap_or_else(|e| die!(1, "{e}"));
            println!("cd {} && {} @user_jvm_args.txt @{} nogui", shell_quote(&sd.to_string_lossy()), shell_quote(&java), args);
            exit(0);
        }
        let pf = sd.join(".run-pack.pid");
        let pid_alive = fs::read_to_string(&pf)
            .ok()
            .and_then(|p| p.trim().parse::<i32>().ok())
            .is_some_and(|p| unsafe { libc::kill(p, 0) } == 0);
        if pid_alive || rcon::port_open(SERVER_RCON_PORT) {
            die!(1, "The server is already running. Stop it first (./run-pack.sh --send stop).");
        }
        ensure_password(&ctx);
        fs::create_dir_all(&sd).ok();
        server::install(&ctx, &java).unwrap_or_else(|e| die!(1, "{e}"));
        let args = server::prepare(&ctx, o.world.as_deref(), &read_password(&ctx), SERVER_RCON_PORT, o.accept_eula)
            .unwrap_or_else(|e| die!(1, "{e}"));
        cmd = vec![java.clone(), "@user_jvm_args.txt".into(), format!("@{args}"), "nogui".into()];
        log = sd.join("logs/latest.log");
        crash_dir = sd.join("crash-reports");
        out = ctx.game_dir.join("logs/run-pack-server.out");
        pid_file = Some(pf);
        run_dir = sd;
    } else {
        let mut c = vec![java.clone()];
        c.extend(launch::client_args(&ctx, &o.username).unwrap_or_else(|e| die!(1, "{e}")));
        if let Some(w) = &o.world {
            c.extend(["--quickPlaySingleplayer".into(), w.clone()]);
        }
        c.extend(o.extra.iter().cloned());
        if o.dry_run {
            println!("{}", c.iter().map(|a| shell_quote(a)).collect::<Vec<_>>().join(" "));
            exit(0);
        }
        let running = Command::new("pgrep")
            .args(["-f", "--", &format!("--gameDir {}", ctx.game_dir.display())])
            .stdout(Stdio::null())
            .status()
            .is_ok_and(|s| s.success());
        if running {
            die!(1, "This instance is already running (CurseForge or another run-pack.sh). Close it first.");
        }
        cmd = c;
        run_dir = ctx.game_dir.clone();
        log = ctx.game_dir.join("logs/latest.log");
        crash_dir = ctx.game_dir.join("crash-reports");
        out = ctx.game_dir.join("logs/run-pack-headless.out");
        pid_file = None;
    }

    // Ready pattern: what --headless waits for, and what --run waits for before sending.
    let until = o.until.clone().or_else(|| {
        if o.server {
            Some(r"Done \(".into())
        } else {
            o.world.as_ref().map(|_| format!("{} joined the game", o.username))
        }
    });
    let until_re = until.as_ref().map(|u| RegexBuilder::new(u).build().unwrap_or_else(|e| die!(2, "Bad --until pattern: {e}")));
    let mut watch = Watch { log, crash_dir, old_inode: None, old_crash: None, until: until_re };
    watch.old_inode = watch.inode();
    watch.old_crash = watch.latest_crash();

    if o.server {
        println!("Starting the dedicated server (offline mode, world '{}') with {}", o.world.as_deref().unwrap_or("world"), java_version(&java));
    } else {
        println!("Launching {} (offline) with {}", o.username, java_version(&java));
    }

    let mut command = Command::new(&cmd[0]);
    command.args(&cmd[1..]).current_dir(&run_dir);

    if !o.headless {
        // Foreground: the server console (stdin) works as usual.
        if o.run.is_empty() {
            if let Some(pf) = &pid_file {
                fs::write(pf, format!("{}\n", std::process::id())).ok(); // exec keeps this pid
            }
            let e = command.exec();
            die!(1, "Can't run {}: {e}", cmd[0]);
        }
        // --run is sent from a helper thread; Ctrl-C reaches the game, which stops itself.
        ctrlc::set_handler(|| {}).ok();
        let mut child = command.spawn().unwrap_or_else(|e| die!(1, "Can't run {}: {e}", cmd[0]));
        if let Some(pf) = &pid_file {
            fs::write(pf, format!("{}\n", child.id())).ok();
        }
        let limit = o.timeout.filter(|t| *t > 0).unwrap_or(600);
        let run = o.run.clone();
        std::thread::scope(|s| {
            s.spawn(|| run_when_ready(&watch, &ctx, &run, limit));
            let status = child.wait().unwrap_or_else(|e| die!(1, "{e}"));
            exit(exit_code(status));
        });
        unreachable!();
    }

    // ---- Headless ----
    // Server: a plain background process. Client: the app is hidden once a second until the game is ready,
    // because showing the window makes GLFW activate the app again.
    let timeout = o.timeout.unwrap_or(if until.is_some() { 600 } else { 0 });
    fs::create_dir_all(ctx.game_dir.join("logs")).ok();
    let out_f = fs::File::create(&out).unwrap_or_else(|e| die!(1, "Can't write {}: {e}", out.display()));
    let mut child = command
        .stdin(Stdio::null())
        .stdout(out_f.try_clone().unwrap())
        .stderr(out_f)
        .process_group(0) // keep the terminal's Ctrl-C away from the game; we stop it ourselves
        .spawn()
        .unwrap_or_else(|e| die!(1, "Can't run {}: {e}", cmd[0]));
    let pid = child.id();
    if let Some(pf) = &pid_file {
        fs::write(pf, format!("{pid}\n")).ok();
    }
    println!("Headless: pid {pid}, output in {}, game log in {}", ctx.rel(&out), ctx.rel(&watch.log));
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
            stop_game(child, o.server);
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
        if !o.server {
            hide_app(pid);
        }
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
    if matches!(status, Status::Ready) && !o.run.is_empty() && !run_when_ready(&watch, &ctx, &o.run, 60) {
        run_rc = 1;
    }
    check_interrupt(&mut child);

    match status {
        Status::Ready => {
            if o.keep_running {
                if o.server {
                    println!("Left running (pid {pid}). Commands: ./run-pack.sh --console; stop it with: ./run-pack.sh --send stop");
                } else {
                    println!("Left running, hidden (pid {pid}). Click its Dock icon to show it; stop it with: kill {pid}");
                }
                summary(&watch, &ctx);
                exit(run_rc);
            }
            stop_game(&mut child, o.server);
            summary(&watch, &ctx);
            exit(run_rc);
        }
        Status::Timeout => {
            stop_game(&mut child, o.server);
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
