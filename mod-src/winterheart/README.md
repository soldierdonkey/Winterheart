# Winterheart

**The core of the Winterheart modpack.** Registers the pack's custom items and the limb poising effect, adds a shapeless recipe type with remainders, and swaps the brewing recipes for the pack's own. The other Winterheart mods and datapacks build on it.

Forge 1.20.1 mod by soldierdonkey2.

## What it does

- Items: metal parts (`winterheart:<copper|iron|gold|diamond>_<plate|rod|ring|gear|bolt|screw>`), saltpeter and the nitrate solution bottles, the guide book and lost page.
- Effect: `winterheart:limb_poising`.
- Recipe type `winterheart:shapeless_with_remainders`: a shapeless recipe plus `"remainders": {"<ingredient>": "<item left behind>"}`.
- Brewing: all brewing recipes are removed, except crude nitrate solution + charred planks.
- Saltpeter taken from a furnace leaves the glass bottles behind.
- Converts the old `kubejs:` IDs of these items in existing worlds.

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds

## Configuration

None. Recipes are datapack JSON (`winterheart_recipes` datapack in the modpack).

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
