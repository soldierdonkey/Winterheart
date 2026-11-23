# mod-publishing

`./publish-mods.sh` builds the mods in `mod-src/`, replaces their jars in `mods/`, and uploads them to CurseForge with each mod's `publishCurseForge` Gradle task (plugin `net.darkhax.curseforgegradle` 1.1.28). Ported from the Soulrend development instance.

## Files

- `publish-mods.sh`: the command. It loops over `mod-src/*/` (skipping `_template`), or over the mods named as arguments, and installs the jars into `mods/`.
- `mod-src/seppuku/build.gradle`: `publishCurseForge` (from the template, requires `playeranimator`).
- `mod-src/*/gradle.properties`: `curseforge_project_id`, `curseforge_release_type`, `mod_version`.

## How it works

```
./publish-mods.sh            # every mod
./publish-mods.sh seppuku    # one mod (or several names)
```

1. If `JAVA_HOME` is unset, it uses Gradle's downloaded JDK 17 (`~/.gradle/jdks/*17*`). Failing that, it uses an installed JDK 21 (`/usr/libexec/java_home -v 21`). Gradle 8.8 can't compile build scripts on JDK 22+.
2. For each mod, it clears `build/libs` and runs `./gradlew build` (plus `jarJar` if the mod's build uses `jarJar.enable()`). It adds `publishCurseForge` only when the mod has that task, a `curseforge_project_id` is set, and a token is set (`CURSEFORGE_TOKEN`, or `curseforge_token=` in `~/.gradle/gradle.properties`). Otherwise it prints why the mod was not uploaded.
3. It deletes that mod's `mods/<mod_id>-<version>.jar` files and copies the new jar in as `mods/<mod_id>-<mod_version>.jar`. For jarJar mods it copies the `-all` build.
4. After a successful upload, it bumps `mod_version` in that mod's `gradle.properties`: the last number before any `-<minecraft>` suffix goes up by one (`1.0.0` → `1.0.1`, `2.12-1.20.1` → `2.13-1.20.1`). Mods that were not uploaded keep their version.
5. It stops at the first mod that fails and prints what was installed and uploaded.

The upload is named `<mod_name> <mod_version>` and marked for `minecraft_version`, Forge, Java 17, client and server. Release notes come from `mod-src/<mod>/CHANGELOG.md`.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `curseforge_project_id` | `mod-src/<mod>/gradle.properties` | empty | Required for upload. The mod is built and installed but not uploaded while this is empty. |
| `curseforge_release_type` | `mod-src/<mod>/gradle.properties` | `release` | `release`, `beta` or `alpha` |
| `mod_version` | `mod-src/<mod>/gradle.properties` | per mod | Uploaded as-is, then bumped by the command. Change it by hand for a bigger version jump. |

## Changes

- 2026-11-22: Ported `publish-mods.sh` from Soulrend. The soulrend_core comment became a generic jarJar rule, and the JDK 21 fallback was added. Tested `./publish-mods.sh seppuku` three times: each run built the mod, installed `mods/seppuku-1.0.0.jar` and printed `seppuku not uploaded: curseforge_project_id is empty`. Nothing was uploaded.

## Gotchas

- Never commit the token. It is read only from the environment or the user-level `~/.gradle/gradle.properties`.
- Seppuku has no CurseForge project yet. Set `curseforge_project_id` before the first upload.
- Every run rewrites the jars in `mods/`. The contents stay the same, but zip timestamps differ, so git shows them as modified. Restore them (`git checkout -- mods/<jar>`) when the mod didn't actually change.
- On the first test run, `git status` reported `mods/seppuku-1.0.0.jar` as deleted right after the script finished. Later runs left it in place. The cause is not known. Check that `mods/` still has the jar after publishing.
