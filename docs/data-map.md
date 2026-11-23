# Data map

What each body of Winterheart data **does** and what **format** it is in: file types, whether it adds new content or overrides a mod's own files. For *where* files live and which feature owns them, see [feature-map.md](feature-map.md). This page doesn't repeat that.

`DP/` = `config/paxi/datapacks/`. All custom datapack data lives in Paxi packs. `kubejs/data/` is not used.

---

## Reading this page

**New vs override.** A datapack file either *adds* something (a new ID, usually in the `winterheart` namespace) or *overrides* a file a mod or vanilla already ships (same `data/<namespace>/<path>`). An override replaces the whole file. It does not merge with it.

**Common patterns:**

| Pattern | What it does |
| --- | --- |
| Same path as a mod/vanilla file | Replaces that file outright. |
| `structure_set` with `"structures": []` | Turns the structure set off: nothing in it generates. |
| Tag with `"replace": false` | Merges our entries into the existing tag. |
| `.nbt` | Structure template (built blocks), not JSON. |
| `.mcfunction` | Command list, run by a script or `/function`. |

---

## Overview

| Data | Location | Kind of data | Mostly | Applies on |
| --- | --- | --- | --- | --- |
| Structure sets | `DP/winterheart_worldgen/data/{minecraft,philipsruins,gore_edition,antlers,butchery}/worldgen/structure_set/` | 47 structure sets | override (all disable) | new chunks |
| Biome modifiers | `DP/winterheart_worldgen/data/butchery/forge/biome_modifier/` | `forge:none` (removes Butchery's sulfur ore) | override | world load |
| Biome tags | `DP/winterheart_worldgen/data/{c,forge,minecraft,streamsreflowing}/tags/worldgen/biome/` | Biome tags adding `winterheart:spawn_clearing` (`is_cold`, `is_snowy`, `is_plains`, spawn tags, `no_streams`) | merge | world load |
| Custom biome | `DP/winterheart_worldgen/data/winterheart/worldgen/biome/spawn_clearing.json` | Biome definition `winterheart:spawn_clearing` | new | world creation |
| Painted overworld | `DP/winterheart_novoatlas/` | `minecraft:dimension/overworld.json` + NovoAtlas biome map / heightmap PNGs and `map_info` | override | world creation |
| Structure templates | `DP/winterheart_structures/data/winterheart/structures/` | `.nbt` templates + `<name>.fixups.json` (block states restored after placing) | new | `/reload` |
| Structure functions | `DP/winterheart_structures/data/winterheart/functions/structures/` | `.mcfunction` (beacon, iceberg shipwreck) | new | `/reload` |
| Survival values | `DP/winterheart_survival/data/minecraft/legendarysurvivaloverhaul/{temperature,thirst}/` | Legendary Survival Overhaul JSON (biomes, blocks, items, consumables, fuel) | new | `/reload` |
| Permafrost drops | `DP/winterheart_survival/data/immersive_weathering/loot_tables/blocks/` | Block loot tables | override | `/reload` |

## Load order

Paxi packs in `DP/` load globally, above mod jars. Their order is pinned only for packs listed in `config/paxi/datapack_load_order.json` (currently none). The four packs don't share any paths, so their order doesn't matter. KubeJS's own data folder (`kubejs/data/`) would load above all of them. Don't use it for new data (AGENTS.md).
