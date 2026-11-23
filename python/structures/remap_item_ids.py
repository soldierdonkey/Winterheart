#!/usr/bin/env python3
"""Rewrite item/namespace IDs inside structure NBT files (gzip NBT: .nbt, .schem) and SNBT text files, in place.

    python3 python/structures/remap_item_ids.py FILE... [--map OLD=NEW ...]

Every string value that contains an OLD id is rewritten (so `{id:"kubejs:lost_page"}` and a JSON text component that
mentions it both change). Root names and everything else are kept. With no --map, the kubejs: items that moved to the
core mod (docs/todo/general-kubejs-removal.md, step 2) are used.

Needed because a world's registry remap (winterheart IdRemap) does not touch item ids stored inside structure
templates: an unknown id in a template loads as an empty stack.
"""
import argparse
import gzip
import io
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import nbt_to_structure as nbt  # noqa: E402

DEFAULT_MAP = {
    f"kubejs:{n}": f"winterheart:{n}"
    for n in ("saltpeter", "crude_nitrate_bottle", "potassium_nitrate_bottle", "guide_book", "lost_page")
}


def rewrite_value(tag, value, mapping, counter):
    if tag == 8:
        for old, new in mapping.items():
            if old in value:
                value = value.replace(old, new)
                counter[0] += 1
        return value
    if tag == 9:
        et, items = value
        return (et, [rewrite_value(et, v, mapping, counter) for v in items])
    if tag == 10:
        return {k: (tt, rewrite_value(tt, vv, mapping, counter)) for k, (tt, vv) in value.items()}
    return value


def rewrite_nbt(path, mapping):
    raw = path.read_bytes()
    data = gzip.decompress(raw) if raw[:2] == b"\x1f\x8b" else raw
    f = io.BytesIO(data)
    tag = f.read(1)[0]
    root_name = f.read(struct.unpack(">H", f.read(2))[0])
    value = nbt._read(f, tag)
    counter = [0]
    value = rewrite_value(tag, value, mapping, counter)
    if counter[0]:
        o = io.BytesIO()
        o.write(bytes([tag]) + struct.pack(">H", len(root_name)) + root_name)
        nbt._write(o, tag, value)
        out = o.getvalue()
        path.write_bytes(gzip.compress(out, mtime=0) if raw[:2] == b"\x1f\x8b" else out)
    return counter[0]


def rewrite_text(path, mapping):
    text = path.read_text()
    n = 0
    for old, new in mapping.items():
        n += text.count(old)
        text = text.replace(old, new)
    if n:
        path.write_text(text)
    return n


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("files", nargs="+", type=Path)
    ap.add_argument("--map", action="append", default=[], metavar="OLD=NEW")
    args = ap.parse_args()
    mapping = dict(m.split("=", 1) for m in args.map) or DEFAULT_MAP
    for path in args.files:
        n = rewrite_text(path, mapping) if path.suffix == ".snbt" else rewrite_nbt(path, mapping)
        print(f"{path}: {n} replacement(s)")


if __name__ == "__main__":
    main()
