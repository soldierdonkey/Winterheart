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

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `April Fools Content` | `config/citadel-common.toml` | `false` (mod default `true`) | Citadel's April 1 content |

## Changes

- 2026-11-26 — disabled Citadel's April Fools content. Not tested in game.

## Gotchas

None known.
