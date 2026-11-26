# Winterheart: Guides

**Trigger-based guide books and lost journal pages for the Winterheart modpack.**

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Guides unlock from sweeps (what a player stands on, looks at or carries) and events (breaking, crafting, kills, time phases, proximity, ...), for the whole world.
- A popup book opens for every player and the server pauses (Multiplayer Server Pause) until it is closed.
- `/guides` admin and debug commands, a JEI plugin, and a Java API (`GuideApi`).
- Data: `datapacks/winterheart_guides/.../guides.json`, hot-reloaded every 2 seconds.

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

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_guides-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
