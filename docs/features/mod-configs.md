# mod-configs

Settings for third-party mods, plus the starter kit, menus and pack load order.

## Files

- `config/` — mod settings
- `defaultconfigs/` — server config defaults copied into new worlds
- `config/packfinder/` — pack load order ([packfinder](packfinder.md))
- `config/starterkit/` — starter kit
- `config/fancymenu/` — menus
- `config/citadel-common.toml` — Citadel settings

## How it works

Each mod reads its own files from `config/`.

Only settings are tracked. Files that mods write out themselves (docs, examples, translations, themes, editor and session state) are ignored in [.gitignore](../../.gitignore) under `Config: written by mods`, and the mods recreate what they need on launch. When a mod is removed, delete its config in the same commit.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `April Fools Content` | `config/citadel-common.toml` | `false` (mod default `true`) | Citadel's April 1 content |

## Changes

- 2026-11-26 — untracked mod-written files (OreSpawn docs, Streams Reflowing guides and examples, Konkrete translations, Better Caves READMEs, Starter Kit descriptions and inactive kits, FancyMenu themes and state, WorldEdit sessions). Deleted configs of mods that aren't installed: Alex's Mobs, Butchery Alex's Mobs, NuclearCraft, Almost Unified, AutoHUD, Biome Spawn Point, Distant Horizons, Forgematica, Ponder, Realistic Torches, Roughly Enough Items, Thin Air. Headless test (`--world "New World"`): pass, joined 29 s after the wait began; none of the deleted configs came back, no tracked config changed, and no config-related ERROR lines turned up in a keyword search. A fresh clone, where the ignored files are absent, was not tested.
- 2026-11-26 — disabled Citadel's April Fools content. Not tested in game.

## Gotchas

None known.
