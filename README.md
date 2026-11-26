# Winterheart

Development instance of the **Winterheart** modpack: a cold-survival pack where the player is caught in a death loop at a far-north outpost as the days run out towards the eternal night. Story: [docs/plot/plot.md](docs/plot/plot.md). Minecraft 1.20.1, Forge 47.4.10. Changes: [CHANGELOG.md](CHANGELOG.md).

## Repository layout

| Path | What it is |
| --- | --- |
| `mod-src/` | Our own Forge mods: `winterheart` (core), `winterheart_survival`, `winterheart_guides`, `winterheart_time`, `winterheart_hud`, `winterheart_spawning`, `winterheart_structures`, `winterheart_fishing`, `seppuku`, and the `_template`. Built jars go to `mods/`. |
| `config/` | Mod configs. |
| `datapacks/`, `resourcepacks/` | Every pack here is always loaded, by [packfinder](docs/features/packfinder.md) (priority in `config/packfinder/`). All custom data lives in `datapacks/winterheart_*`. |
| `mod-src/` | Source for our own mods. New mods start from `mod-src/_template/new-mod.sh`. |
| `tacz/` | TACZ gun pack folder. `tacz-pre.toml` is tracked; TACZ exports its default pack here, and `datapacks/winterheart_tacz` trims it to pre-1950s guns. |
| `python/` | Helper and generator scripts (`structures/`: structure export and generators, `survival_hud/`: HUD textures and preview, `tacz/`: gunsmith recipe generator). |
| `tools/` | Rust apps (`fetch`, `guide_editor`, `run_pack`, `worldpainter`). |
| `run-pack.sh` | Launch the instance from the shell, headless smoke tests, commands in the running world. See [docs/features/run-pack.md](docs/features/run-pack.md). |
| `fetch.sh`, `mods.json` | Mod and resource pack manifest, and the script that downloads and verifies `mods/` and `resourcepacks/` from it. See [docs/features/fetch.md](docs/features/fetch.md). |
| `mods/`, `resourcepacks/`, `shaderpacks/` | Shipped binaries. In `mods/`, only our own and a few third-party jars are tracked; the rest are fetched. |
| `docs/` | Contributor/backend docs. See [docs/README.md](docs/README.md). |
| `CHANGELOG.md` | Release notes, newest first. |
| `THIRD_PARTY_NOTICES.md` | Licenses of the third-party jars tracked in git. |

For a full map of every custom file and the feature it belongs to, see **[docs/feature-map.md](docs/feature-map.md)**.

Not tracked (see [.gitignore](.gitignore)): worlds, logs, crash dumps, launcher state, build output.

## Getting started

1. Clone the repo into a CurseForge instance folder (the repo root is the instance root).
2. Run `./fetch.sh` to download the mods and resource packs (needs [Rust](https://rustup.rs); it walks you through the few manual downloads). Run it again after a pull that changes `mods.json`.
3. Launch the instance with Forge 47.4.10 on Minecraft 1.20.1.
4. Edit scripts/configs/data, then reload (`/reload` for server scripts and datapacks, F3+T for client scripts; restart for startup scripts) and test.

Building one of our own mods (needs JDK 17 or 21; the jar is copied into `mods/`):

```sh
cd mod-src/<mod_id> && ./gradlew build
```

Build and publish our mods and packs to CurseForge: `./publish.sh` (see [docs/features/mod-publishing.md](docs/features/mod-publishing.md)).

## Workflow

- We commit directly to `main`; branches and PRs are optional.
- Open an issue (**Task**, **Bug report**, **Art request**) and reference it in commits, e.g. `time-system: shorten sleep cooldown #12`.
- Pull before starting and before pushing. Never force-push shared work.
- Tell the team before changing shared configs, mod lists, or assets someone else is editing.
- Before marking work done: launch the instance, check the feature, check for new errors, and note what you tested in the issue.

Contributors (people and agents): read [AGENTS.md](AGENTS.md) for the documentation and commit rules.
