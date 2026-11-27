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

Each tier in `TIERS` has a metal ramp (`W L M D S K`), a grip ramp (`e f g`) and a style, following how the Tactical pack varies its pickaxes, axes and swords by tier. The hacksaw bow is wood (`a b c d`) on every tier.

| Tier | Style | Handle | Blades |
| --- | --- | --- | --- |
| bone | `small` | wood, tan cord wrap (`t u`) at the head | shorter knife, smaller cleaver, hacksaw and mallet |
| copper | `plain` | wood, dark end cap, metal ferrule | standard |
| iron | `plain` | banded stone, dark end cap, metal ferrule | standard |
| gold | `fancy` | red stone with gold bands, wide gold guard, flared gold pommel | longer knife with fuller and spine spur, larger cleaver with engraved diamond, gold-capped hacksaw, studded mallet with crown spike |
| diamond | `bound` | stone, bright wrap, 4-wide guard, pommel with gem | standard |
| netherite | `bound` | black stone, wrap, guard, pommel with gem | standard |

`by_style()` picks a tool's blade for the style; `small` and `fancy` fall back to the plain one. The hacksaw and hammer get no guard.

## Config / tunables

None. Tiers (`TIERS`), palettes and shapes are constants in the generator.

## Changes

- 2026-11-26 — added models for the iron skinning knife, cleaver, hacksaw and hammer. Headless load (`--world "New World"`) passed: joined in 27 s, `winterheart_assets` loaded, no log line naming the new models or textures. Shapes checked in a flat offline preview only; in-hand and inventory appearance not yet checked in game.
- 2026-11-26 — added the bone, copper, gold, diamond and netherite tiers; the hacksaw bow is now wood; the skinning knife blade is longer. Checked in a flat offline preview. Not checked in game; no headless load (the instance was running).
- 2026-11-26 — reshaped the skinning knife as a saber blade with a sharp, backswept tip, and reshaded it. Checked in a flat offline preview only.
- 2026-11-26 — handles and blade sizes now vary by tier, after the Tactical pack's tools: small wood-and-cord bone tools, copper and stone handles, a fancy gold set, and bound diamond and netherite handles with guard and pommel. Headless load (`--world "New World"`) passed: joined in 30 s, no log line naming the models or textures (58 ERROR lines, the same count as before). Shapes checked in a flat offline preview only.
- 2026-11-26 — copper tools now have a wood handle instead of a copper one. Asset-only change; checked in a flat offline preview, no headless load.

## Gotchas

- Licensing: the handle layout and display transforms are adapted from Tactical 3D Tools by CmdrMCNuggets (CC-BY-4.0); keep the attribution in the pack `README.md` and the models' `credit` field. Butchery is All Rights Reserved: do not copy or trace its textures.
- `template_tool.json` is our own copy of the pack's display transforms, so the models work without that pack.
- `winterheart_assets` must stay above other packs that model these items (see `config/packfinder/resourcepack_order.json`).
