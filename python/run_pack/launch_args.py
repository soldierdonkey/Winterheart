"""Print the java launch arguments for run-pack.sh, NUL-separated.

Reads the Forge and vanilla version JSONs from the CurseForge install.
Inputs come from env vars set by run-pack.sh. See docs/features/run-pack.md.
"""
import hashlib, json, os, platform, sys, uuid

game_dir = os.environ["GAME_DIR"]
install = os.environ["MC_INSTALL"]
username = os.environ["USERNAME"]
versions = os.path.join(install, "versions")
libdir = os.path.join(install, "libraries")

inst = {}
try:
    with open(os.path.join(game_dir, "minecraftinstance.json")) as f:
        inst = json.load(f)
except (OSError, ValueError):
    pass

version_id = os.environ["FORGE_VERSION"] or (inst.get("baseModLoader") or {}).get("name")
if not version_id:
    sys.exit("Can't tell the Forge version: set FORGE_VERSION (e.g. forge-47.4.22)")
memory = os.environ["MEMORY_MB"] or str(inst.get("allocatedMemory") or 7040)
extra_jvm = (inst.get("javaArgsOverride") or "").split()

def load(vid):
    path = os.path.join(versions, vid, vid + ".json")
    if not os.path.exists(path):
        sys.exit(f"Missing {path}: launch the instance once from CurseForge to install it")
    with open(path) as f:
        return json.load(f)

child = load(version_id)
parent = load(child["inheritsFrom"]) if child.get("inheritsFrom") else {}

arch = platform.machine()
def rules_ok(rules):
    if not rules:
        return True
    ok = False
    for r in rules:
        match = True
        osr = r.get("os") or {}
        if osr.get("name") and osr["name"] != "osx":
            match = False
        if osr.get("arch") == "x86" and arch not in ("i386", "i686"):
            match = False
        if r.get("features"):
            match = False  # demo, custom resolution, quick play: all off
        if match:
            ok = r["action"] == "allow"
    return ok

# Libraries: Forge's entries win over vanilla ones with the same group:artifact:classifier.
libs, order = {}, []
for src in (parent, child):
    for lib in src.get("libraries", []):
        if not rules_ok(lib.get("rules")):
            continue
        art = (lib.get("downloads") or {}).get("artifact")
        if not art:
            continue
        g, a, *rest = lib["name"].split(":")
        key = (g, a, rest[1] if len(rest) > 1 else "")
        if key not in libs:
            order.append(key)
        libs[key] = os.path.join(libdir, art["path"])

cp = []
for key in order:
    p = libs[key]
    if not os.path.exists(p):
        sys.exit(f"Missing library {p}: launch the instance once from CurseForge to download it")
    cp.append(p)
cp.append(os.path.join(versions, version_id, version_id + ".jar"))

asset_index = (parent.get("assetIndex") or child.get("assetIndex") or {}).get("id") or parent.get("assets")
natives = os.path.join(install, "natives", version_id)

# Offline UUID, same as the vanilla server: UUIDv3 of "OfflinePlayer:<name>".
h = bytearray(hashlib.md5(("OfflinePlayer:" + username).encode()).digest())
h[6] = (h[6] & 0x0F) | 0x30
h[8] = (h[8] & 0x3F) | 0x80
player_uuid = str(uuid.UUID(bytes=bytes(h)))

subs = {
    "natives_directory": natives,
    "launcher_name": "winterheart-run-pack",
    "launcher_version": "1",
    "classpath": os.pathsep.join(cp),
    "classpath_separator": os.pathsep,
    "library_directory": libdir,
    "version_name": version_id,
    "auth_player_name": username,
    "game_directory": game_dir,
    "assets_root": os.path.join(install, "assets"),
    "assets_index_name": asset_index,
    "auth_uuid": player_uuid,
    "auth_access_token": "0",
    "clientid": "0",
    "auth_xuid": "0",
    "user_type": "legacy",
    "version_type": child.get("type", "release"),
}
def sub(s):
    for k, v in subs.items():
        s = s.replace("${" + k + "}", v)
    return s

def expand(entries):
    out = []
    for e in entries:
        if isinstance(e, str):
            out.append(sub(e))
        elif rules_ok(e.get("rules")):
            v = e["value"]
            out.extend(sub(x) for x in ([v] if isinstance(v, str) else v))
    return out

pa, ca = parent.get("arguments", {}), child.get("arguments", {})
# Same extra flags CurseForge passes for this instance.
jvm = [
    f"-Xmx{memory}m", "-Xms256m",
    "-Dfml.ignorePatchDiscrepancies=true",
    "-Dfml.ignoreInvalidMinecraftCertificates=true",
    "-Duser.language=en",
] + extra_jvm
log_cfg = (parent.get("logging") or {}).get("client")
if log_cfg:
    p = os.path.join(install, "assets", "log_configs", log_cfg["file"]["id"])
    if os.path.exists(p):
        jvm.append(log_cfg["argument"].replace("${path}", p))
jvm += expand(pa.get("jvm", [])) + expand(ca.get("jvm", []))
game = expand(pa.get("game", [])) + expand(ca.get("game", []))

for a in jvm + [child.get("mainClass") or parent["mainClass"]] + game:
    sys.stdout.write(a + "\0")
