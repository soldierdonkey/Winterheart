#!/usr/bin/env python3
"""List blocks whose saved state must survive /place template (connected blocks the game recomputes from
their neighbours after pasting, e.g. Macaw's bridge corners), so fixed_structures.js can write them back.

    python3 tools/structure_fixups.py kubejs/data/winterheart/structures/watchtower.nbt [--match mcwbridges:]

Writes <template>.fixups.json next to the template: template-local positions and full block states.
--match (repeatable) selects block ids by substring; default mcwbridges:.
"""
import argparse
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_to_structure as nbt  # noqa: E402


def state_string(entry):
    props = entry.get('Properties', (10, {}))[1]
    name = entry['Name'][1]
    if not props:
        return name
    return name + '[' + ','.join(f'{k}={v[1]}' for k, v in sorted(props.items())) + ']'


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('template')
    ap.add_argument('--match', action='append', help='block id substring to keep (default mcwbridges:)')
    args = ap.parse_args()
    matches = args.match or ['mcwbridges:']

    _, root = nbt.load(args.template)
    palette = root['palette'][1][1]
    blocks = []
    for b in root['blocks'][1][1]:
        entry = palette[b['state'][1]]
        if any(m in entry['Name'][1] for m in matches):
            blocks.append({'pos': list(b['pos'][1][1]), 'state': state_string(entry)})
    blocks.sort(key=lambda b: (b['pos'][1], b['pos'][2], b['pos'][0]))

    out = os.path.splitext(args.template)[0] + '.fixups.json'
    with open(out, 'w') as f:
        json.dump({'match': matches, 'blocks': blocks}, f, indent=2)
        f.write('\n')
    print(f'{out}: {len(blocks)} blocks')
    for b in blocks:
        print(f"  {b['pos']}  {b['state']}")


if __name__ == '__main__':
    main()
