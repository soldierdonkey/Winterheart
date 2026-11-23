# __MOD_NAME__

**An add-on for the Winterheart modpack.** TODO: one-paragraph summary.

Forge 1.20.1 mod by soldierdonkey2.

## What it does

TODO

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds

## Configuration

TODO: list the JSON files under `data/__MOD_ID__/__MOD_ID__/` (or remove this section). Edit, then `/reload`.

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `__MOD_ID__-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
