## Winterheart (unreleased)

### Features:

- tacz-guns: guns now repair with iron bolts, more for guns that take more iron to build, and start jamming below 90% durability. Guns wear out much faster.

- tacz-guns: gun translations are updated to the ones shipped with TACZ. The period gun set is unchanged, now applied by the `winterheart_tacz` datapack.

- spark: added the spark 1.10.53 profiler for diagnosing freezes and memory use.

- mod-configs: disabled Citadel's April Fools content.

- resource-packs: removed `Clear Glass Pack 1.20.zip`. Glass looks vanilla again.

- resource-packs: removed `TheForestCustomTextures.zip`, `Universal Bushy Leaves.zip` and `Hidden-Armor-1.2.0.zip`. Worn armor is visible again, and The Forest's menu textures are gone.

- resource-packs: added Polytone 3.5.26, so Dark Atmosphere's sky, lighting and water changes now apply.

- packfinder: new mod that always loads every pack in `resourcepacks/` and `datapacks/`, replacing Paxi.

- resource-packs: `Enchanting Reimagined` is now enabled. Removed `Quark Programmer Art.zip`, `Omega-Colorful-Lighting-Compat.zip` and the `TheForest-Colorful-Lighting-Compat` packs.

- resource-packs: removed `AHM.zip` and `§6Dynamic§9Surroundings §2R19.zip`, two large packs that added unused sounds.

- singleplayer-only: launching the pack as a dedicated server now crashes on load with a message that Winterheart is singleplayer only.

- winterheart-core: new core mod `winterheart` registers the metal parts, saltpeter and nitrate bottles, guide book, lost page and the limb poising effect (formerly KubeJS). The old `kubejs:` items in existing worlds convert automatically. Brewing is unchanged: only crude nitrate solution + charred planks.

- kubejs-removal: every KubeJS script (time system, spawning, guides, HUD, survival mechanics, structures, recipes) moved into seven Winterheart mods (`winterheart`, `winterheart_survival`, `winterheart_guides`, `winterheart_time`, `winterheart_hud`, `winterheart_spawning`, `winterheart_structures`) and Paxi datapacks. Behaviour is the same, and existing worlds carry over (items, unlocked guides, day counter, placed structures are converted or imported). Tuning that was in scripts is now JSON, re-read on `/reload`. KubeJS, Rhino, MoreJS, ProbeJS and SanityJS are removed from `mods/`.

### Fixes:

- proximity-effects: the foliage rule never fired (tag typo) and set sanity from the temperature; it now gives a small chance of +2 sanity near plants.
- time-system: sleeping with several players online now passes one day, not one per player.
