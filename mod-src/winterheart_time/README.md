# Winterheart: Time

**The Winterheart custom day/night cycle.** Counts days, speeds days up and nights out, lets sleeping pass the day, brings the eternal night from day 50, drains sanity after midnight and rewinds the day when a player dies.

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Day counter, sleeping to pass the day, eternal night from day 50, sanity drain after midnight, the death loop (MineZero).
- `/customtime` admin commands.
- Tuning: `data/winterheart/time/config.json` (applied on `/reload`).

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

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_time-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
