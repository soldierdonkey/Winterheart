# Contributor docs

Backend/feature docs for contributors and agents. Not a player guide. Rules: [AGENTS.md](../AGENTS.md). New docs start from [_template.md](_template.md).

- **[feature-map.md](feature-map.md):** every custom file and folder in the repo, grouped by feature. Start here to find where something lives.
- **[data-map.md](data-map.md):** what each datapack and data folder does and its format (overrides vs new content).
- **`features/<feature-name>.md`:** one deep-dive doc per feature.
- **[plot/plot.md](plot/plot.md):** the story (characters, timeline, endings) and its open questions. Check it before writing journal entries, titles or ending content.
- **`audits/`:** research notes and analyses that are not tied to one feature's doc.
- **[TODO.md](TODO.md):** open multi-step work, with date added and progress. Each todo's plan lives in `todo/`; workflow in [todo/README.md](todo/README.md).

## Feature index

One row per feature. Name = doc filename = CHANGELOG/commit prefix. Paths and details are in [feature-map.md](feature-map.md).

| Feature | Doc | Main path |
| --- | --- | --- |
| seppuku | [features/seppuku.md](features/seppuku.md) | `mod-src/seppuku/`, `mod-src/winterheart_survival/.../sanity/SeppukuTrigger.java` |
| winterheart-core | [features/winterheart-core.md](features/winterheart-core.md) | `mod-src/winterheart/` |
| singleplayer-only | [features/singleplayer-only.md](features/singleplayer-only.md) | `mod-src/winterheart/.../Winterheart.java` |
| winterheart-survival | _todo_ (see sanity, beacon-base, limb-poisoning, proximity-effects) | `mod-src/winterheart_survival/` |
| winterheart-mod-template | [features/winterheart-mod-template.md](features/winterheart-mod-template.md) | `mod-src/_template/` |
| mod-publishing | [features/mod-publishing.md](features/mod-publishing.md) | `publish-mods.sh` |
| packfinder | [features/packfinder.md](features/packfinder.md) | `mod-src/packfinder/`, `config/packfinder/` |
| resource-packs | [features/resource-packs.md](features/resource-packs.md) | `resourcepacks/` |
| fetch-mods | [features/fetch-mods.md](features/fetch-mods.md) | `fetch-mods.sh`, `mods.json`, `tools/fetch_mods/` |
| time-system | [features/time-system.md](features/time-system.md) | `mod-src/winterheart_time/`, `datapacks/winterheart_time/` |
| mob-spawning | [features/mob-spawning.md](features/mob-spawning.md) | `mod-src/winterheart_spawning/`, `datapacks/winterheart_spawning/` |
| sanity | [features/sanity.md](features/sanity.md) | `mod-src/winterheart_survival/.../sanity/SanityDespawn.java` |
| limb-poisoning | [features/limb-poisoning.md](features/limb-poisoning.md) | `mod-src/winterheart_survival/.../limb/LimbPoising.java` |
| proximity-effects | [features/proximity-effects.md](features/proximity-effects.md) | `mod-src/winterheart_survival/.../proximity/Proximity.java`, `datapacks/winterheart_survival/data/winterheart/proximity/rules.json` |
| beacon-base | [features/beacon-base.md](features/beacon-base.md) | `mod-src/winterheart_survival/.../world/BeaconBase.java` |
| survival-hud | [features/survival-hud.md](features/survival-hud.md) | `mod-src/winterheart_hud/` |
| test-utils | [features/test-utils.md](features/test-utils.md) | `mod-src/winterheart/.../api/TestMode.java` |
| guides | [features/guides.md](features/guides.md) | `mod-src/winterheart_guides/`, `datapacks/winterheart_guides/`, `tools/guide_editor/` |
| recipes | [features/recipes.md](features/recipes.md) | `datapacks/winterheart_recipes/`, `python/recipes/gen_recipes.py` |
| tacz-guns | [features/tacz-guns.md](features/tacz-guns.md) | `tacz/` |
| worldgen | [features/worldgen.md](features/worldgen.md) | `datapacks/winterheart_worldgen/`, `datapacks/winterheart_novoatlas/` |
| structures | [features/structures.md](features/structures.md) | `mod-src/winterheart_structures/`, `datapacks/winterheart_structures/` |
| world-painter | _todo_ | `tools/worldpainter/` |
| survival-data | [features/survival-data.md](features/survival-data.md) | `datapacks/winterheart_survival/` |
| mod-configs | _todo_ | `config/` |
| run-pack | [features/run-pack.md](features/run-pack.md) | `run-pack.sh`, `tools/run_pack/`, `mod-src/winterheart/.../remote/` |
| agent-commands | [features/agent-commands.md](features/agent-commands.md) | `.claude/commands/` |

Rows marked _todo_ have no feature doc yet. Write the doc the next time you touch that feature, and replace _todo_ with a link.
