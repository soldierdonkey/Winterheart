#!/usr/bin/env python3
"""Convert a schematic into a vanilla structure template that /place template, structure blocks and jigsaw
pools can load. Reads mod-exported .nbt (root "SchematicBlocks", block entities with "Id"/"Pos") and WorldEdit
Sponge .schem (v2 and v3).

    python3 python/structures/nbt_to_structure.py config/worldedit/schematics/watchtower.nbt config/paxi/datapacks/winterheart_structures/data/winterheart/structures/watchtower.nbt
    python3 python/structures/nbt_to_structure.py config/worldedit/schematics/shipwreck.schem config/paxi/datapacks/winterheart_structures/data/winterheart/structures/shipwreck.nbt \
        --keep-world-below 13 --keep-world-block minecraft:water --keep-world-block minecraft:sand \
        --keep-world-block minecraft:gravel --keep-world-block minecraft:dirt --keep-world-block minecraft:stone \
        --keep-world-block minecraft:sandstone --keep-world-block minecraft:andesite \
        --keep-world-block immersive_weathering:mossy_stone

Fixes: empty root name, deduplicated palette, block-entity "Id" -> "id" with "Pos" and empty "ForgeCaps"
removed, an "entities" list, and vanilla block order (plain blocks bottom-up, block entities last).

--keep-world-below Y leaves out the --keep-world-block blocks (default: water) at template Y and below, e.g. the
sea and seabed a build was made in, so placing it keeps whatever water or terrain the world already has there.
Everything else in the template, air included, still overwrites the world.

--merge-nbt X,Y,Z FILE merges an SNBT compound (e.g. copied from a `/data get block` line in latest.log) into the
block entity at template position X,Y,Z; its x/y/z keys are ignored. Repeatable.
"""
import argparse
import gzip
import io
import struct
import sys

# ---- minimal NBT codec: values are (tag_id, payload); a list payload is (elem_id, [payloads]) ----

def _read(f, t):
    if t == 1: return struct.unpack('>b', f.read(1))[0]
    if t == 2: return struct.unpack('>h', f.read(2))[0]
    if t == 3: return struct.unpack('>i', f.read(4))[0]
    if t == 4: return struct.unpack('>q', f.read(8))[0]
    if t == 5: return struct.unpack('>f', f.read(4))[0]
    if t == 6: return struct.unpack('>d', f.read(8))[0]
    if t == 7: return f.read(struct.unpack('>i', f.read(4))[0])
    if t == 8: return f.read(struct.unpack('>H', f.read(2))[0]).decode('utf-8')
    if t == 9:
        et = f.read(1)[0]
        n = struct.unpack('>i', f.read(4))[0]
        return (et, [_read(f, et) for _ in range(n)])
    if t == 10:
        d = {}
        while (tt := f.read(1)[0]) != 0:
            name = f.read(struct.unpack('>H', f.read(2))[0]).decode('utf-8')
            d[name] = (tt, _read(f, tt))
        return d
    if t == 11:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%di' % n, f.read(4 * n)))
    if t == 12:
        n = struct.unpack('>i', f.read(4))[0]
        return list(struct.unpack('>%dq' % n, f.read(8 * n)))
    raise ValueError(f'unknown tag {t}')


def _write(o, t, v):
    if t == 1: o.write(struct.pack('>b', v))
    elif t == 2: o.write(struct.pack('>h', v))
    elif t == 3: o.write(struct.pack('>i', v))
    elif t == 4: o.write(struct.pack('>q', v))
    elif t == 5: o.write(struct.pack('>f', v))
    elif t == 6: o.write(struct.pack('>d', v))
    elif t == 7: o.write(struct.pack('>i', len(v)) + bytes(v))
    elif t == 8:
        b = v.encode('utf-8')
        o.write(struct.pack('>H', len(b)) + b)
    elif t == 9:
        et, items = v
        o.write(bytes([et if items else 0]) + struct.pack('>i', len(items)))
        for it in items:
            _write(o, et, it)
    elif t == 10:
        for k, (tt, vv) in v.items():
            b = k.encode('utf-8')
            o.write(bytes([tt]) + struct.pack('>H', len(b)) + b)
            _write(o, tt, vv)
        o.write(b'\x00')
    elif t == 11: o.write(struct.pack('>i', len(v)) + struct.pack('>%di' % len(v), *v))
    elif t == 12: o.write(struct.pack('>i', len(v)) + struct.pack('>%dq' % len(v), *v))


def load(path):
    data = open(path, 'rb').read()
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    f = io.BytesIO(data)
    t = f.read(1)[0]
    f.read(struct.unpack('>H', f.read(2))[0])  # root name
    return (t, _read(f, t))


def save(path, root):
    o = io.BytesIO()
    o.write(bytes([root[0]]) + struct.pack('>H', 0))  # vanilla templates have an empty root name
    _write(o, *root)
    open(path, 'wb').write(gzip.compress(o.getvalue(), mtime=0))  # mtime 0: re-exports are byte-identical


def state_key(state):
    props = state.get('Properties', (10, {}))[1]
    return (state['Name'][1], tuple(sorted((k, v[1]) for k, v in props.items())))


# ---- SNBT (the text form /data get prints) -> (tag_id, payload) ----

class _Snbt:
    SUFFIX = {'b': 1, 's': 2, 'l': 4, 'f': 5, 'd': 6}
    BARE = set('0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-.+')

    def __init__(self, text):
        self.t, self.i = text, 0

    def ws(self):
        while self.i < len(self.t) and self.t[self.i].isspace():
            self.i += 1

    def peek(self):
        self.ws()
        return self.t[self.i]

    def expect(self, c):
        if self.peek() != c:
            raise ValueError(f'SNBT: expected {c!r} at {self.i}: {self.t[self.i:self.i + 30]!r}')
        self.i += 1

    def string(self):
        q = self.t[self.i]
        self.i += 1
        out = []
        while self.t[self.i] != q:
            if self.t[self.i] == '\\':
                self.i += 1
            out.append(self.t[self.i])
            self.i += 1
        self.i += 1
        return ''.join(out)

    def key(self):
        if self.peek() in '"\'':
            return self.string()
        start = self.i
        while self.t[self.i] in self.BARE:
            self.i += 1
        return self.t[start:self.i]

    def value(self):
        c = self.peek()
        if c == '{':
            self.i += 1
            d = {}
            while self.peek() != '}':
                k = self.key()
                self.expect(':')
                d[k] = self.value()
                if self.peek() == ',':
                    self.i += 1
            self.i += 1
            return (10, d)
        if c == '[':
            self.i += 1
            if self.t[self.i:self.i + 2] in ('B;', 'I;', 'L;'):
                kind = self.t[self.i]
                self.i += 2
                items = []
                while self.peek() != ']':
                    items.append(self.value()[1])
                    if self.peek() == ',':
                        self.i += 1
                self.i += 1
                return ({'B': 7, 'I': 11, 'L': 12}[kind], bytes(v & 0xff for v in items) if kind == 'B' else items)
            items = []
            while self.peek() != ']':
                items.append(self.value())
                if self.peek() == ',':
                    self.i += 1
            self.i += 1
            et = items[0][0] if items else 0
            return (9, (et, [v for _, v in items]))
        if c in '"\'':
            return (8, self.string())
        raw = self.key()
        low = raw.lower()
        if low in ('true', 'false'):
            return (1, int(low == 'true'))
        try:
            if low[-1:] in self.SUFFIX and low[:-1].lstrip('+-').replace('.', '', 1).isdigit():
                t = self.SUFFIX[low[-1]]
                return (t, float(raw[:-1]) if t in (5, 6) else int(raw[:-1]))
            if '.' in raw or 'e' in low:
                return (6, float(raw))
            return (3, int(raw))
        except ValueError:
            return (8, raw)


def parse_snbt(text):
    p = _Snbt(text.strip())
    v = p.value()
    p.ws()
    if p.i != len(p.t):
        raise ValueError(f'SNBT: trailing text at {p.i}')
    return v


def _varints(data):
    vals, i = [], 0
    while i < len(data):
        v = shift = 0
        while True:
            b = data[i] & 0xff
            i += 1
            v |= (b & 0x7f) << shift
            shift += 7
            if not b & 0x80:
                break
        vals.append(v)
    return vals


def _parse_state(s):
    """'minecraft:stairs[facing=west,half=top]' -> palette compound."""
    name, _, props = s.partition('[')
    entry = {'Name': (8, name)}
    if props:
        entry['Properties'] = (10, {k: (8, v) for k, v in (kv.split('=', 1) for kv in props.rstrip(']').split(','))})
    return entry


def schem_to_root(s):
    """Sponge .schem (v2, or v3 with its "Schematic" wrapper) -> the SchematicBlocks-style root convert() reads."""
    if 'Schematic' in s:
        s = s['Schematic'][1]
    blocks_tag = s['Blocks'][1] if 'Blocks' in s else s
    w, h, l = s['Width'][1], s['Height'][1], s['Length'][1]
    pal = {v[1]: k for k, v in blocks_tag['Palette'][1].items()}
    data = blocks_tag['Data'][1] if 'Data' in blocks_tag else blocks_tag['BlockData'][1]
    block_entities = {}
    for be in blocks_tag.get('BlockEntities', (9, (10, [])))[1][1]:
        tag = dict(be.pop('Data')[1]) if 'Data' in be else {}
        tag.update(be)
        x, y, z = tag['Pos'][1]
        block_entities[(x, y, z)] = (10, tag)
    palette = [_parse_state(pal[i]) for i in range(max(pal) + 1)]
    blocks = []
    for idx, v in enumerate(_varints(data)):
        y, rest = divmod(idx, w * l)
        z, x = divmod(rest, w)
        b = {'pos': (9, (3, [x, y, z])), 'state': (3, v)}
        if (x, y, z) in block_entities:
            b['nbt'] = block_entities[(x, y, z)]
        blocks.append(b)
    return {
        'size': (9, (3, [w, h, l])),
        'palette': (9, (10, palette)),
        'blocks': (9, (10, blocks)),
        'DataVersion': s.get('DataVersion', (3, 3465)),
    }


def convert(src, dst, keep_world_below=None, keep_world_blocks=('minecraft:water',), merge_nbt=()):
    _, root = load(src)
    if 'BlockData' in root or 'Blocks' in root or 'Schematic' in root:
        root = schem_to_root(root)
    old_palette = root['palette'][1][1]

    palette, remap, index = [], {}, {}
    for i, st in enumerate(old_palette):
        key = state_key(st)
        if key not in index:
            index[key] = len(palette)
            entry = {'Name': st['Name']}
            if st.get('Properties', (10, {}))[1]:
                entry['Properties'] = st['Properties']
            palette.append(entry)
        remap[i] = index[key]

    plain, with_nbt, dropped = [], [], 0
    for b in root['blocks'][1][1]:
        if keep_world_below is not None and b['pos'][1][1][1] <= keep_world_below \
                and old_palette[b['state'][1]]['Name'][1] in keep_world_blocks:
            dropped += 1
            continue
        out = {'pos': b['pos'], 'state': (3, remap[b['state'][1]])}
        if 'nbt' in b:
            tag = dict(b['nbt'][1])
            if 'Id' in tag and 'id' not in tag:
                tag['id'] = tag.pop('Id')
            for k in ('Pos', 'x', 'y', 'z'):
                tag.pop(k, None)
            if tag.get('ForgeCaps', (10, {1: 1}))[1] == {}:
                tag.pop('ForgeCaps')
            out['nbt'] = (10, tag)
            with_nbt.append(out)
        else:
            plain.append(out)
    for pos, tag in merge_nbt:
        target = next((b for b in with_nbt if tuple(b['pos'][1][1]) == pos), None)
        if target is None:
            raise SystemExit(f'--merge-nbt: no block entity at template position {pos}')
        extra = {k: v for k, v in tag[1].items() if k not in ('x', 'y', 'z')}
        target['nbt'][1].update(extra)
        print(f'merged {sorted(extra)} into the {target["nbt"][1]["id"][1]} at {list(pos)}')
    # bottom-up so supports (floors, posts) are placed before what hangs or sits on them
    order = lambda b: (b['pos'][1][1][1], b['pos'][1][1][2], b['pos'][1][1][0])
    blocks = sorted(plain, key=order) + sorted(with_nbt, key=order)

    out = {
        'size': root['size'],
        'entities': root.get('entities', (9, (10, []))),
        'blocks': (9, (10, blocks)),
        'palette': (9, (10, palette)),
        'DataVersion': root.get('DataVersion', (3, 3465)),
    }
    save(dst, (10, out))
    size = [v for v in root['size'][1][1]]
    print(f'{dst}: size {size}, {len(blocks)} blocks ({len(with_nbt)} with NBT), '
          f'palette {len(old_palette)} -> {len(palette)}'
          + (f', {dropped} blocks left to the world' if dropped else ''))


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('src')
    ap.add_argument('dst')
    ap.add_argument('--keep-world-below', type=int, metavar='Y',
                    help='leave out --keep-world-block blocks at template Y and below so the world keeps its own')
    ap.add_argument('--keep-world-block', action='append', metavar='ID',
                    help='block id left out by --keep-world-below (repeatable; default minecraft:water)')
    ap.add_argument('--merge-nbt', nargs=2, action='append', default=[], metavar=('X,Y,Z', 'SNBT_FILE'),
                    help='merge an SNBT compound into the block entity at template position X,Y,Z (repeatable)')
    args = ap.parse_args()
    merges = []
    for pos, path in args.merge_nbt:
        with open(path) as f:
            merges.append((tuple(int(v) for v in pos.split(',')), parse_snbt(f.read())))
    convert(args.src, args.dst, args.keep_world_below, tuple(args.keep_world_block or ['minecraft:water']), merges)
