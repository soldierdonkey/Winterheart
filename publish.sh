#!/usr/bin/env bash
# Builds and uploads our mods (mod-src/) and our datapacks and resource packs to CurseForge, then bumps the
# version of each one that was uploaded. Mod jars replace the ones in mods/; pack zips are written to
# python/publish/build/libs/. A pack is ours when its folder has a pack.properties.
# Usage: ./publish.sh                                  every mod and pack
#        ./publish.sh seppuku winterheart_recipes      only these
# Anything without a curseforge_project_id is built but not uploaded.
# Token: CURSEFORGE_TOKEN env var, or curseforge_token=... in ~/.gradle/gradle.properties. Never commit it.
# See docs/features/mod-publishing.md.
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
cd "$root"

token=no
if [ -n "${CURSEFORGE_TOKEN:-}" ] || grep -qs '^curseforge_token=.' ~/.gradle/gradle.properties; then token=yes; fi
# Gradle 8.8 can't compile build scripts on newer system JDKs (22+). When JAVA_HOME is unset, use the toolchain
# JDK 17 Gradle downloaded, else an installed JDK 21.
if [ -z "${JAVA_HOME:-}" ]; then
    jdk=$(ls -d ~/.gradle/jdks/*17*/*/Contents/Home 2>/dev/null | head -1 || true)
    if [ -z "$jdk" ] && [ -x /usr/libexec/java_home ]; then
        jdk=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
        case "$jdk" in *21*) ;; *) jdk= ;; esac
    fi
    if [ -n "$jdk" ]; then export JAVA_HOME="$jdk"; fi
fi

# Next version: last number before any "-<mc version>" suffix goes up by one (2.12-1.20.1 -> 2.13-1.20.1).
next_version() {
    local base=${1%%-*}
    local suffix=${1#"$base"} last=${base##*.}
    echo "${base%"$last"}$((last + 1))$suffix"
}
prop() { sed -n "s/^$2=//p" "$1" | tr -d '\r'; }

# Sort the arguments (default: everything) into mods and pack folders, before building anything.
mods=() packs=()
if [ $# -gt 0 ]; then
    for name in "$@"; do
        if [ -f "mod-src/$name/build.gradle" ] && [ "$name" != _template ]; then mods+=("$name")
        elif [ -f "datapacks/$name/pack.properties" ]; then packs+=("datapacks/$name")
        elif [ -f "resourcepacks/$name/pack.properties" ]; then packs+=("resourcepacks/$name")
        else echo "No mod in mod-src/ or pack with a pack.properties named $name" >&2; exit 1; fi
    done
else
    for d in mod-src/*/; do d=${d%/}; [ "$d" != mod-src/_template ] && mods+=("${d#mod-src/}"); done
    for f in datapacks/*/pack.properties resourcepacks/*/pack.properties; do [ -f "$f" ] && packs+=("${f%/pack.properties}"); done
fi

built=() uploaded=() notes=()
for mod in ${mods[@]+"${mods[@]}"}; do
    dir="mod-src/$mod"
    props="$dir/gradle.properties"
    id=$(prop "$props" mod_id)
    version=$(prop "$props" mod_version)
    project=$(prop "$props" curseforge_project_id | tr -d '[:space:]')
    # Mods that use jarJar (to bundle a library) ship the -all jar-in-jar build.
    if grep -q "jarJar.enable()" "$dir/build.gradle"; then tasks=(build jarJar); jar="$dir/build/libs/$id-$version-all.jar"
    else tasks=(build); jar="$dir/build/libs/$id-$version.jar"; fi

    upload=no
    if ! grep -q "register('publishCurseForge'" "$dir/build.gradle"; then notes+=("$mod not uploaded: dev-only, no publish task")
    elif [ -z "$project" ]; then notes+=("$mod not uploaded: curseforge_project_id is empty")
    elif [ "$token" = no ]; then notes+=("$mod not uploaded: no CurseForge token")
    else upload=yes; tasks+=(publishCurseForge); fi

    echo "==> $mod $version: ${tasks[*]}"
    (cd "$dir" && rm -rf build/libs && ./gradlew "${tasks[@]}")
    [ -f "$jar" ] || { echo "Built jar not found: $jar" >&2; exit 1; }

    rm -f "$root/mods/$id"-[0-9]*.jar
    cp "$jar" "$root/mods/$id-$version.jar"
    built+=("mods/$id-$version.jar")
    if [ "$upload" = yes ]; then
        next=$(next_version "$version")
        sed -i '' "s/^mod_version=.*/mod_version=$next/" "$props"
        uploaded+=("$mod $version -> project $project (now $next)")
    fi
done

for dir in ${packs[@]+"${packs[@]}"}; do
    pack=${dir##*/}
    props="$dir/pack.properties"
    version=$(prop "$props" pack_version)
    project=$(prop "$props" curseforge_project_id | tr -d '[:space:]')
    zip="python/publish/build/libs/$pack-$version.zip"
    [ -f "$dir/pack.mcmeta" ] && [ -f "$dir/README.md" ] || { echo "$dir needs a pack.mcmeta and a README.md" >&2; exit 1; }

    upload=no
    if [ -z "$project" ]; then notes+=("$pack not uploaded: curseforge_project_id is empty"); tasks=(packZip)
    elif [ "$token" = no ]; then notes+=("$pack not uploaded: no CurseForge token"); tasks=(packZip)
    else upload=yes; tasks=(publishCurseForge); fi

    echo "==> $pack $version: ${tasks[*]}"
    rm -f "$zip"
    # The packs have no build of their own: one shared Gradle project, run with the template's wrapper.
    mod-src/_template/gradlew -p python/publish "-Ppack_dir=$root/$dir" "${tasks[@]}"
    [ -f "$zip" ] || { echo "Built zip not found: $zip" >&2; exit 1; }

    built+=("$zip")
    if [ "$upload" = yes ]; then
        next=$(next_version "$version")
        sed -i '' "s/^pack_version=.*/pack_version=$next/" "$props"
        uploaded+=("$pack $version -> project $project (now $next)")
    fi
done

echo
echo "Built: ${built[*]}"
echo "Uploaded: ${uploaded[*]:-none}"
for n in "${notes[@]+"${notes[@]}"}"; do echo "$n"; done
