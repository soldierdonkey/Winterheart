# resource-packs

Third-party resource packs shipped with the instance. Packs in `config/paxi/resourcepacks/` are loaded through Paxi.

## Files

- `config/paxi/resourcepacks/` — packs loaded by Paxi (`winterheart_assets` belongs to other features, see [feature-map.md](../feature-map.md))
- `config/paxi/resourcepack_load_order.json` — Paxi load order (empty)
- `resourcepacks/` — packs in the vanilla resource pack folder
- `.gitattributes` — line ending rules; no files use Git LFS

## How it works

The packs are tracked in git as zip files. `mods.json` and [fetch-mods](fetch-mods.md) don't cover them.

## Config / tunables

None.

## Changes

- 2026-11-26 — removed `config/paxi/resourcepacks/AHM.zip` and `config/paxi/resourcepacks/§6Dynamic§9Surroundings §2R19.zip` (unused sounds, the only Git LFS files). Not tested in game.

## Gotchas

A player's `options.txt` (untracked) may still name a removed pack.
