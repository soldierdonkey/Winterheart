# survival-data

Legendary Survival Overhaul (LSO) values for Winterheart's cold-survival balance: how warm biomes, blocks, worn armour, held items, food and fuel are, and how much thirst water and food restore. It also changes permafrost drops.

## Files

- `datapacks/winterheart_survival/`: datapack.
  - `data/minecraft/legendarysurvivaloverhaul/temperature/biomes/{frozen_ocean,deep_frozen_ocean}.json`: biome temperatures.
  - `data/minecraft/legendarysurvivaloverhaul/temperature/blocks/*.json`: heat from fires, torches, furnaces, lava, magma, jack-o'-lanterns (by block state, e.g. campfire `lit=true` → 10).
  - `data/minecraft/legendarysurvivaloverhaul/temperature/items/*.json`: armour (leather/iron/gold/diamond) and held items (ice, torches, lava bucket…).
  - `data/minecraft/legendarysurvivaloverhaul/temperature/consumables/*.json`, `…/fuel_items/*.json`: food temperature effects and heater fuel values.
  - `data/minecraft/legendarysurvivaloverhaul/thirst/{blocks,consumables}/*.json`: hydration and saturation from water, rain, cauldrons and food (water source blocks: 3 hydration, 75% chance of `legendarysurvivaloverhaul:thirst`).
  - `data/immersive_weathering/loot_tables/blocks/{permafrost,grassy_permafrost}.json`: overrides Immersive Weathering's permafrost loot tables: the block itself with the right tool (silk touch), otherwise a chance of bones (25%), gold nuggets and other finds.
- `config/legendarysurvivaloverhaul/legendarysurvivaloverhaul-{common,client}.toml`: LSO settings.
- `mod-src/winterheart/.../api/Temperature.java`: temperature getters and setters used by the Winterheart mods (0–40 range).

## How it works

LSO reads `data/<namespace>/legendarysurvivaloverhaul/<temperature|thirst>/<kind>/<id>.json` from every datapack. The file name is the block/item/biome path, and the namespace is the block's or item's namespace (here everything is `minecraft`). Each file is a list of entries, optionally keyed by block-state `properties`. The loot tables replace Immersive Weathering's own files at the same path.

Everything applies on `/reload`.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| per-entry `temperature` | `…/temperature/<kind>/<id>.json` | per file | Warmth added by that block/item/biome |
| per-entry `hydration`, `saturation`, `effects` | `…/thirst/<kind>/<id>.json` | per file | Thirst restored and side effects |

## Changes

- 2026-11-22: Moved from `kubejs/data/` into the new Paxi pack `winterheart_survival`. File paths inside `data/` are unchanged. Headless test: see [structures](structures.md#changes), which has the same run. Values were not checked in game.

## Gotchas

- LSO data files are not tags. If two packs ship the same `<kind>/<id>.json`, the higher one wins.
