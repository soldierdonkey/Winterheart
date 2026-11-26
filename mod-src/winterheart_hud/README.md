# Winterheart: HUD

**The Winterheart survival HUD: a snowy spruce panel (top right) with day, time, weather and temperature.**

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Other mods add sections with `HudApi.addSection`.
- Textures are in the mod; `python/survival_hud/` in the instance repo regenerates them.

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

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_hud-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
