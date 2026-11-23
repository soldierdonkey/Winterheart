# Winterheart: Structures

**Places the Winterheart hand-built structures (watchtower, shipwreck, beacon) once per world at fixed spots.**

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Spots come from `data/winterheart/fixed_structures/placements.json` (written by the world painter); profiles in `profiles.json`. Applied on `/reload`.
- Fixups restore block states `/place template` recomputes wrongly.

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds

## Configuration

Tuning is datapack JSON in the Winterheart modpack's Paxi packs (see the instance's `docs/data-map.md`).

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_structures-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
