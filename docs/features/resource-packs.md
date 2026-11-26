# resource-packs

Third-party resource packs shipped with the instance. [packfinder](packfinder.md) keeps every pack in `resourcepacks/` enabled.

## Files

- `resourcepacks/` — the packs (`winterheart_assets` belongs to other features, see [feature-map.md](../feature-map.md))
- `config/packfinder/resourcepack_order.json` — priority order, lowest first
- `.gitattributes` — line ending rules; no files use Git LFS

## How it works

Third-party zips are not tracked in git: [fetch](fetch.md) lists them in `mods.json` and downloads them. `winterheart_assets/` is tracked.

## Config / tunables

None. Priority is set in the order file (see [packfinder](packfinder.md)).

## Changes

- 2026-11-26 — removed `Clear Glass Pack 1.20.zip`. Headless load (`--world "New World"`) passed: joined in 42 s, no ERROR line naming the pack. Not checked by eye in game.
- 2026-11-26 — removed `TheForestCustomTextures.zip`, `Universal Bushy Leaves.zip` and `Hidden-Armor-1.2.0.zip`. Headless load (`--world "New World"`) passed: joined in 45 s, 22 third-party packs and `winterheart_assets` loaded, no ERROR line naming a removed pack. Not checked by eye in game.
- 2026-11-26 — added `mods/polytone-1.20-3.5.26.jar` (Polytone, from Modrinth), so packs' `polytone/` files load, e.g. in `DarkAtmosphere2.0.zip`. `./run-pack.sh --headless --world "New World"`: pass, joined after 40s. Log: `Reloaded Polytone Resources in 49 ms`; a `Found missing ID ... minecraft:vines` error from Polytone (a pack names a block that doesn't exist; skipped); a warning that `fusion` and `polytone` both modify Embeddium's `BlockRenderer`. Visuals not checked in game.
- 2026-11-26 — untracked the third-party zips and added them to `mods.json` ([fetch](fetch.md)). Files unchanged.
- 2026-11-26 — moved every pack to `resourcepacks/`, loaded by [packfinder](packfinder.md) instead of Paxi. All packs there are now enabled, including `§bEnchanting§8-§bReimagined§0_§8[v1.4]§0.zip`, which was off. Removed `Quark Programmer Art.zip`, `Omega-Colorful-Lighting-Compat.zip` (crashed the game without Colorful Lighting) and `TheForest-Colorful-Lighting-Compat` (zip and folder). Test: see [packfinder](packfinder.md#changes).
- 2026-11-26 — removed `config/paxi/resourcepacks/AHM.zip` and `config/paxi/resourcepacks/§6Dynamic§9Surroundings §2R19.zip` (unused sounds, the only Git LFS files). Not tested in game.

## Gotchas

- Polytone's licence differs by site (Modrinth: GPL-3.0-or-later; CurseForge: a custom licence forbidding redistribution and modpack bundling). Keep the jar `modrinth`/`curseforge` in `mods.json`, never `repo`.
- Polytone declares `slotify` incompatible.
- A player's `options.txt` (untracked) may still name a removed pack.
- After adding, updating or removing a pack, run `./fetch.sh --generate`.
- A new pack is enabled as soon as it is in the folder. Add it to the order file if its priority matters.
