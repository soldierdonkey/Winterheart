#!/usr/bin/env python3
"""Writes the 3D models and textures of Butchery's tools into winterheart_assets.

Run from the instance root: python3 python/butchery_tools/gen_tool_models.py

The model layout and display transforms are adapted from Tactical 3D Tools
(CC-BY-4.0, see the pack README). The textures are drawn here. Nothing is read
or traced from Butchery's jar.

Model space (before the shared -45 degree turn): the tool stands upright, x = 1
is its centre line, -x is the cutting edge. One model unit is one texture pixel.
"""
import json
import struct
import zlib
from pathlib import Path

ASSETS = Path("resourcepacks/winterheart_assets/assets")
MODELS = ASSETS / "butchery/models/item"
OWN_MODELS = ASSETS / "winterheart/models/item/butchery"
TEXTURES = ASSETS / "winterheart/textures/item/butchery"
SIZE = 32  # texture width and height in pixels

# One character per colour in the shapes below; "." is transparent.
# Wood is the same on every tier.
WOOD = {"a": (137, 103, 39), "b": (104, 78, 30), "c": (73, 54, 21), "d": (40, 30, 11)}
# Head and blade colours of each tier, for the characters W L M D S K (light to dark).
METAL_CHARS = "WLMDSK"
METALS = {
    "bone": [(252, 250, 232), (232, 227, 198), (210, 203, 170), (176, 166, 132), (134, 123, 94), (88, 78, 58)],
    "copper": [(252, 190, 160), (231, 138, 103), (202, 109, 80), (166, 84, 62), (124, 60, 46), (82, 40, 32)],
    "iron": [(255, 255, 255), (216, 216, 216), (190, 190, 190), (150, 150, 150), (106, 106, 106), (68, 68, 68)],
    "gold": [(255, 255, 215), (253, 240, 118), (240, 201, 56), (212, 154, 30), (160, 104, 18), (104, 66, 10)],
    "diamond": [(224, 255, 250), (150, 242, 228), (80, 218, 205), (43, 175, 168), (25, 122, 122), (14, 78, 82)],
    "netherite": [(150, 138, 142), (112, 100, 104), (86, 76, 80), (66, 58, 62), (47, 40, 43), (29, 24, 26)],
}

# Display transforms of the pack's item/template/template_tool.
DISPLAY = {
    "thirdperson_righthand": {"rotation": [-45, -90, 0], "translation": [-1, 3.32, 1]},
    "thirdperson_lefthand": {"rotation": [-45, 90, 0], "translation": [0, 3.32, 1]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [0.5, 4, 1.13], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [0.5, 4, 1.13], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 90], "translation": [0, 9.75, 0]},
    "gui": {"rotation": [-20, -180, 90], "translation": [-0.25, -1, 0], "scale": [0.6, 0.6, 0.6]},
    "fixed": {"rotation": [0, 0, 90], "translation": [0, 0.25, 0], "scale": [2, 2, 2]},
}


class Sheet:
    """A texture being filled, plus the elements that use it."""

    def __init__(self, tier):
        self.palette = {**WOOD, **dict(zip(METAL_CHARS, METALS[tier]))}
        self.pixels: list[list[tuple[int, int, int] | None]] = [
            [None] * SIZE for _ in range(SIZE)
        ]
        self.used = [[False] * SIZE for _ in range(SIZE)]
        self.elements = []

    def place(self, rows):
        """Copies a block of palette characters into free space; returns (x, y, w, h)."""
        h, w = len(rows), len(rows[0])
        assert all(len(r) == w for r in rows), rows
        for y in range(SIZE - h + 1):
            for x in range(SIZE - w + 1):
                if not any(self.used[j][i] for j in range(y, y + h) for i in range(x, x + w)):
                    for j, row in enumerate(rows):
                        for i, char in enumerate(row):
                            self.used[y + j][x + i] = True
                            self.pixels[y + j][x + i] = None if char == "." else self.palette[char]
                    return x, y, w, h
        raise SystemExit(f"texture is full, cannot place {w}x{h}")

    def element(self, start, end, faces):
        self.elements.append({
            "from": start, "to": end,
            "rotation": {"angle": -45, "axis": "z", "origin": [0, 0, 0]},
            "faces": faces,
        })

    def face(self, rows, mirror=False):
        x, y, w, h = self.place(rows)
        u1, v1, u2, v2 = (n * 16 / SIZE for n in (x, y, x + w, y + h))
        return {"uv": [u2, v1, u1, v2] if mirror else [u1, v1, u2, v2], "texture": "#0"}

    def box(self, start, end, front, side, cap):
        """A solid box. front, side and cap take (width, height) and return rows."""
        dx, dy, dz = (end[i] - start[i] for i in range(3))
        wide, narrow, top = front(dx, dy), side(dz, dy), cap(dx, dz)
        self.element(start, end, {
            "north": self.face(wide, mirror=True), "south": self.face(wide),
            "east": self.face(narrow), "west": self.face(narrow, mirror=True),
            "up": self.face(top), "down": self.face(top),
        })

    def plane(self, x, y, rows, z=7.5):
        """A flat blade. rows read as seen with -x (the edge) on the left."""
        h, w = len(rows), len(rows[0])
        south = self.face(rows)
        north = {"uv": [south["uv"][2], south["uv"][1], south["uv"][0], south["uv"][3]], "texture": "#0"}
        self.element([x - 0.005, y - 0.005, z - 0.005], [x + w + 0.005, y + h + 0.005, z + 0.005],
                     {"north": north, "south": south})


def flat(*cycle):
    """Fills a face with horizontal bands, cycling through the colours from the top."""
    return lambda w, h: [cycle[j % len(cycle)] * int(w) for j in range(int(h))]


def banded(edge, body):
    """A face with a one pixel rim at the top and bottom."""
    return lambda w, h: [(edge if j in (0, h - 1) else body) * int(w) for j in range(int(h))]


def grip(sheet, bottom=3, top=8):
    """The wrapped wooden handle and its end cap, shared by every tool."""
    sheet.box([0, bottom, 7], [2, top, 8], flat("ab", "cc"), flat("b", "c"), flat("b"))
    sheet.box([-0.25, bottom - 1, 6.75], [2.25, bottom, 8.25],
              lambda w, h: ["cc"], lambda w, h: ["d"], lambda w, h: ["cc"])


def collar(sheet, y):
    """The metal ferrule between a handle and a blade."""
    sheet.box([-0.25, y, 6.75], [2.25, y + 1, 8.25],
              lambda w, h: ["SS"], lambda w, h: ["K"], lambda w, h: ["SS"])


def skinning_knife(sheet):
    grip(sheet)
    collar(sheet, 8)
    # Saber blade: the edge bellies forward and both sides sweep back to a sharp tip.
    sheet.plane(-2, 9, [
        ".....W",
        "....WS",
        "...WLS",
        "..WLMS",
        "..WLS.",
        ".WLMS.",
        ".WLMS.",
        ".WLS..",
        ".WLS..",
        ".WLS..",
        ".WLS..",
        ".WLS..",
        "..LS..",
        "..LS..",
    ])


def cleaver(sheet):
    grip(sheet)
    collar(sheet, 8)
    # Tall rectangular blade, edge to the left, hanging hole near the top corner.
    sheet.plane(-6, 9, [
        "WLLLLLLLS",
        "WLMMMMKDS",
        "WLMMMK.KS",
        "WLMMMMKDS",
        "WLMMMMMDS",
        "WLMMMMMDS",
        "WLMMMMMDS",
        "WLMMMMMDS",
        "WLMMMMMDS",
        ".WLMMMDDS",
        "..SSSSSS.",
        "......SS.",
    ])


def hacksaw(sheet):
    grip(sheet)
    # Bone saw: toothed blade in line with the handle, wooden bow behind it.
    wood = (flat("a", "b"), flat("c"), flat("a"))
    sheet.box([0, 8, 7], [8, 9, 8], *wood)
    sheet.box([0, 20, 7], [8, 21, 8], *wood)
    sheet.box([7, 9, 7], [8, 20, 8], flat("b", "b", "b", "c"), flat("c"), flat("a"))
    sheet.plane(-1, 9, [
        ".WL",
        "SML",
        ".WL",
    ] + ["SML", ".WL"] * 4)


def hammer(sheet):
    grip(sheet, 3, 8)
    sheet.box([0.25, 8, 7.25], [1.75, 13, 7.75],
              lambda w, h: ["b"] * int(h), lambda w, h: ["c"] * int(h), lambda w, h: ["b"])
    # Square-headed butcher's mallet with raised striking faces.
    sheet.box([-3, 13, 5.5], [5, 18, 9.5], banded("D", "M"), banded("S", "D"), flat("L"))
    for x in (-5, 5):
        sheet.box([x, 12.5, 5], [x + 2, 18.5, 10], banded("M", "L"), banded("D", "M"), flat("W"))


def png(path, pixels):
    raw = b"".join(
        b"\0" + b"".join(bytes(p + (255,)) if p else bytes(4) for p in row) for row in pixels)

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

    header = struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def write(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n")


def main():
    for folder in (MODELS, OWN_MODELS, TEXTURES):
        folder.mkdir(parents=True, exist_ok=True)
    write(OWN_MODELS / "template_tool.json", {"gui_light": "front", "display": DISPLAY})
    for tier in METALS:
        for shape in (skinning_knife, cleaver, hacksaw, hammer):
            name = f"{tier}_{shape.__name__}"
            sheet = Sheet(tier)
            shape(sheet)
            texture = f"winterheart:item/butchery/{name}"
            png(TEXTURES / f"{name}.png", sheet.pixels)
            write(MODELS / f"{name}.json", {
                "credit": "Adapted from Tactical 3D Tools by CmdrMCNuggets, CC-BY-4.0",
                "parent": "winterheart:item/butchery/template_tool",
                "texture_size": [SIZE, SIZE],
                "textures": {"0": texture, "particle": texture},
                "elements": sheet.elements,
            })
            print(f"{name}: {len(sheet.elements)} elements")


if __name__ == "__main__":
    main()
