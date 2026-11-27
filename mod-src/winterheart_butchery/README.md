# Winterheart: Butchery

**An add-on for the Winterheart modpack.** Shows Butchery's butchering and processing steps as JEI recipe pages.

Forge 1.20.1 mod by soldierdonkey2.

## What it does

Adds the JEI category **Carcass Butchering**. Each carcass has one scrollable page: kill, hang, bleed, then every butchering step with its tool and drops, beside the live animal and a swaying carcass preview.

It also adds **Skin Tanning**, **Skull Crushing** and **Acid Skeletons**.

## Considered, not relevant to Winterheart

JEI pages that were considered for these Butchery processes. They are not relevant to Winterheart, so none is planned.

- **Pig spit roast:** roasting a pig carcass on a wooden spit and carving it with a cleaver.
- **Wishbone plucking:** pulling a wishbone, or a broken one, from a drained chicken carcass by hand.
- **Frog balloon:** inflating a frog carcass into a balloon, and deflating it.
- **Displays and gadgets:** organs in jars, sausages on hooks, the turtle shell boat and similar.

## Requirements

- Minecraft **1.20.1**
- Forge **47.x** or newer 1.20.1 builds
- Client only. Does nothing without **JEI** (built against 15.56.0.205) and **Butchery** (data from 5.2)

## For developers

### Building

Requires JDK 17 (or 21 to run Gradle 8.8).

```
./gradlew build
```

The reobfuscated jar is written to `build/libs/` and copied into the instance's `mods/` folder, replacing any older `winterheart_butchery-*.jar`.

### Publishing to CurseForge

```
CURSEFORGE_TOKEN=your-token ./gradlew publishCurseForge
```

Set `curseforge_project_id` in `gradle.properties` first and bump `mod_version`. **Never commit the API token.** Release notes come from `CHANGELOG.md`.

## Credits and license

Made by **soldierdonkey2** for the Winterheart modpack. License: **All Rights Reserved**.
