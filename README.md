# Winterheart

Development instance of the **Winterheart** modpack: a cold-survival pack where the player has to escape south before the eternal night. Minecraft 1.20.1, Forge 47.4.10. Changes: [CHANGELOG.md](CHANGELOG.md).

## Repository layout

| Path | What it is |
| --- | --- |
| `kubejs/` | KubeJS scripts: the custom mechanics (time system, spawning, sanity, limb poisoning, HUD, guides), recipes, items. `kubejs/assets/` holds textures/lang for KubeJS-registered items. |
| `kubejs/data/` | Datapack data loaded by KubeJS (worldgen, structures, survival data). |
| `config/` | Mod configs. |
| `config/paxi/` | Datapacks and resource packs loaded through Paxi (load order in `datapack_load_order.json`, `resourcepack_load_order.json`). |
| `mod-src/` | Source for our own mods. New mods start from `mod-src/_template/new-mod.sh`. |
| `tacz/` | TACZ gun pack (only pre-1950s guns kept). |
| `tools/` | Rust apps (`guide_editor`, `worldpainter`) and structure export scripts. |
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

## Workflow

- We commit directly to `main`; branches and PRs are optional.
- Open an issue (**Task**, **Bug report**, **Art request**) and reference it in commits, e.g. `time-system: shorten sleep cooldown #12`.
- Pull before starting and before pushing. Never force-push shared work.
- Tell the team before changing shared configs, mod lists, or assets someone else is editing.
- Before marking work done: launch the instance, check the feature, check for new errors, and note what you tested in the issue.

Contributors (people and agents): read [AGENTS.md](AGENTS.md) for the documentation and commit rules.
