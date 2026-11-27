# Feature map

Every file and folder in this repo that is **custom to Winterheart**: written or edited by the team, rather than shipped unchanged by a mod. Third-party mod jars and the insides of resource packs are left out on purpose.

Each feature name below (kebab-case) is the name to use for its doc (`docs/features/<feature-name>.md`), its CHANGELOG entries and its commit prefix. See [AGENTS.md](../AGENTS.md).

Paths are repo-relative. `DP/` = `datapacks/`. KubeJS is gone: logic lives in the Winterheart mods (`mod-src/`), data in `datapacks/`. For what each pack and data folder *does* and its file format, see [data-map.md](data-map.md).

---

## Our own mods

| Feature | Source in repo | Jar | Hooked from |
| --- | --- | --- | --- |
| seppuku | `mod-src/seppuku/` (Java, Gradle, own README/CHANGELOG; animation converter `tools/convert_epicfight.py`). Plays the bad-ending seppuku animation via PlayerAnimator; `/seppuku play\|stop`, `SeppukuApi`. | `mods/seppuku-1.0.0.jar` | `mod-src/winterheart_survival/.../sanity/SeppukuTrigger.java` |
| winterheart-core | `mod-src/winterheart/` (Java, Gradle, own README/CHANGELOG; `python/core/` generates its item models and lang and draws the metal part textures). Items, `winterheart:limb_poising`, recipe type `winterheart:shapeless_with_remainders`, brewing changes, `kubejs:` ID remap, run-pack's command listener (`remote/`). Other mods and datapacks build on it. | `mods/winterheart-1.0.0.jar` | none (loaded by Forge) |
| winterheart-survival | `mod-src/winterheart_survival/` (Java, Gradle). Sanity despawn, seppuku trigger, beacon base, limb poisoning, proximity effects. Depends on winterheart-core, winterheart-guides and winterheart-hud. | `mods/winterheart_survival-1.0.0.jar` | none (loaded by Forge) |
| winterheart-guides | `mod-src/winterheart_guides/` (Java, Gradle; JEI plugin). Depends on winterheart-core. | `mods/winterheart_guides-1.0.0.jar` | none (loaded by Forge) |
| winterheart-time | `mod-src/winterheart_time/` (Java, Gradle). Depends on winterheart-core and winterheart-guides. | `mods/winterheart_time-1.0.0.jar` | none (loaded by Forge) |
| winterheart-hud | `mod-src/winterheart_hud/` (Java, Gradle). Depends on winterheart-core. | `mods/winterheart_hud-1.0.0.jar` | none (loaded by Forge) |
| winterheart-spawning | `mod-src/winterheart_spawning/` (Java, Gradle). Depends on winterheart-core. | `mods/winterheart_spawning-1.0.0.jar` | none (loaded by Forge) |
| winterheart-fishing | `mod-src/winterheart_fishing/` (Java, Gradle, mixins; see [fishing](features/fishing.md)) | `mods/winterheart_fishing-1.0.0.jar` | none (loaded by Forge) |
| winterheart-structures | `mod-src/winterheart_structures/` (Java, Gradle). Depends on winterheart-core. | `mods/winterheart_structures-1.0.0.jar` | none (loaded by Forge) |
| winterheart-butchery | `mod-src/winterheart_butchery/` (Java, Gradle; client-only JEI plugin, see [butchery-jei](features/butchery-jei.md)) | `mods/winterheart_butchery-1.0.0.jar` | none (loaded by Forge) |
| winterheart-mod-template | `mod-src/_template/` (placeholders + `new-mod.sh`; scaffolds a mod, `--dev` for a dev-only one). Not a mod itself, no jar. | none | none |
| mod-publishing | `publish.sh` (builds and uploads every `mod-src/` mod and in-house pack with a `curseforge_project_id`), `python/publish/` (zips packs), `pack.properties`, `README.md` and `CHANGELOG.md` in each `winterheart_*` pack. Not a mod, no jar. | none | `curseforge_project_id` in `mod-src/<mod>/gradle.properties` and `<pack>/pack.properties` |
| packfinder | `mod-src/packfinder/` (Java). Loads every pack in `resourcepacks/` and `datapacks/`; replaced Paxi. | `mods/packfinder-1.0.0.jar` | none (loaded by Forge); order in `config/packfinder/` |
| resource-packs | Third-party packs in `resourcepacks/`. Not a mod, no jar. | none | `config/packfinder/resourcepack_order.json` |
| spark | Third-party profiler for lag and memory. | `mods/spark-1.10.53-forge.jar` | `config/spark/config.json` |
| fetch | `fetch.sh`, `mods.json`, `tools/fetch/` (Rust), `THIRD_PARTY_NOTICES.md`. Fills `mods/` and `resourcepacks/` from the manifest. Not a mod, no jar. | none | `mods.json` |

---

## Mechanics

| Feature | Path | What it does |
| --- | --- | --- |
| time-system | `mod-src/winterheart_time/` (Java), `DP/winterheart_time/data/winterheart/time/config.json` | Custom day counter, sleep-to-pass-day with wake-up effects, eternal night from day 50, the death loop. Tuning in `config.json`. Frozen by `/toggle_test` (test-utils). |
| mob-spawning | `mod-src/winterheart_spawning/` (Java), `DP/winterheart_spawning/data/winterheart/spawning/config.json` | Scripted mob spawner: mob templates with hitboxes, spawn pools, scripted-only mobs (wendigo). Blocked by `/toggle_test`. |
| sanity | `mod-src/winterheart_survival/.../sanity/SanityDespawn.java`, `mod-src/winterheart/.../api/{Sanity,Temperature,Cmd,Titles,Scheduler,Rand}.java`, `config/sanitydim/` | Despawns sanity-dimension stalkers once every player is above the threshold. Sanity is 0–100 in Winterheart code, 0–1 in the mod. |
| seppuku | `mod-src/winterheart_survival/.../sanity/SeppukuTrigger.java` | Plays the seppuku animation (Seppuku mod) once when a player's sanity drops to the threshold. |
| limb-poisoning | `mod-src/winterheart_survival/.../limb/LimbPoising.java`, `DP/winterheart_survival/data/winterheart/limb_poising/config.json`; the effect itself is in winterheart-core (`mod-src/winterheart/.../effect/ModEffects.java`, `.../textures/mob_effect/limb_poising.png`) | Damaged LSO limbs drift in health (biased random walk) until treated; status effect and HUD section show it. Paused by `legendarysurvivaloverhaul:recovery`. |
| proximity-effects | `mod-src/winterheart_survival/.../proximity/Proximity.java`, `DP/winterheart_survival/data/winterheart/proximity/rules.json`, `DP/winterheart_survival/data/winterheart/tags/blocks/`, `DP/winterheart_survival/data/butchery/tags/blocks/skeletons.json` | Scans blocks around players and runs data-driven titles/effects for tagged blocks (`#winterheart:gore`, ...). |
| fishing | `mod-src/winterheart_fishing/`, `DP/winterheart_fishing/data/winterheart/fishing/config.json`; third-party `mods/fishnostuck-mc1.20.1-v1.0.3-forge.jar` | Rods catch the real fish in the water, with charged casts (port of Simple Fishing Overhaul). Fish No Stuck fixes frozen fish. |
| carcass-butchering | `docs/features/carcass-butchering.md` | Clean-room spec of how Butchery's carcasses are dropped, bled and cut up. Documentation only; no Winterheart code. |
| butchery-jei | `mod-src/winterheart_butchery/`, `python/butchery_jei/` | JEI pages showing how every carcass, corpse and iron golem is butchered, plus skin tanning, skull crushing and acid skeletons. |
| butchery-tool-models | `python/butchery_tools/`, `resourcepacks/winterheart_assets/assets/butchery/`, `resourcepacks/winterheart_assets/assets/winterheart/{models,textures}/item/butchery/` | 3D models for Butchery's tools, adapted from Tactical 3D Tools (CC-BY-4.0). |
| beacon-base | `mod-src/winterheart_survival/.../world/BeaconBase.java` | Right-clicking a beacon turns the 3x3 below it into iron blocks. |
| survival-hud | `mod-src/winterheart_hud/` (Java; code, textures in `.../assets/winterheart/textures/gui/`), `python/survival_hud/` | Top-right survival panel. The server builds lines (other mods add sections via `HudApi`) and sends a packet; the client draws the snowy frame. `python/survival_hud/` renders previews (via the JS mirror `hud_render.js`) and generates the border textures offline. |
| singleplayer-only | `mod-src/winterheart/.../Winterheart.java` | Crashes a dedicated server on load with a singleplayer-only message. |
| test-utils | `mod-src/winterheart/.../api/{TestMode,TestItems}.java` | `/toggle_test` (freeze time at noon, block spawns) and right-click diagnostics items. |
| (shared) | `mod-src/winterheart/.../api/` (`Sanity`, `Temperature`, `Cmd`, `Titles`, `Scheduler`, `Rand`, `Ticks`, `Numbers`, `CapBackup`) | Helpers used by several features (sanity/temperature getters and setters, titles, scheduling, numbers to words). |

## Guides

| Feature | Path | What it does |
| --- | --- | --- |
| guides | `mod-src/winterheart_guides/` (Java), `DP/winterheart_guides/data/winterheart/guides/guides.json`, `resourcepacks/winterheart_assets/` (book models), `tools/guide_editor/`; the items are in winterheart-core | Trigger-based guide pop-up books and lost journal pages. `guides.json` is edited with the Rust guide editor and hot-reloaded. Full reference: [features/guides.md](features/guides.md). |

## Recipes and items

| Feature | Path | What it does |
| --- | --- | --- |
| recipes | `DP/winterheart_recipes/` (generated, except gunsmith recipes), `python/recipes/gen_recipes.py`, and the items, textures, brewing, leaching recipe type and bottle pop-out from winterheart-core (`mod-src/winterheart/`, `python/core/`) | Recipe overrides and the custom item chains: metal parts (gears, plates, rods…), saltpeter leaching → gunpowder, early potions, moss → sponge. |
| tacz-guns | `DP/winterheart_tacz/` (pack filter, workbench tabs, per-gun `gundb` values), `tacz/tacz-pre.toml`, `python/tacz/gen_gunsmith_recipes.py` → `DP/winterheart_recipes/data/winterheart/recipes/tacz_workbench/` + `DP/winterheart_recipes/data/tacz/recipes/`, `config/tacz-*.toml` | TACZ default gun pack trimmed to pre-1950s weapons (by a pack filter) and their gunsmith recipes. |

## World

| Feature | Path | What it does |
| --- | --- | --- |
| worldgen | `DP/winterheart_worldgen/` (structure sets, biome modifiers, biome tags, `data/winterheart/worldgen/biome/spawn_clearing.json`), `DP/winterheart_novoatlas/`, `resourcepacks/winterheart_assets/assets/streamsreflowing/lang/` | Structure set overrides (vanilla, Philips Ruins, Gore Edition), mod worldgen tweaks, biome tags making the world cold/snowy, the `winterheart:spawn_clearing` biome, and the painted NovoAtlas overworld. |
| structures | `mod-src/winterheart_structures/` (Java), `DP/winterheart_structures/data/winterheart/fixed_structures/{placements,profiles}.json`, `DP/winterheart_structures/` (templates in `data/winterheart/structures/`, `.mcfunction`s), `python/structures/` (`export_structures.sh`, `nbt_to_structure.py`, `schem_to_structure.py`, `structure_fixups.py`, `gen_beacon.py`, `gen_wreck.py`), `config/worldedit/schematics/{watchtower.nbt,shipwreck.schem,shipwreck_chest.snbt}`, `config/starterstructure.json5`, `config/starterstructure/` | Hand-built structures placed once per world at fixed spots (watchtower, shipwreck, beacon) with `/place template`, plus the spawn starter structure. Templates are exported from WorldEdit saves. |
| world-painter | `tools/worldpainter/` | Rust planner for the world layout and the path south from spawn: paint biomes/heights, place structures; writes `DP/winterheart_novoatlas/` and `DP/winterheart_structures/data/winterheart/fixed_structures/placements.json`. |
| survival-data | `DP/winterheart_survival/` (`data/minecraft/legendarysurvivaloverhaul/`, `data/immersive_weathering/loot_tables/blocks/*permafrost.json`), `config/legendarysurvivaloverhaul/` | Legendary Survival Overhaul armour temperatures and permafrost drops. |

## Configs and tooling

| Feature | Path | What it does |
| --- | --- | --- |
| mod-configs | `config/`, `defaultconfigs/`, `config/packfinder/`, `config/starterkit/`, `config/fancymenu/` | Mod settings, starter kit, menus, pack load order. |
| run-pack | `run-pack.sh`, `tools/run_pack/` (Rust), `mod-src/winterheart/.../remote/` (listener) | Launches the client from the shell (offline), runs `--headless` smoke tests (AGENTS.md rule 7), and runs commands in its single-player world (`--send`/`--console`/`--run`). |
| agent-commands | `.claude/commands/`, `.claude/settings.json`, `AGENTS.md`, `CLAUDE.md` | Contributor rules and Claude Code slash commands (`/resolve`). |

## Research notes

- [audits/resource-packs.toml](audits/resource-packs.toml): how much of each resource pack loads, and which packs could be dropped (from file paths only).
- [audits/minezero-vanilla-death.md](audits/minezero-vanilla-death.md): how to bypass MineZero's death interception (untested).
