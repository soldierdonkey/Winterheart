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
#   W L M D S K  head and blade, light to dark (the tier's metal)
#   a b c d      wood of the hacksaw bow, the same on every tier
#   e f g        handle, dark to light (the tier's grip)
#   t u          cord wrap of the small tier
WOOD = {"a": (137, 103, 39), "b": (104, 78, 30), "c": (73, 54, 21), "d": (40, 30, 11)}
CORD = {"t": (170, 152, 122), "u": (150, 132, 101)}
METAL_CHARS = "WLMDSK"
GRIP_CHARS = "efg"

# Handle materials, following the Tactical pack's tool tiers.
WOOD_GRIP = [(55, 41, 16), (89, 67, 25), (117, 88, 33)]
STONE_GRIP = [(60, 60, 60), (80, 80, 80), (106, 106, 106)]
RED_STONE_GRIP = [(45, 31, 38), (60, 42, 50), (74, 54, 63)]
BLACK_GRIP = [(20, 20, 20), (33, 33, 33), (51, 51, 51)]

# style: "small" (wood/stone in the pack: short blades, wood handle, cord wrap),
# "plain" (iron: full blades, metal ferrule), "fancy" (gold: ornate blades, gold
# guard and pommel), "bound" (diamond: iron blades, bright wrap, guard, gem pommel).
TIERS = {
    "bone": {"style": "small", "grip": WOOD_GRIP,
             "metal": [(252, 250, 232), (232, 227, 198), (210, 203, 170), (176, 166, 132), (134, 123, 94), (88, 78, 58)]},
    "copper": {"style": "plain", "grip": WOOD_GRIP,
               "metal": [(252, 190, 160), (231, 138, 103), (202, 109, 80), (166, 84, 62), (124, 60, 46), (82, 40, 32)]},
    "iron": {"style": "plain", "grip": STONE_GRIP,
             "metal": [(255, 255, 255), (216, 216, 216), (190, 190, 190), (150, 150, 150), (106, 106, 106), (68, 68, 68)]},
    "gold": {"style": "fancy", "grip": RED_STONE_GRIP,
             "metal": [(255, 255, 215), (253, 240, 118), (240, 201, 56), (212, 154, 30), (160, 104, 18), (104, 66, 10)]},
    "diamond": {"style": "bound", "grip": STONE_GRIP,
                "metal": [(224, 255, 250), (150, 242, 228), (80, 218, 205), (43, 175, 168), (25, 122, 122), (14, 78, 82)]},
    "netherite": {"style": "bound", "grip": BLACK_GRIP,
                  "metal": [(150, 138, 142), (112, 100, 104), (86, 76, 80), (66, 58, 62), (47, 40, 43), (29, 24, 26)]},
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
        self.style = TIERS[tier]["style"]
        self.palette = {**WOOD, **CORD, **dict(zip(METAL_CHARS, TIERS[tier]["metal"])),
                        **dict(zip(GRIP_CHARS, TIERS[tier]["grip"]))}
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


def px(n):
    """Texture pixels for a face side n units long; thin parts still get one."""
    return max(1, int(n))


def flat(*cycle):
    """Fills a face with horizontal bands, cycling through the rows from the top.
    A row string is repeated or cut to the face's width."""
    return lambda w, h: [(cycle[j % len(cycle)] * px(w))[:px(w)] for j in range(px(h))]


def banded(edge, body):
    """A face with a one pixel rim at the top and bottom."""
    return lambda w, h: [(edge if j in (0, px(h) - 1) else body) * px(w) for j in range(px(h))]


def fixed(*rows):
    """A face drawn exactly as given."""
    return lambda w, h: list(rows)


def checker(a, b):
    """Studded striking face of a meat mallet."""
    return lambda w, h: [((a + b) * px(w))[j % 2:j % 2 + px(w)] for j in range(px(h))]


def by_style(sheet, plain, small=None, fancy=None):
    """Picks a tool part for the tier: small and fancy fall back to plain."""
    return {"small": small, "fancy": fancy}.get(sheet.style) or plain


def handle(sheet, guard=True, top=8):
    """The handle, its end and, with guard, the join to a blade at height top."""
    style = sheet.style
    bottom = 2 if style == "small" else 3
    if style == "fancy":
        # Red stone with gold bands, gold guard and flared gold pommel.
        sheet.box([0, bottom, 7], [2, top, 8], flat("fg", "ef", "MD"), flat("f", "e", "D"), flat("f"))
        sheet.box([-0.5, 1, 6.75], [2.5, 3, 8.25], fixed("LML", "DSD"), fixed("M", "S"), fixed("LML"))
        sheet.box([0.5, 0, 7.25], [1.5, 1, 7.75], fixed("L"), fixed("M"), fixed("W"))
        if guard:
            sheet.box([-1, top, 6.5], [3, top + 1, 8.5], fixed("DLLD"), fixed("SS"), flat("M"))
        return
    sheet.box([0, bottom, 7], [2, top, 8], flat("fg", "ef"), flat("f", "e"), flat("f"))
    if style == "small":
        # Bare wood with a cord wrap where the head is lashed on.
        sheet.box([-0.25, top - 2, 6.75], [2.25, top + (1 if guard else 0), 8.25],
                  flat("tu", "ut"), flat("u", "t"), flat("t"))
    elif style == "plain":
        sheet.box([-0.25, 2, 6.75], [2.25, 3, 8.25], fixed("ee"), fixed("e"), fixed("ee"))
        if guard:
            sheet.box([-0.25, top, 6.75], [2.25, top + 1, 8.25], fixed("SS"), fixed("K"), fixed("SS"))
    else:
        # Bound: a bright wrap below the head, a guard and a pommel with a gem.
        sheet.box([-0.25, top - 4, 6.75], [2.25, top - 1, 8.25],
                  fixed("SS", "LM", "SS"), fixed("K", "D", "K"), fixed("SS"))
        sheet.box([-0.5, 1, 6.5], [2.5, 3, 8.5], fixed("DDD", "DWD"), fixed("SS", "SS"), flat("D"))
        if guard:
            sheet.box([-1, top, 6.75], [3, top + 1, 8.25], fixed("DLLD"), fixed("S"), flat("M"))


def skinning_knife(sheet):
    handle(sheet)
    # Saber blade: the edge bellies forward and both sides sweep back to a sharp tip.
    sheet.plane(-2, 9, by_style(sheet, plain=[
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
    ], small=[
        ".....W",
        "....WS",
        "...WLS",
        "..WLMS",
        "..WLS.",
        ".WLMS.",
        ".WLS..",
        ".WLS..",
        "..LS..",
        "..LS..",
    ], fancy=[
        # Longer, with a fuller down the middle and a hooked spur on the spine.
        "......W",
        ".....WS",
        "....WLS",
        "...WLMS",
        "...WLDS",
        "..WLMDS",
        "..WLDS.",
        "..WLDSS",
        ".WLMD.S",
        ".WLMDS.",
        ".WLMDS.",
        ".WLDS..",
        ".WLDS..",
        ".WLDS..",
        "..LDS..",
        "..LSS..",
    ]))


def cleaver(sheet):
    handle(sheet)
    # Rectangular blade, edge to the left, hanging hole near the top corner.
    x, rows = by_style(sheet, plain=(-6, [
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
    ]), small=(-4, [
        "WLLLLLS",
        "WLMMKDS",
        "WLMK.KS",
        "WLMMKDS",
        "WLMMMDS",
        "WLMMMDS",
        ".WLMDDS",
        "..SSSS.",
        "....SS.",
    ]), fancy=(-7, [
        # Larger, rounded at the front, with an engraved diamond.
        "..WLLLLLLS",
        ".WLMMMMKDS",
        ".WLMMMK.KS",
        "WLMMMMMKDS",
        "WLMMMDMMDS",
        "WLMMDLDMDS",
        "WLMMMDMMDS",
        "WLMMMMMMDS",
        "WLMMMMMMDS",
        "WLMMMMMMDS",
        ".WLMMMMDDS",
        "..WSSSSSS.",
        ".......SS.",
    ]))
    sheet.plane(x, 9, rows)


def hacksaw(sheet):
    handle(sheet, guard=False)
    # Bone saw: toothed blade in line with the handle, wooden bow behind it.
    small = sheet.style == "small"
    top, back = (17, 5) if small else (20, 7)
    wood = (flat("a", "b"), flat("c"), flat("a"))
    sheet.box([0, 8, 7], [back + 1, 9, 8], *wood)
    sheet.box([0, top, 7], [back + 1, top + 1, 8], *wood)
    sheet.box([back, 9, 7], [back + 1, top, 8], flat("b", "b", "b", "c"), flat("c"), flat("a"))
    sheet.plane(-1, 9, [".WL", "SML"] * 4 if small else [".WL", "SML", ".WL"] + ["SML", ".WL"] * 4)
    if sheet.style == "fancy":
        # Gold caps on the top corners and a spire on the back.
        for x in (-0.25, back - 0.25):
            sheet.box([x, top - 0.25, 6.75], [x + 1.5, top + 1.25, 8.25], fixed("LM"), fixed("D"), fixed("LM"))
        sheet.box([back + 0.25, top + 1.25, 7.25], [back + 0.75, top + 3, 7.75], fixed("L", "M"), fixed("D", "D"), fixed("W"))


def hammer(sheet):
    handle(sheet, guard=False)
    small = sheet.style == "small"
    neck = 12 if small else 13
    sheet.box([0.25, 8, 7.25], [1.75, neck, 7.75], flat("f", "e"), flat("e"), flat("f"))
    if small:
        sheet.box([-2, 12, 6], [4, 16, 9], banded("D", "M"), banded("S", "D"), flat("L"))
        for x in (-3, 4):
            sheet.box([x, 11.5, 5.75], [x + 1, 16.5, 9.25], banded("M", "L"), banded("D", "M"), flat("W"))
        return
    # Square-headed butcher's mallet with raised striking faces.
    sheet.box([-3, 13, 5.5], [5, 18, 9.5], banded("D", "M"), banded("S", "D"), flat("L"))
    face = checker("L", "D") if sheet.style == "fancy" else banded("D", "M")
    for x in (-5, 5):
        sheet.box([x, 12.5, 5], [x + 2, 18.5, 10], banded("M", "L"), face, flat("W"))
    if sheet.style == "fancy":
        # Studded faces and a crown spike.
        sheet.box([0, 18, 7], [2, 19, 8], fixed("MM"), fixed("D"), fixed("LL"))
        sheet.box([0.5, 19, 7.25], [1.5, 20.5, 7.75], fixed("L"), fixed("M"), fixed("W"))


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
    for tier in TIERS:
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
