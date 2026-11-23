"""Install and prepare the Forge dedicated server for run-pack.sh --server. See docs/features/run-pack.md.

  server_setup.py install   download the Forge installer and run --installServer into server/ (once per version)
  server_setup.py prepare   link mods, copy config etc. into server/, set server.properties, JVM args and the EULA
  server_setup.py argsfile  print the Forge args file path without installing (for --dry-run)

Inputs come from env vars set by run-pack.sh: GAME_DIR, JAVA, FORGE_VERSION, MEMORY_MB, WORLD,
RCON_PORT, RCON_PASSWORD_FILE, ACCEPT_EULA. Prints the java argument file (relative to server/) on success.
"""
import fnmatch, json, os, shutil, subprocess, sys, urllib.request

game_dir = os.environ["GAME_DIR"]
server_dir = os.path.join(game_dir, "server")
here = os.path.dirname(os.path.abspath(__file__))
EXCLUDE_FILE = os.path.join(here, "server_exclude.txt")
EULA_URL = "https://aka.ms/MinecraftEULA"
MAVEN = "https://maven.minecraftforge.net/net/minecraftforge/forge"

# Instance dirs the server reads, copied into server/ on every launch (see sync_shared).
SHARED_DIRS = ["config", "defaultconfigs", "datapacks", "tacz"]


def instance():
    try:
        with open(os.path.join(game_dir, "minecraftinstance.json")) as f:
            return json.load(f)
    except (OSError, ValueError):
        return {}


def forge_version():
    """Forge version number like 47.4.22, and Minecraft version like 1.20.1."""
    inst = instance()
    env = os.environ.get("FORGE_VERSION", "")
    loader = inst.get("baseModLoader") or {}
    ver = env[len("forge-"):] if env.startswith("forge-") else (env or loader.get("forgeVersion"))
    mc = inst.get("gameVersion") or "1.20.1"
    if not ver:
        sys.exit("Can't tell the Forge version: set FORGE_VERSION (e.g. forge-47.4.22)")
    return ver, mc


def args_file(mc, ver):
    return os.path.join("libraries", "net", "minecraftforge", "forge", f"{mc}-{ver}", "unix_args.txt")


def install():
    ver, mc = forge_version()
    rel = args_file(mc, ver)
    if os.path.exists(os.path.join(server_dir, rel)):
        print(rel)
        return
    os.makedirs(os.path.join(server_dir, ".installer"), exist_ok=True)
    name = f"forge-{mc}-{ver}-installer.jar"
    jar = os.path.join(server_dir, ".installer", name)
    if not os.path.exists(jar):
        url = f"{MAVEN}/{mc}-{ver}/{name}"
        print(f"Downloading {url}", file=sys.stderr)
        tmp = jar + ".part"
        # The Forge maven answers 403 to urllib's default User-Agent.
        req = urllib.request.Request(url, headers={"User-Agent": "winterheart-run-pack/1"})
        with urllib.request.urlopen(req, timeout=60) as r, open(tmp, "wb") as f:
            shutil.copyfileobj(r, f)
        os.replace(tmp, jar)
    print(f"Installing Forge {mc}-{ver} server into server/ (downloads the vanilla server and libraries)", file=sys.stderr)
    log = os.path.join(server_dir, ".installer", "install.log")
    with open(log, "w") as out:
        code = subprocess.call([os.environ["JAVA"], "-jar", jar, "--installServer", server_dir],
                               cwd=server_dir, stdout=out, stderr=subprocess.STDOUT)
    if code != 0 or not os.path.exists(os.path.join(server_dir, rel)):
        sys.exit(f"Forge server install failed (exit {code}), see server/.installer/install.log")
    print(rel)


def load_excludes():
    pats = []
    try:
        with open(EXCLUDE_FILE) as f:
            for line in f:
                line = line.split("#", 1)[0].strip()
                if line:
                    pats.append(line)
    except OSError:
        pass
    return pats


def link_mods():
    mods_src = os.path.join(game_dir, "mods")
    mods_dst = os.path.join(server_dir, "mods")
    if os.path.islink(mods_dst):
        os.unlink(mods_dst)
    os.makedirs(mods_dst, exist_ok=True)
    # Only remove what we own (symlinks); a real jar dropped into server/mods stays.
    for name in os.listdir(mods_dst):
        p = os.path.join(mods_dst, name)
        if os.path.islink(p):
            os.unlink(p)
    pats = load_excludes()
    linked, skipped = 0, []
    for name in sorted(os.listdir(mods_src)):
        if not name.endswith(".jar"):
            continue
        if any(fnmatch.fnmatch(name, p) for p in pats):
            skipped.append(name)
            continue
        os.symlink(os.path.join("..", "..", "mods", name), os.path.join(mods_dst, name))
        linked += 1
    print(f"server/mods: {linked} mods linked, {len(skipped)} client-only skipped (python/run_pack/server_exclude.txt)",
          file=sys.stderr)


def sync_shared():
    # Copied, not symlinked: some mods call Files.createDirectories(config), which throws
    # FileAlreadyExistsException on a symlink. A copy also keeps the server from writing into tracked config/.
    for d in SHARED_DIRS:
        src = os.path.join(game_dir, d)
        dst = os.path.join(server_dir, d)
        if not os.path.isdir(src):
            continue
        if os.path.islink(dst):
            os.unlink(dst)
        code = subprocess.call(["rsync", "-a", "--delete", src + "/", dst + "/"])
        if code != 0:
            sys.exit(f"Copying {d}/ into server/{d} failed (rsync exit {code})")


def set_properties(values):
    path = os.path.join(server_dir, "server.properties")
    lines = []
    if os.path.exists(path):
        with open(path) as f:
            lines = f.read().splitlines()
    todo = dict(values)
    for i, line in enumerate(lines):
        if line.startswith("#") or "=" not in line:
            continue
        key = line.split("=", 1)[0]
        if key in todo:
            lines[i] = f"{key}={todo.pop(key)}"
    lines += [f"{k}={v}" for k, v in todo.items()]
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")


def jvm_args():
    inst = instance()
    memory = os.environ.get("MEMORY_MB") or str(inst.get("allocatedMemory") or 7040)
    with open(os.path.join(server_dir, "user_jvm_args.txt"), "w") as f:
        f.write("# Written by run-pack.sh --server on every launch; set MEMORY_MB to change.\n")
        f.write(f"-Xmx{memory}m\n-Xms256m\n-Duser.language=en\n")


def eula():
    path = os.path.join(server_dir, "eula.txt")
    try:
        with open(path) as f:
            if "eula=true" in f.read():
                return
    except OSError:
        pass
    if os.environ.get("ACCEPT_EULA") != "1":
        if not sys.stdin.isatty():
            sys.exit(f"The server needs the Minecraft EULA ({EULA_URL}) accepted: rerun with --accept-eula")
        # Prompt on stderr: run-pack.sh captures stdout.
        sys.stderr.write(f"Running a Minecraft server means agreeing to the Minecraft EULA ({EULA_URL}). Agree? [y/N] ")
        sys.stderr.flush()
        ans = sys.stdin.readline()
        if ans.strip().lower() not in ("y", "yes"):
            sys.exit("EULA not accepted, not starting the server")
    with open(path, "w") as f:
        f.write(f"# Accepted through run-pack.sh ({EULA_URL})\neula=true\n")


def prepare():
    ver, mc = forge_version()
    rel = args_file(mc, ver)
    if not os.path.exists(os.path.join(server_dir, rel)):
        sys.exit("Forge server not installed: run 'server_setup.py install' first")
    eula()
    link_mods()
    sync_shared()
    with open(os.environ["RCON_PASSWORD_FILE"]) as f:
        password = f.read().strip()
    set_properties({
        "online-mode": "false",  # run-pack's client logs in offline
        "enable-rcon": "true",
        "rcon.port": os.environ["RCON_PORT"],
        "rcon.password": password,
        "broadcast-rcon-to-ops": "false",
        "level-name": os.environ.get("WORLD") or "world",
    })
    jvm_args()
    print(rel)


def argsfile():
    ver, mc = forge_version()
    print(args_file(mc, ver))


if __name__ == "__main__":
    {"install": install, "prepare": prepare, "argsfile": argsfile}.get(sys.argv[1] if len(sys.argv) > 1 else "", lambda: sys.exit(__doc__))()
