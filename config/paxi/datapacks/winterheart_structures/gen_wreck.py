"""Generate an mcfunction: a spruce/dark-oak (planks only) ship lying on its side, split by a leaning iceberg.

Coordinates are relative to the executor. Run it standing on the frozen-ocean surface
(feet at sea level, so water's top layer is ~-1).
"""
import math, random, sys

OUT = sys.argv[1]
rng = random.Random(1912)
M = {}  # (x,y,z) -> block string


def h(*a):  # deterministic hash noise in [0,1)
    n = hash(a) & 0xFFFFFFFF
    return ((n * 2654435761) & 0xFFFFFFFF) / 2**32


def put(p, b):
    M[p] = b


# ---------------------------------------------------------------- ship model (local frame)
# u: stern(-) -> bow(+), v: beam, w: keel(0) -> deck. The ship lies on its side, so
# v maps to world y and w to world +z (deck faces south). Planks/stairs/slabs/trapdoors only.
SP, DO = "spruce", "dark_oak"
L, B, H = 46, 11, 9
SC = -L / 2 + 10  # sterncastle front wall
FC = L / 2 - 8    # forecastle back wall
MAST_U, MAST2_U = 8.0, -13.0


def keel_rise(u):  # rocker: the bottom sweeps up into the stem and sternpost
    if u > L / 2 - 13:
        return 6.0 * ((u - (L / 2 - 13)) / 13) ** 2
    if u < -L / 2 + 8:
        return 2.0 * ((-L / 2 + 8 - u) / 8) ** 2
    return 0.0


def deck_h(u):
    d = H + 0.0045 * u * u  # sheer
    if u < SC:
        d += 3
    elif u > FC:
        d += 1.5
    return d


def u_limits(w):  # overhanging transom, raked stem
    return -L / 2 - 0.12 * w, L / 2 + 0.35 * (w - 6)


def half_beam(u, w):
    wm = keel_rise(u)
    we = (w - wm) * (H + 3) / (H + 3 - wm)  # sections stay V-shaped where the keel rises
    turn = 5.5  # height of the bilge turn
    if we < turn:  # deadrise V off the keel, rounding into the bilge (no flat floor)
        t = max(we, 0) / turn
        hb = 0.6 + (B / 2 - 0.6) * math.sin(t ** 1.15 * math.pi / 2)
    else:  # slight tumblehome toward the deck
        t = (we - turn) / (H + 3 - turn)
        hb = B / 2 * (1 - 0.1 * t * t)
    lo, hi = u_limits(w)
    if u > L / 2 - 16:  # fine entry at the bow
        k = min(1.0, max(0.0, (u - (L / 2 - 16)) / (hi - (L / 2 - 16))))
        hb *= (1 - k ** 1.8) ** 0.55
    elif u < -L / 2 + 6:  # narrower transom
        hb *= 1 - 0.3 * min(1.0, (-L / 2 + 6 - u) / 6)
    if abs(w - (H - 3.2)) < 0.45 or abs(w - (deck_h(u) - 0.4)) < 0.4:
        hb += 0.45  # wales / rubbing strake stand proud of the planking
    return hb


def is_wale(u, w):
    return abs(w - (H - 3.2)) < 0.45 or abs(w - (deck_h(u) - 0.4)) < 0.4


def hull_at(u, v, w):
    lo, hi = u_limits(w)
    wm = keel_rise(u)
    D = deck_h(u)
    av = abs(v)
    if av < 0.55:  # keel, stem, sternpost
        if wm - 1.2 <= w < wm + 0.5 and lo <= u <= hi + 0.5:
            return DO
        if hi - 0.5 <= u <= hi + 0.7 and wm - 1 <= w <= D + 1.8:
            return DO
        if lo - 0.6 <= u <= lo + 0.5 and wm - 1 <= w <= D + 1.5:
            return DO
    if not (lo <= u <= hi) or w < wm:
        return None
    hb = half_beam(u, w)
    if av > hb:
        return None
    # castle front walls rise from the main deck to the raised deck (doorway in the middle)
    for wall_u, top in ((SC + 0.5, deck_h(SC - 0.01) + 1.5), (FC - 0.5, deck_h(FC + 0.01) + 1.5)):
        if abs(u - wall_u) < 0.5 and D - 1 <= w <= top:
            return None if (av < 0.9 and w < D + 2.3) else DO
    if w > D + 1.5:
        return None
    side = av > hb - 1.05 - (0.45 if is_wale(u, w) else 0)
    end = u < lo + 1.0 or u > hi - 1.0
    if w > D:  # bulwark
        if side or end:
            return DO if w > D + 0.9 or is_wale(u, w) else SP
        return None
    if w > D - 1.0:  # deck planking with dark seams
        if FC - 4 < u < FC - 1 and av < 1.5:
            return None  # open cargo hatch
        return DO if (u < SC or math.floor(v) % 4 == 0) else SP
    if side or end or w < wm + 1.0:
        if is_wale(u, w):
            return DO
        if SC + 2 < u < FC - 2 and D - 2.7 < w < D - 1.3 and 1 < u % 5 < 2.6 and side:
            return None  # gun ports
        return DO if h(int(u // 3), int(w), 77) < 0.08 else SP
    if abs(u - SC) < 0.5 or abs(u - FC) < 0.5:
        return DO  # bulkheads
    return None


def rod(p, a, b, r):  # distance test from point p to segment a-b (3D, local coords)
    ab = [b[i] - a[i] for i in range(3)]
    ap = [p[i] - a[i] for i in range(3)]
    t = max(0.0, min(1.0, sum(ap[i] * ab[i] for i in range(3)) / sum(c * c for c in ab)))
    d2 = sum((ap[i] - ab[i] * t) ** 2 for i in range(3))
    return d2 <= r * r, t


def spar_at(u, v, w, name):
    if name == "bow":
        mu, top_add = MAST_U, 15
    else:
        mu, top_add = MAST2_U, 4
    D = deck_h(mu)
    r = math.hypot(u - mu, v)
    jag = 2.5 * h(int(math.atan2(v, u - mu) * 2), name)  # splintered break
    if keel_rise(mu) + 0.5 < w < D + top_add - jag:
        if r < 0.95:
            return SP
        if r < 1.2 and w > D and (w - D) % 4 < 0.55:
            return DO  # mast hoops
    if r < 1.6 and D - 0.3 < w < D + 0.8:
        return DO  # mast partners where it passes the deck
    if name != "bow":
        return None
    yw = D + 11.5  # yard (crosses the beam, so it's near-vertical now)
    if abs(v) < 5.5 and math.hypot(u - mu - 0.9, w - yw) < 0.55:
        return DO
    # bowsprit
    sw = deck_h(L / 2) - 0.5
    a = math.radians(24)
    hit, t = rod((u, v, w), (L / 2 - 2, 0, sw), (L / 2 - 2 + 11 * math.cos(a), 0, sw + 11 * math.sin(a)), 0.7)
    if hit and t < 0.92 - 0.15 * h(int(v * 4)):
        return DO
    return None


def solid_at(u, v, w, name):
    f = spar_at(u, v, w, name)
    return (f, True) if f else (hull_at(u, v, w), False)


# ------- octant matching: 2x2x2 samples per block -> best-fitting wooden block
# quadrant bits within one y-layer: 1=(x0,z0) 2=(x1,z0) 4=(x0,z1) 8=(x1,z1)
STAIR_TOP = {  # shape -> facing -> quadrants occupied by the raised step (from StairBlock)
    "straight": {"south": 12, "west": 5, "north": 3, "east": 10},
    "outer_left": {"south": 8, "west": 4, "north": 1, "east": 2},
    "outer_right": {"south": 4, "west": 1, "north": 2, "east": 8},
    "inner_left": {"south": 14, "west": 13, "north": 7, "east": 11},
    "inner_right": {"south": 13, "west": 7, "north": 11, "east": 14},
}
CANDS = [((0, 0), None), ((15, 15), "planks"),
         ((15, 0), "slab[type=bottom]"), ((0, 15), "slab[type=top]")]
for fac, m in (("north", 12), ("south", 3), ("east", 5), ("west", 10)):
    CANDS.append(((m, m), f"trapdoor[facing={fac},open=true,half=bottom]"))
for shape, d in STAIR_TOP.items():
    for fac, m in d.items():
        CANDS.append(((15, m), f"stairs[facing={fac},half=bottom,shape={shape}]"))
        CANDS.append(((m, 15), f"stairs[facing={fac},half=top,shape={shape}]"))
POP = [bin(i).count("1") for i in range(16)]


def best_block(lo, hi):
    best, bd = None, 99
    for (cl, ch), kind in CANDS:
        d = POP[lo ^ cl] + POP[hi ^ ch]
        if d < bd:
            best, bd = kind, d
    return best


# halves: anchor (x,y), pitch, allowed u range
HALVES = [
    ("bow", (3.0, 13.0), math.radians(26), (-4, L / 2 + 12)),
    ("stern", (-3.0, 9.0), math.radians(18), (-L / 2 - 2, 4)),
]
Z0 = -4  # keel at world z~-4, deck faces +z


def to_local(x, y, half):
    _, (ax, ay), th, _ = half
    dx, dy = x - ax, y - ay
    return dx * math.cos(th) + dy * math.sin(th), -dx * math.sin(th) + dy * math.cos(th)


def to_world(u, v, w, half):
    _, (ax, ay), th, _ = half
    return (round(ax + u * math.cos(th) - v * math.sin(th) - 0.5),
            round(ay + u * math.sin(th) + v * math.cos(th) - 0.5),
            round(Z0 + w - 0.5))


OFF = (0.25, 0.75)
SHIP_U = {}  # world pos -> local u (for damage)
for half in HALVES:
    name, _, th, (umin, umax) = half
    for x in range(-40, 42):
        for y in range(-14, 44):
            uc, _ = to_local(x + 0.5, y + 0.5, half)
            if not (umin - 1 <= uc <= umax + 1):
                continue
            locs = [[to_local(x + ox, y + oy, half) for ox in OFF] for oy in OFF]  # [ybit][xbit]
            for z in range(Z0 - 2, Z0 + 30):
                layers, fams, spar = [0, 0], {}, False
                for yb in (0, 1):
                    for zb in (0, 1):
                        w = z + OFF[zb] - Z0
                        for xb in (0, 1):
                            u, v = locs[yb][xb]
                            if not (umin <= u <= umax):
                                continue
                            f, is_spar = solid_at(u, v, w, name)
                            spar |= is_spar
                            if f:
                                layers[yb] |= 1 << (xb + 2 * zb)
                                fams[f] = fams.get(f, 0) + 1
                kind = best_block(*layers)
                if not kind:
                    continue
                fam = DO if fams.get(DO, 0) >= fams.get(SP, 0) else SP
                put((x, y, z), f"minecraft:{fam}_{kind}")
                if not spar:
                    SHIP_U[(x, y, z)] = uc
                else:
                    SHIP_U.pop((x, y, z), None)

# weathering: holes, splintered planks (random stairs), worst near the break
FACINGS = ("north", "south", "east", "west")
for p, uc in SHIP_U.items():
    b = M.get(p)
    if not b or "spruce" not in b:
        continue
    near = abs(uc) < 9
    r = h(*p, 1)
    if r < 0.05 + (0.3 if near else 0):
        del M[p]
    elif b.endswith("planks") and r < 0.12 + (0.25 if near else 0):
        fam = b[len("minecraft:"):-len("_planks")]
        M[p] = (f"minecraft:{fam}_stairs[facing={FACINGS[int(h(*p, 2) * 4)]},"
                f"half={'top' if h(*p, 3) < 0.5 else 'bottom'},shape=straight]")

# torn sail hanging from the yard (wool, in the plane just forward of the mast)
bow = HALVES[0]
Dm = deck_h(MAST_U)
for vi in range(-10, 11):
    v = vi / 2
    bottom = Dm + 4.5 + 3 * h(vi, 41)
    for wi in range(int(bottom * 2), int((Dm + 11.2) * 2)):
        w = wi / 2
        u = MAST_U + 1.6 + 1.2 * math.sin(math.pi * (w - Dm - 4.5) / 7)  # billow
        p = to_world(u, v, w, bow)
        if p not in M and h(*p, 42) > 0.22:
            put(p, "minecraft:white_wool" if h(*p, 43) > 0.3 else "minecraft:light_gray_wool")
# ---------------------------------------------------------------- iceberg
YB, YT = -16, 46


def berg_shape(y):
    t = (y - YB) / (YT - YB)
    cx = -0.22 * y + 0.004 * y * y * -1  # lean toward -x, curling at the top
    cz = 0.08 * y
    rx = 3.8 + 5.5 * (1 - t) ** 1.4 + 3.2 * math.exp(-((y - 13) / 7) ** 2)  # bulge where the hull is jammed
    if y < 0:
        rx += 2 + (-y) * 0.25
    if y > YT - 7:
        rx *= (YT - y) / 7
    return cx, cz, rx, rx * 1.35


def in_berg(x, y, z):
    if not (YB <= y <= YT):
        return False
    cx, cz, rx, rz = berg_shape(y)
    if rx <= 0.4:
        return False
    dx, dz = x + 0.5 - cx, z + 0.5 - cz
    a = math.atan2(dz, dx)
    n = (1 + 0.12 * math.sin(3 * a + y * 0.21) + 0.08 * math.sin(7 * a - y * 0.37)
         + 0.08 * math.sin(2 * a + 1.3) + (0.06 if (y // 5) % 2 else 0))  # faceting + ledges
    return (dx / (rx * n)) ** 2 + (dz / (rz * n)) ** 2 <= 1


BERG = set()
for y in range(YB, YT + 1):
    for x in range(-40, 30):
        for z in range(-30, 30):
            if in_berg(x, y, z):
                BERG.add((x, y, z))


def exposed(p):
    x, y, z = p
    return any(q not in BERG for q in ((x+1,y,z),(x-1,y,z),(x,y+1,z),(x,y-1,z),(x,y,z+1),(x,y,z-1)))


for p in BERG:
    x, y, z = p
    b = "minecraft:packed_ice"
    if exposed(p):
        r = h(x, y, z, 5)
        if y > 1 and (x, y + 1, z) not in BERG:
            b = "minecraft:snow_block" if r < 0.8 else "minecraft:packed_ice"
        elif h(x // 3, z // 3, 11) < 0.3 and r < 0.75:
            b = "minecraft:blue_ice"  # vertical veins
        elif r < 0.15:
            b = "minecraft:ice"
    put(p, b)

# loot: find a spot just above the lower hull wall inside a half
def loot_spot(u, w, half):
    x, _, z = to_world(u, 0, w, half)
    ys = [y for (px, y, pz) in M if px == x and pz == z]
    for y in range(min(ys), max(ys)):
        if (x, y, z) in M and (x, y + 1, z) not in M and (x, y + 2, z) not in M and y + 1 >= 0:
            return x, y + 1, z
    return None


LOOT = []
for u, w, half, block in [
    (15, 3, HALVES[0], 'minecraft:barrel[facing=up]{LootTable:"minecraft:chests/shipwreck_supply"}'),
    (11, 5, HALVES[0], 'minecraft:chest[facing=north]{LootTable:"minecraft:chests/shipwreck_supply"}'),
    (-18, 6, HALVES[1], 'minecraft:chest[facing=north]{LootTable:"minecraft:chests/shipwreck_treasure"}'),
    (-15, 4, HALVES[1], 'minecraft:chest[facing=north]{LootTable:"minecraft:chests/shipwreck_map"}'),
]:
    s = loot_spot(u, w, half)
    if s:
        put(s, block)
        LOOT.append(s)

# ---------------------------------------------------------------- dressing
for (x, y, z), b in list(M.items()):
    if y < 0 or (x, y + 1, z) in M:
        continue
    if ("snow_block" in b and h(x, z, 3) < 0.5) or b.endswith("planks") and h(x, y, z, 4) < 0.55:
        put((x, y + 1, z), f"minecraft:snow[layers={1 + int(h(x, y, z, 6) * 3)}]")
# icicles under overhangs (ship hull and berg)
for (x, y, z), b in list(M.items()):
    if y < 2 or "snow[" in b or "{" in b or "fence" in b or (x, y - 1, z) in M:
        continue
    if h(x, y, z, 8) < 0.12:
        for k in range(1, 2 + int(h(x, z, 9) * 3)):
            if (x, y - k, z) in M:
                break
            put((x, y - k, z), "minecraft:ice")

# waterlog non-full blocks below the waterline
for p, b in list(M.items()):
    if p[1] <= -1 and any(k in b for k in ("stairs", "slab", "trapdoor")) and "waterlogged" not in b:
        put(p, b[:-1] + ",waterlogged=true]")
    if p[1] <= -1 and "snow[" in b:
        del M[p]


# floating debris near the hull ends (waterlogged slabs/trapdoors at the waterline)
for i in range(40):
    x = rng.choice([rng.randint(-34, -14), rng.randint(14, 30)])
    z = rng.randint(-14, 16)
    if (x, -1, z) in M or (x, 0, z) in M:
        continue
    put((x, -1, z), rng.choice(["minecraft:spruce_slab[type=top,waterlogged=true]",
                                "minecraft:dark_oak_slab[type=top,waterlogged=true]",
                                "minecraft:spruce_trapdoor[half=top,open=false,waterlogged=true]"]))

# ---------------------------------------------------------------- emit (merge x-runs)
lines = [
    "# Iceberg-split shipwreck. Generated by config/paxi/datapacks/winterheart_structures/gen_wreck.py — edit that, not this.",
    "# Run while standing on the frozen ocean surface at sea level (feet at y=63).",
    "# Footprint ~ x-40..+40, y-16..+47, z-30..+30 around you. No undo: test on a copy.",
    "",
    "# background icebergs (vanilla features, random shape; placed at sea level automatically)",
    "place feature minecraft:iceberg_packed ~-55 ~ ~30",
    "place feature minecraft:iceberg_blue ~45 ~ ~-40",
    "place feature minecraft:iceberg_packed ~20 ~ ~55",
    "",
]
keys = sorted(M, key=lambda p: (p[1], p[2], p[0]))
i, cmds = 0, 0
while i < len(keys):
    x, y, z = keys[i]
    b = M[keys[i]]
    j = i
    while j + 1 < len(keys) and keys[j+1] == (keys[j][0] + 1, y, z) and M[keys[j+1]] == b:
        j += 1
    x2 = keys[j][0]
    lines.append(f"setblock ~{x} ~{y} ~{z} {b}" if x2 == x else f"fill ~{x} ~{y} ~{z} ~{x2} ~{y} ~{z} {b}")
    cmds += 1
    i = j + 1
lines = [l.replace("~0 ", "~ ").replace("~-0 ", "~ ") for l in lines]
open(OUT, "w").write("\n".join(lines) + "\n")
print(f"{len(M)} blocks, {cmds} commands, {len(LOOT)} loot containers")
