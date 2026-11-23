#!/bin/sh
# Re-export every placed structure template from its WorldEdit save. Run from the instance root:
#   sh python/structures/export_structures.sh
set -e
SRC=config/worldedit/schematics
OUT=kubejs/data/winterheart/structures

python3 python/structures/nbt_to_structure.py "$SRC/watchtower.nbt" "$OUT/watchtower.nbt"
python3 python/structures/structure_fixups.py "$OUT/watchtower.nbt"

# Sea and seabed (template layer 13 and below) are left to the world; the chest gets its loot from
# shipwreck_chest.snbt (a /data get block dump).
python3 python/structures/nbt_to_structure.py "$SRC/shipwreck.schem" "$OUT/shipwreck.nbt" \
    --keep-world-below 13 \
    --keep-world-block minecraft:water --keep-world-block minecraft:sand --keep-world-block minecraft:gravel \
    --keep-world-block minecraft:dirt --keep-world-block minecraft:stone --keep-world-block minecraft:sandstone \
    --keep-world-block minecraft:andesite --keep-world-block immersive_weathering:mossy_stone \
    --merge-nbt 18,29,25 "$SRC/shipwreck_chest.snbt"
