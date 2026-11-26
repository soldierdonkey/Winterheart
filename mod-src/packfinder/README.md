# PackFinder

**An add-on for the Winterheart modpack.** Loads every resource pack and datapack in the instance's `resourcepacks/` and `datapacks/` folders, so packs can stay where the CurseForge app installs them.

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Every zip or folder pack in `resourcepacks/` and `datapacks/` is always enabled, in every world.
- Priority among those packs is fixed by the order files, not by `options.txt` or the world.

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds

## Configuration

`config/packfinder/resourcepack_order.json` and `config/packfinder/datapack_order.json` (both optional):

```json
{ "loadOrder": ["lowest priority.zip", "highest priority"] }
```

Entries are file or folder names. Packs that aren't listed load alphabetically, below the listed ones. Changes apply on F3+T or `/reload`.

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `packfinder-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
