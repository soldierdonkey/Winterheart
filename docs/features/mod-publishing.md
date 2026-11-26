# mod-publishing

`./publish.sh` builds our mods (`mod-src/`) and our datapacks and resource packs, and uploads them to CurseForge with a `publishCurseForge` Gradle task (plugin `net.darkhax.curseforgegradle` 1.1.28). Mod jars also replace the ones in `mods/`. The mod half was ported from the Soulrend development instance.

## Files

- `publish.sh`: the command.
- `mod-src/*/build.gradle`: each mod's `publishCurseForge` (from the template).
- `mod-src/*/gradle.properties`: `curseforge_project_id`, `curseforge_release_type`, `mod_version`.
- `python/publish/build.gradle`, `python/publish/settings.gradle`: shared Gradle project that zips one pack (`packZip`) and uploads it.
- `datapacks/winterheart_*/pack.properties`, `resourcepacks/winterheart_assets/pack.properties`: pack name, version, `curseforge_project_id`, release type.
- `datapacks/winterheart_*/README.md`, `resourcepacks/winterheart_assets/README.md`: what each pack does and needs; the text for its CurseForge description.
- `datapacks/winterheart_*/CHANGELOG.md`, `resourcepacks/winterheart_assets/CHANGELOG.md`: release notes.

## How it works

```
./publish.sh                                # every mod and pack
./publish.sh seppuku winterheart_recipes    # only these
```

A name is a mod (`mod-src/<name>/`) or a pack folder with a `pack.properties` in `datapacks/` or `resourcepacks/`. Packs without that file, including third-party ones, are never touched. An unknown name stops the run before anything is built.

1. If `JAVA_HOME` is unset, it uses Gradle's downloaded JDK 17 (`~/.gradle/jdks/*17*`). Failing that, it uses an installed JDK 21 (`/usr/libexec/java_home -v 21`). Gradle 8.8 can't compile build scripts on JDK 22+.
2. For each mod, it clears `build/libs` and runs `./gradlew build` (plus `jarJar` if the mod's build uses `jarJar.enable()`). It deletes that mod's `mods/<mod_id>-<version>.jar` files and copies the new jar in as `mods/<mod_id>-<mod_version>.jar` (the `-all` build for jarJar mods).
3. For each pack, it runs `packZip` through the mod template's Gradle wrapper, writing `python/publish/build/libs/<pack>-<pack_version>.zip` with `pack.mcmeta` at the root. `pack.properties`, `CHANGELOG.md` and `.DS_Store` are left out. The instance keeps using the folder.
4. `publishCurseForge` runs only when a `curseforge_project_id` and a token are set (`CURSEFORGE_TOKEN`, or `curseforge_token=` in `~/.gradle/gradle.properties`), and for mods, when the build has that task. Otherwise it prints why nothing was uploaded.
5. After a successful upload, it bumps `mod_version` or `pack_version`: the last number before any `-<minecraft>` suffix goes up by one (`1.0.0` → `1.0.1`, `2.12-1.20.1` → `2.13-1.20.1`). Anything not uploaded keeps its version.
6. It stops at the first failure.

An upload is named `<name> <version>` and marked for Minecraft 1.20.1, with `CHANGELOG.md` as its notes. Mods are also marked Forge, Java 17, client and server.

A CurseForge project page, including its description, logo and license, is set up on the website; the upload doesn't send the README as the description.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `curseforge_project_id` | `mod-src/<mod>/gradle.properties`, `<pack>/pack.properties` | empty | Required for upload. Built but not uploaded while this is empty. |
| `curseforge_release_type` | same | `release` | `release`, `beta` or `alpha` |
| `mod_version`, `pack_version` | same | per project | Uploaded as-is, then bumped by the command. Change it by hand for a bigger version jump. |
| `pack_name` | `<pack>/pack.properties` | per pack | Upload display name |

## Changes

- 2026-11-26: Renamed `publish-mods.sh` to `publish.sh` and added the nine in-house packs, each with a `README.md`, `CHANGELOG.md` and `pack.properties`. World Painter's install and uninstall now keep those files in `winterheart_novoatlas`. Tested `./publish.sh` with all mods and packs: everything built, the nine zips had `pack.mcmeta` and `README.md` at the root, and nothing was uploaded (no project IDs). Unknown names were rejected. Uploading a pack has not been tested.
- 2026-11-22: Ported `publish-mods.sh` from Soulrend. The soulrend_core comment became a generic jarJar rule, and the JDK 21 fallback was added. Tested `./publish-mods.sh seppuku` three times: each run built the mod, installed `mods/seppuku-1.0.0.jar` and printed `seppuku not uploaded: curseforge_project_id is empty`. Nothing was uploaded.

## Gotchas

- Never commit the token. It is read only from the environment or the user-level `~/.gradle/gradle.properties`.
- Seppuku has no CurseForge project yet. Set `curseforge_project_id` before the first upload.
- A new in-house pack is published only once it has a `pack.properties`; give it a `README.md` and `CHANGELOG.md` too.
- `winterheart_guides` only works as an unzipped folder: the guides mod reads `datapacks/winterheart_guides/` directly. If the CurseForge app installs its zip into `datapacks/`, packfinder loads it, but guide data is still read from the folder.
- Don't put a published pack zip in `datapacks/` or `resourcepacks/` next to its folder: both would load.
- A run with no names rebuilds every mod.
- Every mod build rewrites the jars in `mods/`. The contents stay the same, but zip timestamps differ, so git shows them as modified. Restore them (`git checkout -- mods/<jar>`) when the mod didn't actually change.
- On the first test run, `git status` reported `mods/seppuku-1.0.0.jar` as deleted right after the script finished. Later runs left it in place. The cause is not known. Check that `mods/` still has the jar after publishing.
