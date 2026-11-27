# butchery-tool-models

3D held and inventory models for Butchery's skinning knives, cleavers, hacksaws and hammers (bone, copper, iron, gold, diamond, netherite), in the style of the `Tactical3DTools[10.2].zip` resource pack. Visual only: no behaviour changes.

## Files

- `python/butchery_tools/gen_tool_models.py` — generator: shapes, palette, textures, models
- `resourcepacks/winterheart_assets/assets/butchery/models/item/` — 24 overrides, `<tier>_<tool>.json` (generated)
- `resourcepacks/winterheart_assets/assets/winterheart/models/item/butchery/template_tool.json` — shared display transforms (generated)
- `resourcepacks/winterheart_assets/assets/winterheart/textures/item/butchery/` — one 32x32 texture per tool (generated)

## How it works

The pack replaces the `butchery:<tier>_<tool>` item models, which are flat `item/handheld` sprites in `butchery-5.2-forge-1.20.1.jar`. Each tool is a few boxes (handle, cap, ferrule or frame, mallet head; the hacksaw's bow is wood) plus, for the blades, a thin plane whose texture cuts out the silhouette. Every element is turned -45 degrees around z; in the unturned model x = 1 is the centre line, -x is the cutting edge and one unit is one texture pixel.

The shapes are ASCII art in the generator. Run `python3 python/butchery_tools/gen_tool_models.py` from the instance root after editing them, then press F3+T in game.

Every tier shares one shape per tool. A tier is a six-colour ramp in `METALS` for the characters `W L M D S K`; wood (`a b c d`) is the same on all tiers.

## Config / tunables

None. Palettes (`METALS`, `WOOD`) and shapes are constants in the generator.

## Changes

- 2026-11-26 — added models for the iron skinning knife, cleaver, hacksaw and hammer. Headless load (`--world "New World"`) passed: joined in 27 s, `winterheart_assets` loaded, no log line naming the new models or textures. Shapes checked in a flat offline preview only; in-hand and inventory appearance not yet checked in game.
- 2026-11-26 — added the bone, copper, gold, diamond and netherite tiers; the hacksaw bow is now wood; the skinning knife blade is longer. Checked in a flat offline preview. Not checked in game; no headless load (the instance was running).
- 2026-11-26 — reshaped the skinning knife as a saber blade with a sharp, backswept tip, and reshaded it. Checked in a flat offline preview only.

## Gotchas

- Licensing: the handle layout and display transforms are adapted from Tactical 3D Tools by CmdrMCNuggets (CC-BY-4.0); keep the attribution in the pack `README.md` and the models' `credit` field. Butchery is All Rights Reserved: do not copy or trace its textures.
- `template_tool.json` is our own copy of the pack's display transforms, so the models work without that pack.
- `winterheart_assets` must stay above other packs that model these items (see `config/packfinder/resourcepack_order.json`).
