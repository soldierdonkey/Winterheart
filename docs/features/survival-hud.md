# survival-hud

The survival panel in the top right of the screen: a snowy spruce frame whose snow level follows the weather, with lines of survival info. It is drawn by KubeJS without a sidebar mod.

## Files

- `mod-src/winterheart_hud/` (Java): `HudServer.java` (builds the lines, every 5 ticks per player), `HudApi.java` (other mods add sections), `net/HudNet.java` (the packet), `client/HudClient.java` (stores the packet and draws the panel).
- `mod-src/winterheart_hud/src/main/resources/assets/winterheart/textures/gui/{wood_border,wood_stubs,plank_bg}.png`: frame tiles (three snow levels), overflow plank stubs, background planks.
- `python/survival_hud/gen_textures.py`: generates the three PNGs from `SETTINGS` (needs Pillow).
- `python/survival_hud/hud_render.js`: a JS mirror of `HudClient`'s drawing, only for the offline preview. Keep it in step with the Java.
- `python/survival_hud/preview.py`, `record_hud.js`, `preview.json`, `preview.png`: renders the HUD for clear/snow/blizzard weather without the game. `record_hud.js` runs `hud_render.js` against a mock `GuiGraphics` in Node and records the draw calls. `preview.py` paints them into `preview.png` (needs node and Pillow).
- `mods/winterheart_hud-1.0.0.jar`: the built jar.

## How it works

Server -> packet -> client store -> render event. `HudServer` runs on `PlayerTickEvent` every 5 ticks. It builds `style|text` lines: Forecast (day and phase from the world state, weather, the ambient temperature to 3 significant digits), then each section registered with `HudApi.addSection` (limb poising adds "Limb Status", see [limb-poisoning](limb-poisoning.md)). It sends them with the title and a weather key (`clear`, `snow`, `blizzard`). `HudClient` draws the panel after the hotbar overlay (`RenderGuiOverlayEvent.Post`). Styles: `section`, `normal`, `accent`, `warning`, `critical`, `muted`, `spacer`; a `c:` prefix centres a line. The tile layouts of the three textures are described in the header of `HudClient.java`. Render errors are logged once as `[SurvivalHUD]`.

Offline workflow:

```sh
python3 python/survival_hud/gen_textures.py   # rewrite the PNGs in the mod's textures/gui/
python3 python/survival_hud/preview.py        # render python/survival_hud/preview.png
```

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `UPDATE_TICKS` | `HudServer.java` | 5 | Ticks between updates |
| layout constants | `HudClient.java` | see file | Width, margin, padding, snow and plank chances |
| `SETTINGS` | `python/survival_hud/gen_textures.py` | - | Texture colours, sizes, snow amounts |

## Changes

- 2026-11-22: Ported from KubeJS (`custom_sidebars.js`, `client_scripts/survival_hud.js`, the startup `survival_hud_render.js`) to the Java mod `winterheart_hud` (step 5 of [general-kubejs-removal](../todo/general-kubejs-removal.md)). The drawing code is a line-by-line port, so it draws the same panel, and a regular client mod renders it (no startup script, so a rebuilt jar and restart are needed for changes). The textures moved into the mod. The JS renderer stays in `python/survival_hud/` as the preview mirror; the preview tool, run from the new location, produced an identical `preview.png`. Tested: the packet and HUD lines for the joined player (`Day: 16`, `Time: DAWN`, `Weather: Clear`, `Ambient: 0.00°`); the client received packets and rendered frames with no `[SurvivalHUD]` render error. The panel wasn't looked at on screen.
- 2026-11-22: Moved the offline tools from `kubejs/tools/hud/` to `python/survival_hud/`. Fixed their path setup (`KUBEJS`, `OUT`) and usage strings. Ran both scripts from another directory: the regenerated textures and `preview.png` are byte-identical to the committed ones.

## Gotchas

- `hud_render.js` and `HudClient.java` must be kept in step by hand; `gen_textures.py`'s `VARIANTS` must equal both.
