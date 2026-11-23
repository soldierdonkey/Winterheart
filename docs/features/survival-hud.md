# survival-hud

The survival panel in the top right of the screen: a snowy spruce frame whose snow level follows the weather, with lines of survival info. It is drawn by KubeJS without a sidebar mod.

## Files

- `kubejs/server_scripts/mechanics/custom_sidebars.js`: server half. Every `SIDEBAR_CONFIG.UPDATE_TICKS`, it builds the title, weather key and lines and sends them with `player.sendData('winterheart_hud', …)`.
- `kubejs/client_scripts/survival_hud.js`: receives the `winterheart_hud` packet and stores it in `global.winterheartHud = { title, weather, lines: [{ style, text }] }`.
- `kubejs/startup_scripts/survival_hud_render.js`: draws the panel with ForgeEvents. That only exists in startup scripts, so restart the game after editing it.
- `kubejs/assets/winterheart/textures/gui/{wood_border,wood_stubs,plank_bg}.png`: frame tiles (three snow levels), overflow plank stubs, background planks.
- `python/survival_hud/gen_textures.py`: generates the three PNGs from `SETTINGS` (needs Pillow).
- `python/survival_hud/preview.py`, `record_hud.js`, `preview.json`, `preview.png`: renders the HUD for clear/snow/blizzard weather without the game. `record_hud.js` runs the real `survival_hud_render.js` against a mock `GuiGraphics` in Node and records the draw calls. `preview.py` paints them into `preview.png` (needs node and Pillow).

## How it works

Server → client packet → global → render event. The tile layouts of the three textures are described in the header of `survival_hud_render.js`. Errors are logged once as `[SurvivalHUD]` in `logs/kubejs/startup.log`.

Offline workflow:

```sh
python3 python/survival_hud/gen_textures.py   # rewrite the PNGs in kubejs/assets/winterheart/textures/gui/
python3 python/survival_hud/preview.py        # render python/survival_hud/preview.png
```

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `SIDEBAR_CONFIG` | `kubejs/server_scripts/mechanics/custom_sidebars.js` | — | Update interval and the content of the lines |
| `SETTINGS` | `python/survival_hud/gen_textures.py` | — | Texture colours, sizes, snow amounts |
| constants at the top | `python/survival_hud/preview.py` | — | Preview scale and screen size |

## Changes

- 2026-11-22: Moved the offline tools from `kubejs/tools/hud/` to `python/survival_hud/`. `kubejs/` now holds only scripts and assets. Fixed their path setup (`KUBEJS`, `OUT`) and usage strings. Ran both scripts from another directory: the regenerated textures and `preview.png` are byte-identical to the committed ones.

## Gotchas

- The renderer is a startup script, so changes to it need a game restart, and F3+T doesn't reload it.
