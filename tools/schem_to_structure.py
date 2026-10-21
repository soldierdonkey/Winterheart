#!/usr/bin/env python3
"""Convert a WorldEdit/Sponge .schem (v2) into a vanilla structure template for /place template.

    python3 tools/schem_to_structure.py config/worldedit/schematics/shipwreck.schem \
        kubejs/data/winterheart/structures/shipwreck.nbt --iceberg

    python3 tools/schem_to_structure.py config/worldedit/schematics/watchtower.schem \
        kubejs/data/winterheart/structures/beacon.nbt --drop minecraft:stone \
        --drop notreepunching:stone_loose_rock --drop minecraft:snow@2 --drop aquaculture:neptunium_block \
        --replace minecraft:iron_block=minecraft:obsidian --keep-inner-air

- Air and water are left out, so placing the template keeps whatever the world has there (air/ocean),
  while every other block overwrites the world.
- waterlogged=true is cleared: /place template re-waterlogs blocks that land in water and doesn't
  leave stray water sources where they land in air.
- The template is cropped to its blocks. The waterline (top layer of water, if any) is printed as a
  template-local Y for fixed_structures.js's `waterline`.

--drop ID[@MAXY] (repeatable) leaves out blocks of that id, optionally only at schematic Y <= MAXY: terrain
the copy caught, e.g. `--drop minecraft:stone --drop minecraft:snow@2`.

--replace FROM=TO (repeatable) swaps one block id for another, keeping block states that still apply.

--keep-inner-air keeps the air enclosed by the build (per layer) and up to 3 blocks of headroom above every
block, so placing it clears terrain/snow inside and on top of it without cutting a box into the hillside.

--iceberg (for the iceberg shipwreck): drops the ocean's flat surface ice sheet and stray floating ice
that the copy caught, and re-sculpts the berg below the waterline into a rounded keel (no flat
bottom, sheer sides or waterline shelf).
"""
import argparse
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_to_structure as nbt  # noqa: E402

ICE = ('minecraft:packed_ice', 'minecraft:blue_ice', 'minecraft:ice')


def read_schem(path):
    _, r = nbt.load(path)
    if 'Schematic' in r:
        sys.exit('Sponge v3 schematics are not supported; save with WorldEdit 7.2 (v2).')
    W, H, L = r['Width'][1], r['Height'][1], r['Length'][1]
    pal = {v: k for k, (_, v) in r['Palette'][1].items()}
    data, ids, i = r['BlockData'][1], [], 0
    while i < len(data):  # varints
        v = sh = 0
        while True:
            b = data[i]
            i += 1
            v |= (b & 0x7F) << sh
            sh += 7
            if not b & 0x80:
                break
        ids.append(v)
    blocks = {}
    for idx, v in enumerate(ids):
        blocks[(idx % W, idx // (W * L), (idx // W) % L)] = pal[v]
    bes = {}
    for be in r.get('BlockEntities', (9, (10, [])))[1][1]:
        tag = dict(be)
        pos = tuple(tag.pop('Pos')[1])
        tag['id'] = tag.pop('Id')
        if tag.get('ForgeCaps', (10, {1: 1}))[1] == {}:
            tag.pop('ForgeCaps')
        bes[pos] = tag
    return (W, H, L), blocks, bes, r.get('DataVersion', (3, 3465))


def is_water(b):
    return b.startswith('minecraft:water')


def waterline(size, blocks):
    W, H, L = size
    best = None
    for y in range(H):
        n = sum(1 for x in range(W) for z in range(L) if blocks[(x, y, z)] == 'minecraft:water[level=0]')
        if n > W * L * 0.3:
            best = y
    return best


def h(*a):
    n = hash(a) & 0xFFFFFFFF
    return ((n * 2654435761) & 0xFFFFFFFF) / 2**32


def vnoise(x, y, z, s):
    """Smooth value noise in [-1, 1] with feature size s."""
    fx, fy, fz = x / s, y / s, z / s
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = [t - math.floor(t) for t in (fx, fy, fz)]
    tx, ty, tz = [t * t * (3 - 2 * t) for t in (tx, ty, tz)]
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                acc += w * h(ix + dx, iy + dy, iz + dz, 99)
    return acc * 2 - 1


def reshape_iceberg(size, blocks, wl):
    W, H, L = size
    # 1. the ocean's own surface ice sheet / thin ice and loose floating ice: ice at or below the waterline
    #    that has water directly under it
    for (x, y, z), b in list(blocks.items()):
        if y <= wl and (b in ICE or 'thin_ice' in b) and y > 0 and is_water(blocks[(x, y - 1, z)]):
            blocks[(x, y, z)] = 'minecraft:water[level=0]'

    # 2. the berg's section just above the water (largest connected patch) is the seed for the keel
    above = wl + 1
    seed = {(x, z) for x in range(W) for z in range(L) if blocks[(x, above, z)] in ICE}
    comps, seen = [], set()
    for p in seed:
        if p in seen:
            continue
        stack, comp = [p], set()
        seen.add(p)
        while stack:
            cx, cz = stack.pop()
            comp.add((cx, cz))
            for q in ((cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)):
                if q in seed and q not in seen:
                    seen.add(q)
                    stack.append(q)
        comps.append(comp)
    section = max(comps, key=len)
    edge = [p for p in section if any(q not in section for q in ((p[0] + 1, p[1]), (p[0] - 1, p[1]), (p[0], p[1] + 1), (p[0], p[1] - 1)))]

    def sdist(x, z):  # signed distance to the waterline section (negative inside)
        d = min(math.hypot(x - ex, z - ez) for ex, ez in edge)
        return -d if (x, z) in section else d + 0.5

    sd = {(x, z): sdist(x, z) for x in range(W) for z in range(L)}

    # 3. drop the old underwater ice and grow the keel: flares out under the waterline, then rounds off
    for (x, y, z), b in list(blocks.items()):
        if y <= wl and b in ICE:
            blocks[(x, y, z)] = 'minecraft:water[level=0]'
    berg = set()
    for y in range(wl, -1, -1):
        d = above - y
        grow = 4.0 * math.sin(min(d / 7, 1) * math.pi / 2) - 0.04 * d * d
        for x in range(W):
            for z in range(L):
                n = 1.4 * vnoise(x, y, z, 6) + 0.6 * vnoise(x, y, z, 2.5)
                if sd[(x, z)] < grow + n and is_water(blocks[(x, y, z)]):
                    berg.add((x, y, z))
    for p in berg:
        x, y, z = p
        exposed = any(q not in berg and is_water(blocks.get(q, 'minecraft:water[level=0]'))
                      for q in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)))
        r = h(x, y, z, 5)
        if exposed and h(x // 3, z // 3, 11) < 0.3 and r < 0.75:
            b = 'minecraft:blue_ice'
        elif exposed and r < 0.15:
            b = 'minecraft:ice'
        else:
            b = 'minecraft:packed_ice'
        blocks[p] = b
    return len(berg)


def inner_air(blocks, keep, lo, hi):
    """Air enclosed by the build in each layer (not reachable from the layer's edge), plus headroom air
    within 3 blocks above any kept block."""
    out = set()
    for y in range(lo[1], hi[1] + 1):
        cells = {(x, z) for x in range(lo[0], hi[0] + 1) for z in range(lo[2], hi[2] + 1) if (x, y, z) not in keep}
        outside = {c for c in cells if c[0] in (lo[0], hi[0]) or c[1] in (lo[2], hi[2])}
        stack = list(outside)
        while stack:
            cx, cz = stack.pop()
            for q in ((cx + 1, cz), (cx - 1, cz), (cx, cz + 1), (cx, cz - 1)):
                if q in cells and q not in outside:
                    outside.add(q)
                    stack.append(q)
        for x, z in cells:
            if (x, z) not in outside or any((x, y - k, z) in keep for k in (1, 2, 3)):
                if blocks.get((x, y, z), 'minecraft:air') == 'minecraft:air':
                    out.add((x, y, z))
    return out


def parse_state(s):
    if '[' not in s:
        return s, {}
    name, rest = s.split('[', 1)
    return name, dict(kv.split('=', 1) for kv in rest.rstrip(']').split(','))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('schem')
    ap.add_argument('out')
    ap.add_argument('--iceberg', action='store_true', help='strip sea ice and re-sculpt the underwater berg')
    ap.add_argument('--drop', action='append', default=[], metavar='ID[@MAXY]', help='leave out these blocks (terrain)')
    ap.add_argument('--replace', action='append', default=[], metavar='FROM=TO', help='swap block ids')
    ap.add_argument('--keep-inner-air', action='store_true', help='keep enclosed air and headroom air')
    args = ap.parse_args()

    size, blocks, bes, dv = read_schem(args.schem)
    wl = waterline(size, blocks)
    if args.iceberg:
        if wl is None:
            sys.exit('--iceberg needs water in the schematic to find the waterline')
        n = reshape_iceberg(size, blocks, wl)
        print(f'reshaped underwater berg: {n} blocks')

    drops = []
    for d in args.drop:
        bid, _, maxy = d.partition('@')
        drops.append((bid, int(maxy) if maxy else None))
    dropped = 0
    for p, b in blocks.items():
        if any(b.split('[')[0] == bid and (maxy is None or p[1] <= maxy) for bid, maxy in drops):
            blocks[p] = 'minecraft:air'
            dropped += 1
    if drops:
        print(f'dropped {dropped} blocks')
    for r in args.replace:
        src, _, dst = r.partition('=')
        n = 0
        for p, b in blocks.items():
            if b.split('[')[0] == src:
                blocks[p] = dst + b[len(src):]
                n += 1
        print(f'replaced {n} {src} -> {dst}')

    keep = {p: b for p, b in blocks.items() if b != 'minecraft:air' and not is_water(b)}
    lo = [min(p[i] for p in keep) for i in range(3)]
    hi = [max(p[i] for p in keep) for i in range(3)]
    if args.keep_inner_air:
        air = inner_air(blocks, keep, lo, hi)
        keep.update((p, 'minecraft:air') for p in air)
        print(f'kept {len(air)} inner/headroom air blocks')

    palette, index, plain, with_nbt = [], {}, [], []
    for p, b in keep.items():
        name, props = parse_state(b)
        if props.get('waterlogged') == 'true':
            props['waterlogged'] = 'false'
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': (8, name)}
            if props:
                entry['Properties'] = (10, {k: (8, v) for k, v in sorted(props.items())})
            palette.append(entry)
        rel = [p[i] - lo[i] for i in range(3)]
        out = {'pos': (9, (3, rel)), 'state': (3, index[key])}
        if p in bes:
            out['nbt'] = (10, bes[p])
            with_nbt.append(out)
        else:
            plain.append(out)
    order = lambda b: (b['pos'][1][1][1], b['pos'][1][1][2], b['pos'][1][1][0])
    tsize = [hi[i] - lo[i] + 1 for i in range(3)]
    root = {
        'size': (9, (3, tsize)),
        'entities': (9, (10, [])),
        'blocks': (9, (10, sorted(plain, key=order) + sorted(with_nbt, key=order))),
        'palette': (9, (10, palette)),
        'DataVersion': dv,
    }
    nbt.save(args.out, (10, root))
    print(f'{args.out}: size {tsize}, {len(plain) + len(with_nbt)} blocks ({len(with_nbt)} with NBT), palette {len(palette)}')
    print(f'schematic y {lo[1]} is template y 0')
    if wl is not None:
        print(f'waterline (top water layer): template y = {wl - lo[1]}')


if __name__ == '__main__':
    main()
