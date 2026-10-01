#!/usr/bin/env python3
"""Renders the survival HUD for clear / snow / blizzard weather into tools/hud/preview.png, using the real drawing code
in startup_scripts/survival_hud_render.js and the real textures. No game launch needed.

    python3 kubejs/tools/hud/preview.py            (needs node and Pillow)

Edit preview.json to change the title and lines. Colours, sizes, pile heights etc. are the constants at the top of the
render script; textures come from gen_textures.py. Text uses a stand-in font, so widths are approximate."""
import json, re, subprocess, sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
KUBEJS = HERE.parents[1]
SCRIPT = KUBEJS / 'startup_scripts' / 'survival_hud_render.js'
ASSETS = KUBEJS / 'assets'
SCALE = 4
SCREEN_W, SCREEN_H = 200, 170

proc = subprocess.run(['node', str(HERE / 'record_hud.js'), str(HERE / 'preview.json'), str(SCRIPT)], capture_output=True, text=True)
if proc.returncode != 0:
    sys.exit('render script failed:\n' + proc.stderr)
data = json.loads(proc.stdout)

gen = (HERE / 'gen_textures.py').read_text()
m = re.search(r"'VARIANTS':\s*(\d+)", gen)
if m and int(m.group(1)) != data['variants']:
    print(f"WARNING: gen_textures.py VARIANTS={m.group(1)} but HUD_VARIANTS={data['variants']} in the render script")

try:
    font = ImageFont.truetype('/System/Library/Fonts/Menlo.ttc', 8 * SCALE)
except OSError:
    font = ImageFont.load_default()
textures = {}
def texture(name):
    if name not in textures:
        ns, path = name.split(':')
        textures[name] = Image.open(ASSETS / ns / path).convert('RGBA')
    return textures[name]

panels = []
for weather in ('clear', 'snow', 'blizzard'):
    img = Image.new('RGBA', (SCREEN_W * SCALE, SCREEN_H * SCALE), (14, 20, 30, 255))
    for op in data[weather]:
        kind = op[0]
        if kind == 'fill':
            _, x0, y0, x1, y1, c = op
            a, r, g, b = c >> 24, (c >> 16) & 255, (c >> 8) & 255, c & 255
            layer = Image.new('RGBA', (max(0, x1 - x0) * SCALE, max(0, y1 - y0) * SCALE), (r, g, b, a))
            img.alpha_composite(layer, (x0 * SCALE, y0 * SCALE)) if x0 >= 0 and y0 >= 0 else None
        elif kind == 'blit':
            _, tex, x, y, u, v, w, h, tw, th = op
            tile = texture(tex).crop((u, v, u + w, v + h)).resize((w * SCALE, h * SCALE), Image.NEAREST)
            if x >= 0 and y >= 0:
                img.alpha_composite(tile, (x * SCALE, y * SCALE))
        elif kind == 'text':
            _, text, x, y, c = op
            ImageDraw.Draw(img).text((x * SCALE, y * SCALE - SCALE), text, font=font, fill=((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
    panels.append(img.crop(((SCREEN_W - 10 - 128 - 12) * SCALE, 0, SCREEN_W * SCALE, SCREEN_H * SCALE)))

out = Image.new('RGBA', (sum(p.width for p in panels) + 20, panels[0].height), (0, 0, 0, 255))
x = 0
for p in panels:
    out.alpha_composite(p, (x, 0)); x += p.width + 10
dest = HERE / 'preview.png'
out.convert('RGB').save(dest)
print('wrote', dest)
