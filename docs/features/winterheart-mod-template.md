# winterheart-mod-template

Starting point for new custom Winterheart mods (Forge 1.20.1, ForgeGradle 6), plus a script that stamps out a new mod from it. Add `--dev` for a dev-only mod (debug tooling that never ships). Ported from the Soulrend development instance's template.

## Files

- `mod-src/_template/`: the template. It is not a buildable mod itself, because it contains `__MOD_*__` placeholders.
- `mod-src/_template/new-mod.sh`: scaffolding script.
- `mod-src/_template/build.gradle`: ForgeGradle build. It includes the `copyJarToMods` task and a CurseForge block fenced by `// ---- CurseForge publishing` … `// ---- end CurseForge publishing ----`.
- `mod-src/_template/gradle.properties`: versions (MC 1.20.1, Forge 47.4.10) and mod metadata placeholders. Group `io.github.soldierdonkey2.<mod_id>`, author `soldierdonkey2`.
- `mod-src/_template/settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/`: Gradle 8.8 wrapper.
- `mod-src/_template/src/main/java/io/github/soldierdonkey2/__MOD_ID__/__MOD_CLASS__.java`: `@Mod` entry point, logger only.
- `mod-src/_template/src/main/resources/META-INF/mods.toml`: forge + minecraft deps, with commented examples of a load-after dependency and of `displayTest`.
- `mod-src/_template/src/main/resources/pack.mcmeta`: `pack_format` 15.
- `mod-src/_template/src/main/resources/assets/__MOD_ID__/lang/en_us.json`, `…/data/__MOD_ID__/.gitkeep`: empty asset/data roots.
- `mod-src/_template/README.md`, `mod-src/_template/CHANGELOG.md`: skeletons.

## How it works

```sh
cd mod-src
./_template/new-mod.sh winterheart_foo "Winterheart: Foo" "One-line description."           # shipped mod
./_template/new-mod.sh winterheart_debug "Winterheart: Debug" "Dev tools." --dev            # dev-only mod
```

The script copies the template to `mod-src/<mod_id>/` and replaces:

| Placeholder | Becomes |
| --- | --- |
| `__MOD_ID__` | The `mod_id` argument (lowercase letters, digits, `_`). Also used for the Java package `io.github.soldierdonkey2.<mod_id>`, the asset/data namespace and the jar name. |
| `__MOD_CLASS__` | `mod_id` in CamelCase (`winterheart_foo` → `WinterheartFoo`) |
| `__MOD_NAME__`, `__MOD_DESCRIPTION__` | The other two arguments |

Then fill in the TODOs. `mod-src/*/build/`, `.gradle/`, `run/` and `bin/` are already gitignored. The `build` task copies the reobfuscated jar into `mods/`, replacing older `<mod_id>-*.jar` files. Build with JDK 17 or 21:

```sh
cd mod-src/<mod_id> && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew build
```

Useful patterns:

- JSON config as an `AddReloadListenerEvent` listener, so datapacks can override it and `/reload` picks it up.
- Optional mods behind `ModList.isLoaded`, kept in a `compat/` package.
- `ordering="AFTER"` in `mods.toml` when the mod's data must override another mod's data.
- A small static API class (like `SeppukuApi`) when another mod should call this one (compile against it: see the `compileOnly files('../<mod>/build/classes/java/main')` lines the new mods' `build.gradle` get from `newmod.sh`).

### Dev-only mods

> **⚠ Dev-only mods must never ship.** They are tools for the development instance and must not end up in the exported modpack.

`--dev` produces that variant:

- no `net.darkhax.curseforgegradle` plugin, no `publishCurseForge` task, no `curseforge_project_id`
- `displayTest="IGNORE_ALL_VERSION"` in `mods.toml`, so servers and other clients don't need the mod
- the README gets the "do not ship" banner

Checklist for every dev-only mod:

1. Untick its jar in the CurseForge export dialog.
2. Mark it **Dev-only: never include in the export, not on CurseForge** in its [feature-map.md](../feature-map.md#our-own-mods) row and in its feature doc.
3. Guard client code with `FMLEnvironment.dist == Dist.CLIENT` and use client commands, so nothing runs on a server.
4. Don't give it a CurseForge project. If it ever needs to ship, create a normal mod from the template instead of removing the banner.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `mod_id`, `mod_name`, `mod_version`, `mod_description` | `mod-src/<mod_id>/gradle.properties` | set by `new-mod.sh` | Metadata expanded into `mods.toml` and `pack.mcmeta` |
| `forge_version`, `minecraft_version` | `mod-src/<mod_id>/gradle.properties` | 47.4.10 / 1.20.1 | Keep in step with the instance (`minecraftinstance.json`) |
| `curseforge_project_id` | `mod-src/<mod_id>/gradle.properties` | empty | Required before `publishCurseForge`; see [mod-publishing](mod-publishing.md). Not present with `--dev`. |

## Changes

- 2026-11-22: Ported from the Soulrend template, with Winterheart names, the `io.github.soldierdonkey2` group and author soldierdonkey2. Scaffolded a normal and a `--dev` mod in a scratch folder: the placeholders were replaced, and the `--dev` build.gradle has no CurseForge block. The scaffolded mods were not compiled.

## Gotchas

- The template itself isn't a buildable project (it has placeholders in paths and files). Always scaffold first.
- Gradle 8.8 needs JDK 17 or 21. A newer default JDK fails with `Unsupported class file major version`. Set `JAVA_HOME`.
- A new mod is a new feature. Add `docs/features/<feature-name>.md`, a row in [feature-map.md](../feature-map.md), a row in the index in [docs/README.md](../README.md), and a CHANGELOG line if the change is player-visible.
- The `--dev` edits are text substitutions on the template. If the CurseForge fence markers in `build.gradle` change, update `new-mod.sh` too.
