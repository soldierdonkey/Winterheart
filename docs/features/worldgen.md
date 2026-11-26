# worldgen

Shapes the Winterheart overworld. The painted NovoAtlas map replaces the overworld generator. Almost every vanilla and mod structure set is switched off, Butchery's sulfur ore is removed, and the spawn pad gets its own `winterheart:spawn_clearing` biome, tagged so it behaves like Snowy Plains.

## Files

- `datapacks/winterheart_worldgen/`: datapack with everything below except the NovoAtlas map.
  - `data/{minecraft,philipsruins,gore_edition,antlers,butchery}/worldgen/structure_set/*.json`: 47 structure sets, all with `"structures": []`, which turns them off.
  - `data/butchery/forge/biome_modifier/{deepslate_sulfur_ore,sulfuroregeneration}_biome_modifier.json`: `forge:none`, which removes Butchery's sulfur ore biome modifiers.
  - `data/winterheart/worldgen/biome/spawn_clearing.json`: the `winterheart:spawn_clearing` biome (a copy of Snowy Plains, temperature 0).
  - `data/{c,forge}/tags/worldgen/biome/{is_cold,is_cold/overworld,is_plains,is_snowy}.json`, `data/minecraft/tags/worldgen/biome/{is_overworld,spawns_snow_foxes,spawns_white_rabbits,spawns_cold_variant_frogs,has_structure/mineshaft}.json`: add `winterheart:spawn_clearing` (`"replace": false`) so mods treat it as a cold snowy plains biome.
  - `data/streamsreflowing/tags/worldgen/biome/no_streams.json`: Streams Reflowing carves no stream through the spawn pad.
- `datapacks/winterheart_novoatlas/`: the painted overworld. It holds `data/minecraft/dimension/overworld.json` (NovoAtlas `image_map` generator) and `data/winterheart/novoatlas/{heightmap,biome_map}/world.png` plus `map_info/world.json`. Written by the World Painter (`tools/worldpainter`, File > Export to modpack).
- `resourcepacks/winterheart_assets/assets/streamsreflowing/lang/en_us.json`: Streams Reflowing lang override.

## How it works

packfinder loads both packs globally. The dimension, biome and structure sets are worldgen registries, read when a world is created or loaded. Changes to the structure sets and biome modifiers affect only chunks generated afterwards. Changes to the NovoAtlas map and `overworld.json` affect only new worlds.

The spawn pad biome is painted by the World Painter around (0, 0). Its tags keep spawning (snow foxes, white rabbits, cold frogs), "is cold/snowy" checks in mods, and mineshafts working there, while `no_streams` keeps rivers out of spawn.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| structure set list | `datapacks/winterheart_worldgen/data/*/worldgen/structure_set/` | 47 sets off | Delete a file to turn that set back on |
| map, heights, biomes | World Painter project (`tools/worldpainter/worldpainter_project.json`, gitignored) | — | Re-export to rewrite `winterheart_novoatlas` |
| region weights etc. | `config/` (see mod-configs) | — | Other worldgen settings live in mod configs |

## Changes

- 2026-11-22: Moved the structure sets, biome modifiers, biome tags and the `spawn_clearing` biome from `kubejs/data/` into the new Paxi pack `winterheart_worldgen`. File paths inside `data/` are unchanged. KubeJS's built-in pack used to load above all others; Paxi packs load above mod jars, which is enough for these overrides, and no other pack in the instance touches the same paths. Headless test: see [structures](structures.md#changes), which has the same run.

## Gotchas

- NovoAtlas 1.1.1 keeps each map in the heap as an `int[][]`, 4 bytes per pixel: about 134 MB for the two 4096x4096 maps. Reloading data briefly needs more.
- The NovoAtlas `overworld.json` replaces the whole overworld. Don't add a second `data/minecraft/dimension/overworld.json` in another pack. The World Painter's installer also moves a legacy `kubejs/data/minecraft/dimension/overworld.json` out of the way if one exists.
- Structure set overrides replace the whole file. To turn a set back on, delete our file instead of editing it.
