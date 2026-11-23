# Feature map

Every file and folder in this repo that is **custom to Winterheart**: written or edited by the team, rather than shipped unchanged by a mod. Third-party mod jars and the insides of resource packs are left out on purpose.

Each feature name below (kebab-case) is the name to use for its doc (`docs/features/<feature-name>.md`), its CHANGELOG entries and its commit prefix. See [AGENTS.md](../AGENTS.md).

Paths are repo-relative. `DP/` = `config/paxi/datapacks/`, `SS/` = `kubejs/server_scripts/`, `ST/` = `kubejs/startup_scripts/`, `CS/` = `kubejs/client_scripts/`. For what each pack and data folder *does* and its file format, see [data-map.md](data-map.md).

---

## Our own mods

| Feature | Source in repo | Jar | Hooked from |
| --- | --- | --- | --- |
| seppuku | `mod-src/seppuku/` (Java, Gradle, own README/CHANGELOG; animation converter `tools/convert_epicfight.py`). Plays the bad-ending seppuku animation via PlayerAnimator; `/seppuku play\|stop`, `SeppukuApi`. | `mods/seppuku-1.0.0.jar` | `SS/mechanics/sanity_seppuku.js` |
| winterheart-core | `mod-src/winterheart/` (Java, Gradle, own README/CHANGELOG; `python/core/` generates its item models and lang and draws the metal part textures). Items, `winterheart:limb_poising`, recipe type `winterheart:shapeless_with_remainders`, brewing changes, `kubejs:` ID remap. Other mods and datapacks build on it. | `mods/winterheart-1.0.0.jar` | none (loaded by Forge) |
| winterheart-survival | `mod-src/winterheart_survival/` (Java, Gradle). Sanity despawn, seppuku trigger, beacon base; limb poisoning and proximity effects follow. Depends on winterheart-core. | `mods/winterheart_survival-1.0.0.jar` | none (loaded by Forge) |
| winterheart-guides | `mod-src/winterheart_guides/` (Java, Gradle; JEI plugin). Depends on winterheart-core. | `mods/winterheart_guides-1.0.0.jar` | none (loaded by Forge) |
| winterheart-time | `mod-src/winterheart_time/` (Java, Gradle). Depends on winterheart-core and winterheart-guides. | `mods/winterheart_time-1.0.0.jar` | none (loaded by Forge) |
| winterheart-mod-template | `mod-src/_template/` (placeholders + `new-mod.sh`; scaffolds a mod, `--dev` for a dev-only one). Not a mod itself, no jar. | none | none |
| mod-publishing | `publish-mods.sh` (builds and uploads every `mod-src/` mod with a `curseforge_project_id`). Not a mod, no jar. | none | `curseforge_project_id` in `mod-src/<mod>/gradle.properties` |

---

## Mechanics (KubeJS)

| Feature | Path | What it does |
| --- | --- | --- |
| time-system | `mod-src/winterheart_time/` (Java), `DP/winterheart_time/data/winterheart/time/config.json` | Custom day counter, sleep-to-pass-day with wake-up effects, eternal night from day 50, the death loop. Tuning in `config.json`. Frozen by `/toggle_test` (test-utils). |
| mob-spawning | `SS/mechanics/spawning.js` | Scripted mob spawner: mob templates with hitboxes, spawn pools, scripted-only mobs (wendigo). Blocked by `/toggle_test`. |
| sanity | `mod-src/winterheart_survival/.../sanity/SanityDespawn.java`, `mod-src/winterheart/.../api/{Sanity,Temperature,Cmd,Titles,Scheduler,Rand}.java`, old JS helpers in `SS/utils/helper_functions.js`, `config/sanitydim/` | Despawns sanity-dimension stalkers once every player is above the threshold. Sanity is 0–100 in scripts, 0–1 in the mod. |
| seppuku | `mod-src/winterheart_survival/.../sanity/SeppukuTrigger.java` | Plays the seppuku animation (Seppuku mod) once when a player's sanity drops to the threshold. |
| limb-poisoning | `SS/mechanics/limb_poisoning.js`; the effect itself is in winterheart-core (`mod-src/winterheart/.../effect/ModEffects.java`, `.../textures/mob_effect/limb_poising.png`) | Damaged LSO limbs drift in health (biased random walk) until treated; status effect shows it. Paused by `legendarysurvivaloverhaul:recovery`. |
| proximity-effects | `SS/mechanics/proximity.js`, `DP/winterheart_survival/data/winterheart/tags/blocks/`, `DP/winterheart_survival/data/butchery/tags/blocks/skeletons.json` | Scans blocks around players and shows titles/effects for tagged blocks (`#winterheart:gore`, …). |
| beacon-base | `mod-src/winterheart_survival/.../world/BeaconBase.java` | Right-clicking a beacon turns the 3x3 below it into iron blocks. |
| survival-hud | `SS/mechanics/custom_sidebars.js`, `CS/survival_hud.js`, `ST/survival_hud_render.js`, `kubejs/assets/winterheart/textures/gui/`, `python/survival_hud/` | Top-right survival panel. Server builds lines and sends a `winterheart_hud` packet; client stores them in `global.winterheartHud`; startup script draws the snowy frame. `python/survival_hud/` renders previews and generates the border textures offline. |
| test-utils | `mod-src/winterheart/.../api/{TestMode,TestItems}.java` | `/toggle_test` (freeze time at noon, block spawns) and right-click diagnostics items. |
| (shared) | `mod-src/winterheart/.../api/` (`Sanity`, `Temperature`, `Cmd`, `Titles`, `Scheduler`, `Rand`, `Ticks`, `Numbers`, `CapBackup`), `SS/utils/helper_functions.js` (old JS helpers still used by scripts not yet ported) | Helpers used by several features. |

## Guides and quests

| Feature | Path | What it does |
| --- | --- | --- |
| guides | `mod-src/winterheart_guides/` (Java), `DP/winterheart_guides/data/winterheart/guides/guides.json`, `config/paxi/resourcepacks/winterheart_assets/` (book models), `tools/guide_editor/`; the items are in winterheart-core | Trigger-based guide pop-up books and lost journal pages. `guides.json` is edited with the Rust guide editor and hot-reloaded. Full reference: [features/guides.md](features/guides.md). |
| quests | `SS/questing/quests.js` | Placeholder (empty). |

## Recipes and items

| Feature | Path | What it does |
| --- | --- | --- |
| recipes | `DP/winterheart_recipes/` (generated, except gunsmith recipes), `python/recipes/gen_recipes.py`, and the items, textures, brewing, leaching recipe type and bottle pop-out from winterheart-core (`mod-src/winterheart/`, `python/core/`) | Recipe overrides and the custom item chains: metal parts (gears, plates, rods…), saltpeter leaching → gunpowder, early potions, moss → sponge. |
| tacz-guns | `python/tacz/gen_gunsmith_recipes.py` → `DP/winterheart_recipes/data/winterheart/recipes/tacz_workbench/` + `DP/winterheart_recipes/data/tacz/recipes/gun_smith_table.json`, `tacz/` (`tacz-pre.toml`, `tacz_default_gun/`, `.export-state.json`), `python/tacz/purge_{guns,attachments,ammo}.py`, `config/tacz-*.toml` | TACZ gun pack trimmed to pre-1950s weapons (by the purge scripts) and their gunsmith recipes. |

## World

| Feature | Path | What it does |
| --- | --- | --- |
| worldgen | `DP/winterheart_worldgen/` (structure sets, biome modifiers, biome tags, `data/winterheart/worldgen/biome/spawn_clearing.json`), `DP/winterheart_novoatlas/`, `kubejs/assets/streamsreflowing/lang/` | Structure set overrides (vanilla, Philips Ruins, Gore Edition), mod worldgen tweaks, biome tags making the world cold/snowy, the `winterheart:spawn_clearing` biome, and the painted NovoAtlas overworld. |
| structures | `SS/worldgen/fixed_structures.js`, `SS/worldgen/fixed_structures.json`, `DP/winterheart_structures/` (templates in `data/winterheart/structures/`, `.mcfunction`s), `python/structures/` (`export_structures.sh`, `nbt_to_structure.py`, `schem_to_structure.py`, `structure_fixups.py`, `gen_beacon.py`, `gen_wreck.py`), `config/worldedit/schematics/{watchtower.nbt,shipwreck.schem,shipwreck_chest.snbt}`, `config/starterstructure.json5`, `config/starterstructure/` | Hand-built structures placed once per world at fixed spots (watchtower, shipwreck, beacon) with `/place template`, plus the spawn starter structure. Templates are exported from WorldEdit saves. |
| world-painter | `tools/worldpainter/` | Rust planner for the escape-south route: paint biomes/heights, place structures; writes `DP/winterheart_novoatlas/` and `SS/worldgen/fixed_structures.json`. |
| survival-data | `DP/winterheart_survival/` (`data/minecraft/legendarysurvivaloverhaul/`, `data/immersive_weathering/loot_tables/blocks/*permafrost.json`), `config/legendarysurvivaloverhaul/` | Legendary Survival Overhaul temperature/thirst values for biomes, blocks, items, consumables and fuel; permafrost drops. |

## Configs and tooling

| Feature | Path | What it does |
| --- | --- | --- |
| mod-configs | `config/`, `defaultconfigs/`, `config/paxi/*_load_order.json`, `config/starterkit/`, `config/fancymenu/` | Mod settings, starter kit, menus, Paxi load order. |
| run-pack | `run-pack.sh`, `python/run_pack/` | Launches the instance from the shell (offline): `--headless` smoke tests (AGENTS.md rule 7), `--server` dedicated server in `server/` (gitignored; client-only mods left out via `python/run_pack/server_exclude.txt`), `--send`/`--console`/`--run` server RCON. |
| agent-commands | `.claude/commands/`, `.claude/settings.json`, `AGENTS.md`, `CLAUDE.md` | Contributor rules and Claude Code slash commands (`/resolve`). |

## Research notes

- [audits/minezero-vanilla-death.md](audits/minezero-vanilla-death.md): how to bypass MineZero's death interception (untested).
