#!/usr/bin/env python3
"""Convert a mod-exported .nbt schematic (root "SchematicBlocks", block entities with "Id"/"Pos") into a
vanilla structure template that /place template, structure blocks and jigsaw pools can load.

    python3 tools/nbt_to_structure.py config/worldedit/schematics/watchtower.nbt kubejs/data/winterheart/structures/watchtower.nbt

Fixes: empty root name, deduplicated palette, block-entity "Id" -> "id" with "Pos" and empty "ForgeCaps"
removed, an "entities" list, and vanilla block order (plain blocks bottom-up, block entities last).
"""
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
    open(path, 'wb').write(gzip.compress(o.getvalue()))


def state_key(state):
    props = state.get('Properties', (10, {}))[1]
    return (state['Name'][1], tuple(sorted((k, v[1]) for k, v in props.items())))


def convert(src, dst):
    _, root = load(src)
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

    plain, with_nbt = [], []
    for b in root['blocks'][1][1]:
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
          f'palette {len(old_palette)} -> {len(palette)}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    convert(sys.argv[1], sys.argv[2])
