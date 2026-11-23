#!/usr/bin/env bash
# Creates a new Winterheart mod from this template.
#
#   ./new-mod.sh <mod_id> "<Mod Name>" "<one-line description>" [--dev]
#   ./new-mod.sh winterheart_foo "Winterheart: Foo" "Adds foo to Winterheart."
#
# --dev makes a DEV-ONLY mod (debug tooling): no CurseForge plugin, publish task or project id,
# and mods.toml gets displayTest="IGNORE_ALL_VERSION". Dev-only mods must never ship in the modpack export.
set -euo pipefail

if [ $# -lt 3 ]; then
  sed -n '2,8p' "$0" | sed 's/^# \{0,1\}//'; exit 1
fi
ID="$1"; NAME="$2"; DESC="$3"; DEV="${4:-}"
[[ "$ID" =~ ^[a-z][a-z0-9_]*$ ]] || { echo "mod_id must be lowercase letters, digits and underscores"; exit 1; }

HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="$HERE/../$ID"
[ -e "$DEST" ] && { echo "$DEST already exists"; exit 1; }

# winterheart_foo -> WinterheartFoo
CLASS="$(echo "$ID" | awk -F_ '{for(i=1;i<=NF;i++) printf toupper(substr($i,1,1)) substr($i,2)}')"

mkdir "$DEST"
( cd "$HERE" && tar --exclude=new-mod.sh --exclude=build --exclude=.gradle --exclude=run -cf - . ) | tar -xf - -C "$DEST"

# Rename placeholder paths (deepest first)
find "$DEST" -depth -name '*__MOD_*' | while read -r p; do
  d="$(dirname "$p")"; b="$(basename "$p")"
  b="${b//__MOD_ID__/$ID}"; b="${b//__MOD_CLASS__/$CLASS}"
  mv "$p" "$d/$b"
done

# Replace placeholders inside text files
find "$DEST" -type f \( -name '*.java' -o -name '*.gradle' -o -name '*.properties' -o -name '*.toml' -o -name '*.md' -o -name '*.json' -o -name '*.mcmeta' \) ! -path '*/gradle/wrapper/*' |
while read -r f; do
  python3 - "$f" "$ID" "$CLASS" "$NAME" "$DESC" <<'PY'
import sys
f, i, c, n, d = sys.argv[1:]
s = open(f).read()
for k, v in (("__MOD_ID__", i), ("__MOD_CLASS__", c), ("__MOD_NAME__", n), ("__MOD_DESCRIPTION__", d)):
    s = s.replace(k, v)
open(f, "w").write(s)
PY
done

if [ "$DEV" = "--dev" ]; then
  python3 - "$DEST" <<'PY'
import re, sys
root = sys.argv[1]
p = root + "/build.gradle"
s = open(p).read()
s = re.sub(r"\n// ---- CurseForge publishing.*?// ---- end CurseForge publishing ----\n", "\n", s, flags=re.S)
s = s.replace("    id 'net.darkhax.curseforgegradle' version '1.1.28'\n", "")
open(p, "w").write(s)
p = root + "/gradle.properties"
s = open(p).read()
s = re.sub(r"\n## CurseForge.*", "\n", s, flags=re.S)
open(p, "w").write(s)
p = root + "/src/main/resources/META-INF/mods.toml"
s = open(p).read().replace('# displayTest=', 'displayTest=')
open(p, "w").write(s)
p = root + "/README.md"
s = open(p).read()
s = re.sub(r"### Publishing to CurseForge.*?(?=\n## Credits)", "", s, flags=re.S)
s = s.replace("**An add-on for the Winterheart modpack.**", "**Internal tooling for the Winterheart development instance.**")
s = s.replace("\nForge 1.20.1 mod by", "\n> **⚠ Do not ship this mod.** Leave `" + root.split('/')[-1] + "-*.jar` out of the modpack export. It is not published to CurseForge.\n\nForge 1.20.1 mod by", 1)
open(p, "w").write(s)
PY
fi

echo "Created $DEST ($CLASS${DEV:+, dev-only})"
echo "Next: write the Java/data, then follow docs/features/winterheart-mod-template.md (doc, feature-map, README index, CHANGELOG)."
[ "$DEV" = "--dev" ] && echo "DEV-ONLY: add it to the never-export list (see docs/features/winterheart-mod-template.md#dev-only-mods)."
exit 0
