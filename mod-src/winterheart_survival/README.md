# Winterheart: Survival

**Survival mechanics for the Winterheart modpack: sanity-based stalker despawn and the seppuku trigger, limb poising, proximity effects and the beacon base.**

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Stalkers (`sanitydim`) despawn once every player is above 75 sanity.
- Plays the Seppuku mod's animation at 10 sanity or less.
- Limb poising: damaged Legendary Survival Overhaul limbs drift in health until treated; `/reload` tuning in `limb_poising/config.json`.
- Proximity effects: titles and effects near gore, rotting flesh, furniture, warmth and so on; data-driven in `proximity/rules.json`.
- Right-clicking a beacon builds an iron base.

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds

## Configuration

Tuning is datapack JSON in the Winterheart modpack's `datapacks/` folder (see the instance's `docs/data-map.md`).

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_survival-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
