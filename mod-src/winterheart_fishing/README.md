# Winterheart: Fishing

**An add-on for the Winterheart modpack.** Fishing rods catch the fish you can actually see in the water.

Forge 1.20.1 mod by soldierdonkey2, ported from Simple Fishing Overhaul by pajic.

## What it does

- A bite needs a real fish near the bobber. It swims up, bites, and reeling in removes it and gives the item set for it, a Butchery carcass for cod, salmon and pufferfish.
- Fish further off can become interested in the bobber and swim over. Lure widens that area.
- Hold right-click to charge a cast: the longer you hold, the farther it goes.
- Killing a fish drops none of its normal loot and no Butchery carcass. Named fish and fish released from a bucket can't be caught.

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds
- The settings file `data/winterheart/fishing/config.json` comes from the **Winterheart: Fishing Data** datapack. Without it the built-in defaults apply.

[Fish No Stuck](https://www.curseforge.com/minecraft/mc-mods/fish-no-stuck) is recommended.

## Configuration

Edit `data/winterheart/fishing/config.json`, then `/reload`. It is made for single-player: the client reads the same values as the game's built-in server.

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_fishing-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. A port of [Simple Fishing Overhaul](https://github.com/pajicadvance/simple-fishing-overhaul) by **pajic**, including its casting bar textures. MIT License; the original notice is in `META-INF/LICENSE-simple-fishing-overhaul`.
