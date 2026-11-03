"""Generate an mcfunction: a round ~107-block stone signal tower with an outside spiral stair and an open
beacon platform (3x3 iron + beacon) ringed by four pillars.

    python3 gen_beacon.py data/winterheart/functions/structures/beacon.mcfunction

Coordinates are relative to the executor: run it standing where the tower's centre should be, feet on the
ground. The foundation reaches 10 blocks down to sit on uneven summits. Stone bricks + cobblestone (plus mossy
and cracked variants); curves come out as stairs/slabs by sampling each block in 2x2x2 parts.
"""
import math, sys

OUT = sys.argv[1]
M = {}
TAU = 2 * math.pi


def h(*a):  # deterministic hash noise in [0,1)
    n = hash(a) & 0xFFFFFFFF
    return ((n * 2654435761) & 0xFFFFFFFF) / 2**32


def vnoise(x, y, z, s):  # smooth value noise in [-1, 1]
    fx, fy, fz = x / s, y / s, z / s
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    t = [v - math.floor(v) for v in (fx, fy, fz)]
    t = [v * v * (3 - 2 * v) for v in t]
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (t[0] if dx else 1 - t[0]) * (t[1] if dy else 1 - t[1]) * (t[2] if dz else 1 - t[2])
                acc += w * h(ix + dx, iy + dy, iz + dz, 7)
    return acc * 2 - 1


def adist(a, b):  # unsigned angular distance
    d = (a - b) % TAU
    return min(d, TAU - d)


# ---------------------------------------------------------------- dimensions
FOUND = -10          # foundation bottom
FLOOR_TOP = 77       # top of the floor inside the shaft where the outer stair arrives
PLAT_Y = 88          # platform blocks occupy PLAT_Y .. PLAT_Y+1; walking surface PLAT_Y+2
PLAT_R = 9.6
PILLAR_TOP = 101.5   # pillars full width up to here, then cone caps
CAP_TOP = 107
BANDS = (8, 30, 54, 76)
RIBS = 8
WALL = 1.7           # shell thickness

K_OUT = 4.9                       # outer stair rise per radian (gentle enough on the inner edge to walk)
TH_END = (FLOOR_TOP - 0.0) / K_OUT  # stair runs theta 0 .. TH_END, surface 0 .. 77
DOOR_TH = TH_END % TAU
K_IN = 1.97                       # inner stair rise per radian
IN_TH0 = DOOR_TH + 0.7            # starts beside the doorway, turns the same way
IN_RISE = PLAT_Y + 2 - FLOOR_TOP  # 77 -> 90
IN_TH_END = IN_TH0 + IN_RISE / K_IN
HATCH = 1.8                       # radians of the inner stair left open through the platform
CRUMBLE = [(3.0, 3.5), (7.5, 7.9), (12.0, 12.5)]  # outer-stair arcs whose outer edge has fallen away


def R(y):  # shaft outer radius
    if y < 0:
        return 11.0
    if y < 8:
        return 11.0 - 3.5 * (y / 8)
    return 7.5 - 1.8 * (y - 8) / 78


def stair_s(th, yc):
    """Outer stair surface height under world angle th, on the turn closest to yc (None if no stair)."""
    best = None
    for n in range(3):
        t = (th % TAU) + TAU * n
        if t > TH_END + 0.35:
            break
        s = min(K_OUT * t, FLOOR_TOP)
        if best is None or abs(s - yc) < abs(best[0] - yc):
            best = (s, t)
    return best


def inner_s(th):
    t = (th - IN_TH0) % TAU + IN_TH0
    if t > IN_TH_END:
        return None, t
    return FLOOR_TOP + K_IN * (t - IN_TH0), t


def solid_at(x, y, z):
    """Material family at a sample point, or None."""
    d = math.hypot(x, z)
    th = math.atan2(z, x) % TAU
    r = R(y)

    # inner stair (77 -> 90, its last steps rising through the hatch) and its newel post, which also
    # carries the beacon's iron base
    if FLOOR_TOP <= y < PLAT_Y + 2:
        if d < 1.5:
            return "stone_brick"
        s, t = inner_s(th)
        if s is not None and 1.5 <= d <= r - WALL + 0.2 and s - 1.0 <= y <= s:
            return "stone_brick"

    # outer stair, its brackets and railing kerb
    if y < FLOOR_TOP + 0.5:
        hit = stair_s(th, y)
        if hit:
            s, t = hit
            rs = R(s)
            outer = rs + 3.3
            if any(a <= t <= b for a, b in CRUMBLE):
                outer = rs + 1.9
            if rs - 0.6 <= d <= outer and s - 1.6 <= y <= s:
                return "stone_brick" if y > s - 0.6 else "cobblestone"
            # corbel bracket every ~4 blocks of arc, deeper against the wall
            near = min(adist(th, b * 0.45) for b in range(14)) * d  # every ~4 blocks of arc
            if near < 0.55 and rs <= d <= rs + 1.9:
                bottom = s - 1.6 - 2.6 * (1 - (d - rs) / 1.9)
                if bottom <= y < s - 1.6:
                    return "cobblestone"

    # platform with the inner stair's hatch left open
    if PLAT_Y <= y < PLAT_Y + 2 and d <= PLAT_R:
        s, t = inner_s(th)
        if s is not None and t > IN_TH_END - HATCH and 1.5 <= d <= 4.8:
            return None
        return "stone_brick"

    # four round pillars with cone caps, tied by a crown ring
    if PLAT_Y + 2 <= y < CAP_TOP:
        for k in range(4):
            a = math.pi / 4 + k * math.pi / 2
            q = math.hypot(x - 8.0 * math.cos(a), z - 8.0 * math.sin(a))
            if y < PLAT_Y + 3:
                pr = 1.9  # plinth
            elif y < PILLAR_TOP:
                pr = 1.45
            else:
                pr = 1.45 * (CAP_TOP - y) / (CAP_TOP - PILLAR_TOP)
            if q <= pr:
                return "chiseled" if 96 <= y < 97 else "stone_brick"
        if PILLAR_TOP - 1.5 <= y < PILLAR_TOP and 7.4 <= d <= 8.6:
            return "stone_brick"
        return None

    if y < FOUND or y >= PLAT_Y:
        return None

    # doorway where the outer stair arrives (arched)
    if FLOOR_TOP <= y < FLOOR_TOP + 4 and r - WALL - 0.2 < d <= r + 0.8:
        off = adist(th, DOOR_TH) * d
        if off < 1.0 and y < FLOOR_TOP + 3.4 - off * off:
            return None

    # gallery: corbel brackets flaring out under a solid ring that carries the platform
    if 78 <= y < PLAT_Y:
        rg = r + (PLAT_R - 0.1 - r) * ((y - 78) / 10) ** 1.6
        bracket = adist(th, round(th / (TAU / 16)) * (TAU / 16)) * d < 0.6
        if y >= 85 or bracket:
            if r - WALL < d <= rg:
                return "stone_brick"
        elif r - WALL < d <= r:
            return "stone_brick"
        return None

    rout = r
    on_stair = y < FLOOR_TOP + 4 and (lambda s: s is not None and s[0] - 2 <= y <= s[0] + 3.5)(stair_s(th, y))
    if on_stair:
        pass  # ribs/bands stop where the stair passes, so they don't trip the climb
    elif any(b <= y < b + 1 for b in BANDS):
        rout += 0.5
    elif 0 <= y < 76 and adist(th, round(th / (TAU / RIBS)) * (TAU / RIBS)) * d < 0.75:
        rout += 0.55

    floor = y < 1 or FLOOR_TOP - 1 <= y < FLOOR_TOP
    if d <= rout and (floor or d > r - WALL):
        # arrow slits, kept clear of the outer stair
        if 12 <= y < 72 and d > r - WALL and not floor:
            lvl = int((y - 12) // 11)
            if (y - 12) % 11 < 3:
                for j in range(3):
                    a = (lvl * 1.3 + j * TAU / 3) % TAU
                    if adist(th, a) * d < 0.5:
                        s = stair_s(a, y)
                        if not s or abs(s[0] - y) > 4:
                            return None
        if y < 9 or vnoise(x, y, z, 5) > 0.3:
            fam = "cobblestone"
        else:
            fam = "stone_brick"
        if vnoise(x + 50, y, z, 4) > 0.62 and y < 50:
            fam = "mossy_" + fam
        return fam
    return None


# ---------------------------------------------------------------- 2x2x2 sampling -> block shapes
STAIR_TOP = {  # shape -> facing -> quadrants of the raised step; bits 1=(x0,z0) 2=(x1,z0) 4=(x0,z1) 8=(x1,z1)
    "straight": {"south": 12, "west": 5, "north": 3, "east": 10},
    "outer_left": {"south": 8, "west": 4, "north": 1, "east": 2},
    "outer_right": {"south": 4, "west": 1, "north": 2, "east": 8},
    "inner_left": {"south": 14, "west": 13, "north": 7, "east": 11},
    "inner_right": {"south": 13, "west": 7, "north": 11, "east": 14},
}
CANDS = [((0, 0), None), ((15, 15), "full"), ((15, 0), "slab[type=bottom]"), ((0, 15), "slab[type=top]")]
for shape, dd in STAIR_TOP.items():
    for fac, m in dd.items():
        CANDS.append(((15, m), f"stairs[facing={fac},half=bottom,shape={shape}]"))
        CANDS.append(((m, 15), f"stairs[facing={fac},half=top,shape={shape}]"))
POP = [bin(i).count("1") for i in range(16)]


def best_block(lo, hi):
    best, bd = None, 99
    for (cl, ch), kind in CANDS:
        dist = POP[lo ^ cl] + POP[hi ^ ch]
        if dist < bd:
            best, bd = kind, dist
    return best


FULL = {"stone_brick": "stone_bricks", "mossy_stone_brick": "mossy_stone_bricks", "cobblestone": "cobblestone",
        "mossy_cobblestone": "mossy_cobblestone", "chiseled": "chiseled_stone_bricks"}


def block_name(fam, kind, p):
    if kind == "full":
        if fam == "stone_brick" and h(*p, 9) < 0.1:
            return "minecraft:cracked_stone_bricks"
        return "minecraft:" + FULL[fam]
    if fam == "chiseled":
        fam = "stone_brick"
    return f"minecraft:{fam}_{kind}"


OFF = (0.25, 0.75)
AIR = set()
for x in range(-13, 14):
    for z in range(-13, 14):
        for y in range(FOUND, CAP_TOP + 1):
            layers, fams = [0, 0], {}
            for yb in (0, 1):
                for zb in (0, 1):
                    for xb in (0, 1):
                        f = solid_at(x + OFF[xb], y + OFF[yb], z + OFF[zb])
                        if f:
                            layers[yb] |= 1 << (xb + 2 * zb)
                            fams[f] = fams.get(f, 0) + 1
            kind = best_block(*layers)
            if kind:
                fam = max(fams, key=lambda f: (fams[f], f))
                M[(x, y, z)] = block_name(fam, kind, (x, y, z))
            elif 1 <= y < PLAT_Y and math.hypot(x + 0.5, z + 0.5) < R(y) - WALL - 0.3:
                AIR.add((x, y, z))  # clear terrain/snow inside the shaft

# ---------------------------------------------------------------- explicit details
# beacon: 3x3 iron set into the platform, beacon on top, nothing above it
for x in (-1, 0, 1):
    for z in (-1, 0, 1):
        M[(x, PLAT_Y + 1, z)] = "minecraft:iron_block"
M[(0, PLAT_Y + 2, 0)] = "minecraft:beacon"

# parapet: low wall ring between the pillars
for i in range(72):
    a = i / 72 * TAU
    p = (round(9.0 * math.cos(a) - 0.5), PLAT_Y + 2, round(9.0 * math.sin(a) - 0.5))
    if p not in M and math.hypot(p[0] + 0.5, p[2] + 0.5) <= PLAT_R:
        M[p] = "minecraft:stone_brick_wall"

# railing posts along the outer stair (some lost to weather)
for i in range(int(TH_END / 0.32)):
    t = i * 0.32
    if any(a <= t <= b for a, b in CRUMBLE) or h(i, 3) < 0.35:
        continue
    s = min(K_OUT * t, FLOOR_TOP)
    rr = R(s) + 2.9
    x, z = round(rr * math.cos(t) - 0.5), round(rr * math.sin(t) - 0.5)
    ys = [y for y in range(int(s) - 2, int(s) + 2) if (x, y, z) in M]
    if ys and (x, max(ys) + 1, z) not in M:
        M[(x, max(ys) + 1, z)] = "minecraft:cobblestone_wall"

# snow on exposed full-block tops (not the platform floor, which the player walks)
for (x, y, z), b in list(M.items()):
    full = not any(k in b for k in ("stairs", "slab", "wall", "beacon", "iron"))
    inside = math.hypot(x + 0.5, z + 0.5) < R(y) - 0.5 and y < PLAT_Y
    if full and not inside and (x, y + 1, z) not in M and y + 1 != PLAT_Y + 2 and h(x, y, z, 4) < 0.35:
        M[(x, y + 1, z)] = f"minecraft:snow[layers={1 + int(h(x, y, z, 5) * 2)}]"
for p in AIR:
    M.setdefault(p, "minecraft:air")

# ---------------------------------------------------------------- emit (merge x-runs, bottom-up)
lines = [
    "# Summit beacon. Generated by config/paxi/datapacks/winterheart_structures/gen_beacon.py.",
    "# Run standing where the tower's centre goes, feet on the ground. Footprint ~27x27, y -10..+107. No undo.",
    "",
]
keys = sorted(M, key=lambda p: (p[1], p[2], p[0]))
i, cmds = 0, 0
while i < len(keys):
    x, y, z = keys[i]
    b = M[keys[i]]
    j = i
    while j + 1 < len(keys) and keys[j + 1] == (keys[j][0] + 1, y, z) and M[keys[j + 1]] == b:
        j += 1
    x2 = keys[j][0]
    lines.append(f"setblock ~{x} ~{y} ~{z} {b}" if x2 == x else f"fill ~{x} ~{y} ~{z} ~{x2} ~{y} ~{z} {b}")
    cmds += 1
    i = j + 1
lines = [l.replace("~0 ", "~ ") for l in lines]
open(OUT, "w").write("\n".join(lines) + "\n")
print(f"{sum(1 for b in M.values() if b != 'minecraft:air')} blocks, {len(AIR)} cleared, {cmds} commands")
