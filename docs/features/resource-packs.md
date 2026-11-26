# resource-packs

Third-party resource packs shipped with the instance. [packfinder](packfinder.md) keeps every pack in `resourcepacks/` enabled.

## Files

- `resourcepacks/` — the packs (`winterheart_assets` belongs to other features, see [feature-map.md](../feature-map.md))
- `config/packfinder/resourcepack_order.json` — priority order, lowest first
- `.gitattributes` — line ending rules; no files use Git LFS

## How it works

Third-party zips are not tracked in git: [fetch-mods](fetch-mods.md) lists them in `mods.json` and downloads them. `TheForestCustomTextures.zip` and `winterheart_assets/` are tracked.

## Config / tunables

None. Priority is set in the order file (see [packfinder](packfinder.md)).

## Changes

- 2026-11-26 — untracked the third-party zips and added them to `mods.json` ([fetch-mods](fetch-mods.md)). Files unchanged.
- 2026-11-26 — moved every pack to `resourcepacks/`, loaded by [packfinder](packfinder.md) instead of Paxi. All packs there are now enabled, including `§bEnchanting§8-§bReimagined§0_§8[v1.4]§0.zip`, which was off. Removed `Quark Programmer Art.zip`, `Omega-Colorful-Lighting-Compat.zip` (crashed the game without Colorful Lighting) and `TheForest-Colorful-Lighting-Compat` (zip and folder). Test: see [packfinder](packfinder.md#changes).
- 2026-11-26 — removed `config/paxi/resourcepacks/AHM.zip` and `config/paxi/resourcepacks/§6Dynamic§9Surroundings §2R19.zip` (unused sounds, the only Git LFS files). Not tested in game.

## Gotchas

- A player's `options.txt` (untracked) may still name a removed pack.
- After adding, updating or removing a pack, run `./fetch-mods.sh --generate`.
- A new pack is enabled as soon as it is in the folder. Add it to the order file if its priority matters.
