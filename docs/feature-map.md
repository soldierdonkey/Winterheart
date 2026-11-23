# Feature map

Every file and folder in this repo that is **custom to Winterheart**: written or edited by the team, rather than shipped unchanged by a mod. Third-party mod jars and the insides of resource packs are left out on purpose.

Each feature name below (kebab-case) is the name to use for its doc (`docs/features/<feature-name>.md`), its CHANGELOG entries and its commit prefix. See [AGENTS.md](../AGENTS.md).

Paths are repo-relative. `DP/` = `config/paxi/datapacks/`, `SS/` = `kubejs/server_scripts/`, `ST/` = `kubejs/startup_scripts/`, `CS/` = `kubejs/client_scripts/`. For what each pack and data folder *does* and its file format, see [data-map.md](data-map.md).

---

## Our own mods

| Feature | Source in repo | Jar | Hooked from |
| --- | --- | --- | --- |
| seppuku | `mod-src/seppuku/` (Java, Gradle, own README/CHANGELOG; animation converter `tools/convert_epicfight.py`). Plays the bad-ending seppuku animation via PlayerAnimator; `/seppuku play\|stop`, `SeppukuApi`. | `mods/seppuku-1.0.0.jar` | `SS/mechanics/sanity_seppuku.js` |
| winterheart-mod-template | `mod-src/_template/` (placeholders + `new-mod.sh`; scaffolds a mod, `--dev` for a dev-only one). Not a mod itself, no jar. | none | none |
| mod-publishing | `publish-mods.sh` (builds and uploads every `mod-src/` mod with a `curseforge_project_id`). Not a mod, no jar. | none | `curseforge_project_id` in `mod-src/<mod>/gradle.properties` |

---

## Mechanics (KubeJS)

| Feature | Path | What it does |
| --- | --- | --- |
| time-system | `SS/mechanics/time_system.js` | Custom day counter, sleep-to-pass-day with wake-up effects, eternal night from day 50 (`TIME_CONFIG`). Frozen by `/toggle_test` (test-utils). |
| mob-spawning | `SS/mechanics/spawning.js` | Scripted mob spawner: mob templates with hitboxes, spawn pools, scripted-only mobs (wendigo). Blocked by `/toggle_test`. |
| sanity | `SS/mechanics/sanity_despawn.js`, sanity helpers in `SS/utils/helper_functions.js`, `config/sanitydim/` | Despawns sanity-dimension stalkers once every player is above the threshold. Sanity is 0–100 in scripts, 0–1 in the mod. |
| seppuku | `SS/mechanics/sanity_seppuku.js` | Plays the seppuku animation (Seppuku mod) once when a player's sanity drops to the threshold. |
| limb-poisoning | `SS/mechanics/limb_poisoning.js`, `ST/effects/limb_poising.js`, `kubejs/assets/winterheart/textures/mob_effect/limb_poising.png` | Damaged LSO limbs drift in health (biased random walk) until treated; status effect shows it. Paused by `legendarysurvivaloverhaul:recovery`. |
| proximity-effects | `SS/mechanics/proximity.js`, `SS/tags/proximity_tags.js` | Scans blocks around players and shows titles/effects for tagged blocks (`#winterheart:gore`, …). |
| beacon-base | `SS/mechanics/beacon_base.js` | Right-clicking a beacon turns the 3x3 below it into iron blocks. |
| survival-hud | `SS/mechanics/custom_sidebars.js`, `CS/survival_hud.js`, `ST/survival_hud_render.js`, `kubejs/assets/winterheart/textures/gui/`, `kubejs/tools/hud/` | Top-right survival panel. Server builds lines and sends a `winterheart_hud` packet; client stores them in `global.winterheartHud`; startup script draws the snowy frame. `kubejs/tools/hud/` renders previews and generates the border textures offline. |
| test-utils | `SS/utils/test_mode.js`, `SS/utils/tests.js` | `/toggle_test` (freeze time at noon, block spawns) and right-click diagnostics items. |
| (shared) | `SS/utils/helper_functions.js`, `SS/utils/number_counting.js` | Helpers used by several features (sanity/temperature getters and setters, titles, numbers to words). |

## Guides and quests

| Feature | Path | What it does |
| --- | --- | --- |
| guides | `SS/questing/guides/` (`guides.json`, `guides_engine.js`, `guides_commands.js`, `README.md`), `CS/guide_book_jei.js`, `CS/guide_book_watcher.js`, `ST/items/guide_book.js`, `ST/items/lost_page.js`, `kubejs/assets/kubejs/models/item/guide_book*.json`, `tools/guide_editor/` | Trigger-based guide pop-up books and lost journal pages. `guides.json` is edited with the Rust guide editor and hot-reloaded. Full reference: `SS/questing/guides/README.md`. |
| quests | `SS/questing/quests.js` | Placeholder (empty). |

## Recipes and items

| Feature | Path | What it does |
| --- | --- | --- |
| recipes | `SS/recipes/` (`equipment`, `gunpowder`, `metal_parts`, `potions`, `recipe_fixes`, `saltpeter`, `sponge`), `ST/items/{metal_parts,saltpeter}.js`, `ST/recipes/potions.js`, `kubejs/assets/winterheart/textures/item/` (+ `metal_parts.py`), `kubejs/assets/winterheart/lang/en_us.json` | Recipe overrides and the custom item chains: metal parts (gears, plates, rods…), saltpeter leaching → gunpowder, early potions, moss → sponge. |
| tacz-guns | `SS/recipes/guns.js`, `tacz/` (`tacz-pre.toml`, `tacz_default_gun/`, `.export-state.json`), `config/tacz-*.toml` | TACZ gun pack trimmed to pre-1950s weapons (`tacz_default_gun/remove*.py`) and their crafting recipes. |

## World

| Feature | Path | What it does |
| --- | --- | --- |
| worldgen | `kubejs/data/*/worldgen/`, `kubejs/data/*/tags/worldgen/biome/`, `kubejs/data/butchery/forge/biome_modifier/`, `kubejs/data/winterheart/worldgen/biome/spawn_clearing.json`, `DP/winterheart_novoatlas/`, `kubejs/assets/streamsreflowing/lang/` | Structure set overrides (vanilla, Philips Ruins, Gore Edition), mod worldgen tweaks, biome tags making the world cold/snowy, the `winterheart:spawn_clearing` biome, and the painted NovoAtlas overworld. |
| structures | `SS/worldgen/fixed_structures.js`, `SS/worldgen/fixed_structures.json`, `kubejs/data/winterheart/structures/`, `DP/winterheart_structures/`, `tools/export_structures.sh`, `tools/{nbt_to_structure,schem_to_structure,structure_fixups}.py`, `config/worldedit/schematics/{watchtower.nbt,shipwreck.schem,shipwreck_chest.snbt}`, `config/starterstructure.json5`, `config/starterstructure/` | Hand-built structures placed once per world at fixed spots (watchtower, shipwreck, beacon) with `/place template`, plus the spawn starter structure. Templates are exported from WorldEdit saves. |
| world-painter | `tools/worldpainter/` | Rust planner for the escape-south route: paint biomes/heights, place structures; writes `DP/winterheart_novoatlas/` and `SS/worldgen/fixed_structures.json`. |
| survival-data | `kubejs/data/minecraft/legendarysurvivaloverhaul/`, `kubejs/data/immersive_weathering/loot_tables/blocks/*permafrost.json`, `config/legendarysurvivaloverhaul/` | Legendary Survival Overhaul temperature/thirst values for biomes, blocks, items, consumables and fuel; permafrost drops. |

## Configs and tooling

| Feature | Path | What it does |
| --- | --- | --- |
| mod-configs | `config/`, `defaultconfigs/`, `config/paxi/*_load_order.json`, `config/starterkit/`, `config/fancymenu/` | Mod settings, starter kit, menus, Paxi load order. |
| agent-commands | `.claude/commands/`, `.claude/settings.json`, `AGENTS.md`, `CLAUDE.md` | Contributor rules and Claude Code slash commands (`/resolve`). |

## Research notes

- [audits/minezero-vanilla-death.md](audits/minezero-vanilla-death.md): how to bypass MineZero's death interception (untested).
