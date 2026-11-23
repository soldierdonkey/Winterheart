#!/usr/bin/env bash
# Builds the mods in mod-src/, replaces their jars in mods/, uploads them to CurseForge and bumps
# mod_version in gradle.properties for each mod that was uploaded.
# Usage: ./publish-mods.sh                  every mod
#        ./publish-mods.sh seppuku          one mod (or several names)
# Mods without a publish task or a curseforge_project_id are built and installed but not uploaded.
# Token: CURSEFORGE_TOKEN env var, or curseforge_token=... in ~/.gradle/gradle.properties. Never commit it.
# See docs/features/mod-publishing.md.
set -euo pipefail
root="$(cd "$(dirname "$0")" && pwd)"
cd "$root/mod-src"

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

if [ $# -gt 0 ]; then mods=("$@"); else mods=(); for d in */; do [ "$d" != "_template/" ] && mods+=("${d%/}"); done; fi
for mod in "${mods[@]}"; do
    [ -f "$mod/build.gradle" ] || { echo "No mod named $mod in mod-src/" >&2; exit 1; }
done

installed=() uploaded=() notes=()
for mod in "${mods[@]}"; do
    props="$mod/gradle.properties"
    id=$(sed -n 's/^mod_id=//p' "$props")
    version=$(sed -n 's/^mod_version=//p' "$props")
    project=$(sed -n 's/^curseforge_project_id=//p' "$props" | tr -d '[:space:]')
    # Mods that use jarJar (to bundle a library) ship the -all jar-in-jar build.
    if grep -q "jarJar.enable()" "$mod/build.gradle"; then tasks=(build jarJar); jar="$mod/build/libs/$id-$version-all.jar"
    else tasks=(build); jar="$mod/build/libs/$id-$version.jar"; fi

    upload=no
    if ! grep -q "register('publishCurseForge'" "$mod/build.gradle"; then notes+=("$mod not uploaded: dev-only, no publish task")
    elif [ -z "$project" ]; then notes+=("$mod not uploaded: curseforge_project_id is empty")
    elif [ "$token" = no ]; then notes+=("$mod not uploaded: no CurseForge token")
    else upload=yes; tasks+=(publishCurseForge); fi

    echo "==> $mod $version: ${tasks[*]}"
    (cd "$mod" && rm -rf build/libs && ./gradlew "${tasks[@]}")
    [ -f "$jar" ] || { echo "Built jar not found: $jar" >&2; exit 1; }

    rm -f "$root/mods/$id"-[0-9]*.jar
    cp "$jar" "$root/mods/$id-$version.jar"
    installed+=("$id-$version.jar")
    if [ "$upload" = yes ]; then
        # Next version: last number before any "-<mc version>" suffix goes up by one (2.12-1.20.1 -> 2.13-1.20.1).
        base=${version%%-*}; suffix=${version#"$base"}; last=${base##*.}; head=${base%"$last"}
        next="$head$((last + 1))$suffix"
        sed -i '' "s/^mod_version=.*/mod_version=$next/" "$props"
        uploaded+=("$mod $version -> project $project (now $next)")
    fi
done

echo
echo "Installed in mods/: ${installed[*]}"
echo "Uploaded: ${uploaded[*]:-none}"
for n in "${notes[@]+"${notes[@]}"}"; do echo "$n"; done
