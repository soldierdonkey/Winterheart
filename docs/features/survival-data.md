# survival-data

Legendary Survival Overhaul (LSO) armour temperatures for Winterheart's cold-survival balance. It also changes permafrost drops.

## Files

- `datapacks/winterheart_survival/`: datapack.
  - `data/minecraft/legendarysurvivaloverhaul/temperature/items/*.json`: armour temperatures (leather chestplate and boots, iron, golden, diamond).
  - `data/immersive_weathering/loot_tables/blocks/{permafrost,grassy_permafrost}.json`: overrides Immersive Weathering's permafrost loot tables: the block itself with the right tool (silk touch), otherwise a chance of bones (25%), gold nuggets and other finds.
- `config/legendarysurvivaloverhaul/legendarysurvivaloverhaul-{common,client}.toml`: LSO settings.
- `mod-src/winterheart/.../api/Temperature.java`: temperature getters and setters used by the Winterheart mods (0–40 range).

## How it works

LSO reads `data/<namespace>/legendarysurvivaloverhaul/<temperature|thirst>/<kind>/<id>.json` from every datapack. The file name is the block/item/biome path, and the namespace is the block's or item's namespace (here everything is `minecraft`). Each file is a list of entries, optionally keyed by block-state `properties`. The pack only holds files that differ from LSO's own; every other value (biomes, blocks, held items, fuel, food, thirst) comes from the LSO jar. The loot tables replace Immersive Weathering's own files at the same path.

Everything applies on `/reload`.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| per-entry `temperature` | `…/temperature/items/<id>.json` | per file | Warmth added by that armour piece |

## Changes

- 2026-11-26: Removed the 50 files whose content matched LSO's own file at the same path, with no other mod or datapack providing that path, so the values are unchanged. Headless test (`--world "New World"`, then `/reload`): joined 27 s after the wait began, `winterheart_survival` enabled, and a search of the log found no LSO data errors. Values were not read back in game.
- 2026-11-22: Moved from `kubejs/data/` into the new Paxi pack `winterheart_survival`. File paths inside `data/` are unchanged. Headless test: see [structures](structures.md#changes), which has the same run. Values were not checked in game.

## Gotchas

- LSO data files are not tags. If two packs ship the same `<kind>/<id>.json`, the higher one wins.
