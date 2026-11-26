# Winterheart

Development instance of the **Winterheart** modpack: a cold-survival pack where the player is caught in a death loop at a far-north outpost as the days run out towards the eternal night. Story: [docs/plot/plot.md](docs/plot/plot.md). Minecraft 1.20.1, Forge 47.4.10. Changes: [CHANGELOG.md](CHANGELOG.md).

## Repository layout

| Path | What it is |
| --- | --- |
| `mod-src/` | Our own Forge mods: `winterheart` (core), `winterheart_survival`, `winterheart_guides`, `winterheart_time`, `winterheart_hud`, `winterheart_spawning`, `winterheart_structures`, `seppuku`, and the `_template`. Built jars go to `mods/`. |
| `config/` | Mod configs. |
| `config/paxi/` | Datapacks and resource packs loaded through Paxi (load order in `datapack_load_order.json`, `resourcepack_load_order.json`). All custom data lives in `config/paxi/datapacks/winterheart_*` (worldgen, structures, survival, novoatlas). |
| `mod-src/` | Source for our own mods. New mods start from `mod-src/_template/new-mod.sh`. |
| `tacz/` | TACZ gun pack (only pre-1950s guns kept). |
| `python/` | Helper and generator scripts (`structures/`: structure export and generators, `survival_hud/`: HUD textures and preview, `tacz/`: gun pack purge scripts). |
| `tools/` | Rust apps (`guide_editor`, `run_pack`, `worldpainter`). |
| `run-pack.sh` | Launch the instance from the shell, headless smoke tests. See [docs/features/run-pack.md](docs/features/run-pack.md). |
| `mods/`, `resourcepacks/`, `shaderpacks/` | Shipped binaries. |
| `docs/` | Contributor/backend docs. See [docs/README.md](docs/README.md). |
| `CHANGELOG.md` | Release notes, newest first. |

For a full map of every custom file and the feature it belongs to, see **[docs/feature-map.md](docs/feature-map.md)**.

Not tracked (see [.gitignore](.gitignore)): worlds, logs, crash dumps, launcher state, build output.

## Getting started

1. Clone the repo into a CurseForge instance folder (the repo root is the instance root).
2. Launch the instance with Forge 47.4.10 on Minecraft 1.20.1.
3. Edit scripts/configs/data, then reload (`/reload` for server scripts and datapacks, F3+T for client scripts; restart for startup scripts) and test.

Building one of our own mods (needs JDK 17 or 21; the jar is copied into `mods/`):

```sh
cd mod-src/<mod_id> && ./gradlew build
```

Build and publish every mod to CurseForge: `./publish-mods.sh` (see [docs/features/mod-publishing.md](docs/features/mod-publishing.md)).

## Workflow

- We commit directly to `main`; branches and PRs are optional.
- Open an issue (**Task**, **Bug report**, **Art request**) and reference it in commits, e.g. `time-system: shorten sleep cooldown #12`.
- Pull before starting and before pushing. Never force-push shared work.
- Tell the team before changing shared configs, mod lists, or assets someone else is editing.
- Before marking work done: launch the instance, check the feature, check for new errors, and note what you tested in the issue.

Contributors (people and agents): read [AGENTS.md](AGENTS.md) for the documentation and commit rules.
