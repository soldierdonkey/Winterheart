# general-kubejs-removal

Remove KubeJS from the pack entirely. Data-only scripts (recipes, tags) become Paxi datapacks. Everything with logic or registrations moves into seven Winterheart Forge mods in `mod-src/`. Script hot-reload is given up for logic. Tuning values move to datapack JSON read by the mods through reload listeners, so `/reload` still applies them.

- **Feature:** spans several (`recipes`, `tacz-guns`, `proximity-effects`, `time-system`, `mob-spawning`, `sanity`, `seppuku`, `limb-poisoning`, `beacon-base`, `survival-hud`, `test-utils`, `guides`, `structures`)
- **Added:** 2026-11-22
- **Progress:** 0/9 (keep in sync with the row in [../TODO.md](../TODO.md))

## Target layout

`DP/` = `config/paxi/datapacks/`, `SS/` = `kubejs/server_scripts/`, `ST/` = `kubejs/startup_scripts/`, `CS/` = `kubejs/client_scripts/`.

### Datapacks

| Pack | Takes over |
| --- | --- |
| `DP/winterheart_recipes/` (new) | `SS/recipes/*.js` recipes, plus the item tags in `metal_parts.js`, `potions.js` and `recipe_fixes.js`. Repetitive recipes are generated: `python/recipes/gen_recipes.py` (equipment, metal parts) and `python/tacz/gen_gunsmith_recipes.py` (TACZ gunsmith). |
| `DP/winterheart_survival/` | `SS/tags/proximity_tags.js` (block tags). Later: the proximity rule JSON. |
| `DP/winterheart_structures/` | `SS/worldgen/fixed_structures.json` (step 8). `tools/worldpainter` must write the new path. |

KubeJS `event.remove(...)` becomes a recipe override at the same ID with `"conditions": [{"type": "forge:false"}]`. The matched IDs were found by scanning every jar in `mods/` plus the vanilla jar (see Notes).

### Mods

| Mod | Takes over |
| --- | --- |
| `winterheart` (core) | Every registration: metal parts, saltpeter chain, `guide_book`, `lost_page`, `winterheart:limb_poising`. Assets from `kubejs/assets/winterheart/`. Shared API replacing `SS/utils/helper_functions.js` and `number_counting.js` (sanity and temperature via the sanitydim and LSO capabilities, titles, numbers to words). test-utils (`/toggle_test`, diagnostic items). Brewing changes (replaces MoreJS `ST/recipes/potions.js` and `ST/items/saltpeter.js`). Saltpeter leaching recipe (needs a remainder-replacing recipe type) and the bottle pop-out on smelt. `MissingMappingsEvent` remap of `kubejs:*` IDs. |
| `winterheart_time` | `SS/mechanics/time_system.js` |
| `winterheart_survival` | `limb_poisoning.js`, `proximity.js`, `sanity_despawn.js`, `sanity_seppuku.js` (calls `SeppukuApi` directly), `beacon_base.js` |
| `winterheart_spawning` | `SS/mechanics/spawning.js`. Mob templates and pools become JSON. |
| `winterheart_hud` | `custom_sidebars.js`, `CS/survival_hud.js`, `ST/survival_hud_render.js`, GUI textures. A real packet replaces `winterheart_hud` / `global.winterheartHud`. |
| `winterheart_guides` | `SS/questing/guides/*`, `CS/guide_book_jei.js`, `CS/guide_book_watcher.js`. JEI plugin, optional ServerPause. `guides.json` moves to a datapack (`tools/guide_editor` must follow). Java API replaces `global.Guides`. |
| `winterheart_structures` | `SS/worldgen/fixed_structures.js` |

Every feature mod depends on `winterheart` (core). `winterheart_time` and `winterheart_survival` also depend on `winterheart_guides`.

## Save compatibility

1. **Item IDs.** `kubejs:saltpeter`, `kubejs:crude_nitrate_bottle`, `kubejs:potassium_nitrate_bottle`, `kubejs:guide_book` and `kubejs:lost_page` become `winterheart:*`, remapped by the core mod. Also update the references in `config/starterkit/kits/*.txt`, `config/worldedit/schematics/shipwreck_chest.snbt`, `guides.json`, `config/jei_plus_plus/recipe_defaults.json` and `tools/worldpainter/src/{main,install}.rs`. Metal parts and the mob effect already use `winterheart:`.
2. **Player state** (`player.persistentData`). Should be Forge's entity persistent data. The Java ports must read the same NBT keys. **To verify.**
3. **World state** (`server.persistentData`: day counter, structure-placed flags, spawn state). KubeJS keeps this in its own saved data. Each mod needs a one-time import, or old worlds reset. **To verify.**

## Steps

Each step is one or more feature-prefixed commits. Each one deletes the scripts it replaces, so old and new code never run together, and is followed by the headless smoke test (AGENTS.md rule 7).

- [ ] 1. Recipes and tags to datapacks (`winterheart_recipes`, proximity tags into `winterheart_survival`)
- [ ] 2. Core mod `winterheart`: registrations, ID remap, shared API, test-utils, brewing, saltpeter leaching + pop-out. Test the remaps on an existing save.
- [ ] 3. `winterheart_survival` part 1: beacon-base, sanity despawn, seppuku hook
- [ ] 4. `winterheart_time`, including the world-state import
- [ ] 5. `winterheart_hud`, then proximity-effects and limb-poisoning into `winterheart_survival`
- [ ] 6. `winterheart_spawning`
- [ ] 7. `winterheart_guides` (+ guide editor path)
- [ ] 8. `winterheart_structures` (+ world-painter output path)
- [ ] 9. Remove `kubejs`, `rhino`, `morejs`, `probejs` jars and `kubejs/`. Check what else needs `architectury` first. Update feature map, data map, AGENTS.md "where things go".

## Files

- `kubejs/` (all of it, by the end)
- `config/paxi/datapacks/winterheart_recipes/`
- `config/paxi/datapacks/winterheart_survival/`
- `config/paxi/datapacks/winterheart_structures/`
- `python/recipes/`, `python/tacz/`
- `mod-src/winterheart/`, `mod-src/winterheart_time/`, `mod-src/winterheart_survival/`, `mod-src/winterheart_spawning/`, `mod-src/winterheart_hud/`, `mod-src/winterheart_guides/`, `mod-src/winterheart_structures/`
- `tools/guide_editor/`, `tools/worldpainter/`
- `docs/feature-map.md`, `docs/data-map.md`, `docs/README.md`

## Notes

- 2026-11-22: Decisions: remove KubeJS entirely; seven mods as in the table; hot-reload only for JSON tuning.
- 2026-11-22: Recipe IDs matched by the old `event.remove` filters (scanned across `mods/*.jar` + vanilla 1.20.1): vanilla armor and tools for leather/iron/golden/diamond armor and wooden/stone/iron/golden/diamond tools (no vanilla chainmail recipes exist); `gore_edition:exarrack_can_craft_stone_{axe,axe_two,hoe,hoe_two,pickaxe,shovel}`; `tacz:gunpowder`; `tacz:gun_smith_table`; `minecraft:brewing_stand`; `immersive_weathering:green_dye_from_moss_clump`.
- 2026-11-22: `potions.js` removed `minecraft:blaze_rod` from `#minecraft:brewing_stand_fuel`. That was always a no-op: the tag comes from `betterbrewingfuels` and only holds `minecraft:blaze_powder`. The datapack only adds coal and charcoal.
- 2026-11-22: KubeJS `.damageIngredient('#forge:hammer')` maps to NoTreePunching's `notreepunching:tool_damaging_shaped` / `tool_damaging_shapeless` recipe types. The saltpeter leaching recipe's `.replaceIngredient(rich_soil -> dirt)` has no datapack equivalent, so it stays in KubeJS until the core mod adds a recipe type for it (step 2).
