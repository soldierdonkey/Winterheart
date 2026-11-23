# Contributor docs

Backend/feature docs for contributors and agents. Not a player guide. Rules: [AGENTS.md](../AGENTS.md). New docs start from [_template.md](_template.md).

- **[feature-map.md](feature-map.md):** every custom file and folder in the repo, grouped by feature. Start here to find where something lives.
- **[data-map.md](data-map.md):** what each datapack and data folder does and its format (overrides vs new content).
- **`features/<feature-name>.md`:** one deep-dive doc per feature.
- **`audits/`:** research notes and analyses that are not tied to one feature's doc.
- **[TODO.md](TODO.md):** open multi-step work, with date added and progress. Each todo's plan lives in `todo/`; workflow in [todo/README.md](todo/README.md).

## Feature index

One row per feature. Name = doc filename = CHANGELOG/commit prefix. Paths and details are in [feature-map.md](feature-map.md).

| Feature | Doc | Main path |
| --- | --- | --- |
| seppuku | [features/seppuku.md](features/seppuku.md) | `mod-src/seppuku/`, `kubejs/server_scripts/mechanics/sanity_seppuku.js` |
| winterheart-mod-template | [features/winterheart-mod-template.md](features/winterheart-mod-template.md) | `mod-src/_template/` |
| mod-publishing | [features/mod-publishing.md](features/mod-publishing.md) | `publish-mods.sh` |
| time-system | _todo_ | `kubejs/server_scripts/mechanics/time_system.js` |
| mob-spawning | _todo_ | `kubejs/server_scripts/mechanics/spawning.js` |
| sanity | _todo_ | `kubejs/server_scripts/mechanics/sanity_despawn.js` |
| limb-poisoning | _todo_ | `kubejs/server_scripts/mechanics/limb_poisoning.js` |
| proximity-effects | [features/proximity-effects.md](features/proximity-effects.md) | `kubejs/server_scripts/mechanics/proximity.js` |
| beacon-base | _todo_ | `kubejs/server_scripts/mechanics/beacon_base.js` |
| survival-hud | [features/survival-hud.md](features/survival-hud.md) | `kubejs/startup_scripts/survival_hud_render.js` |
| test-utils | _todo_ | `kubejs/server_scripts/utils/test_mode.js` |
| guides | [README in the scripts folder](../kubejs/server_scripts/questing/guides/README.md) | `kubejs/server_scripts/questing/guides/`, `tools/guide_editor/` |
| quests | _todo_ | `kubejs/server_scripts/questing/quests.js` |
| recipes | [features/recipes.md](features/recipes.md) | `config/paxi/datapacks/winterheart_recipes/`, `python/recipes/gen_recipes.py` |
| tacz-guns | [features/tacz-guns.md](features/tacz-guns.md) | `tacz/` |
| worldgen | [features/worldgen.md](features/worldgen.md) | `config/paxi/datapacks/winterheart_worldgen/`, `config/paxi/datapacks/winterheart_novoatlas/` |
| structures | [features/structures.md](features/structures.md) | `kubejs/server_scripts/worldgen/fixed_structures.js`, `config/paxi/datapacks/winterheart_structures/` |
| world-painter | _todo_ | `tools/worldpainter/` |
| survival-data | [features/survival-data.md](features/survival-data.md) | `config/paxi/datapacks/winterheart_survival/` |
| mod-configs | _todo_ | `config/` |
| run-pack | [features/run-pack.md](features/run-pack.md) | `run-pack.sh`, `python/run_pack/` |
| agent-commands | [features/agent-commands.md](features/agent-commands.md) | `.claude/commands/` |

Rows marked _todo_ have no feature doc yet. Write the doc the next time you touch that feature, and replace _todo_ with a link.
