# Streams Reflowing -- custom bank features

This pass plants features on the land around water. As of 2.13.0 the mod SHIPS a default set as ordinary
files in this folder -- hand-built fallen logs and trees matched to their forests, pale driftwood on
beaches and in dry country -- and you own them from there:

- **Remove one**: delete its file. It stays deleted (a `.seeded_defaults` marker remembers what has been
  seeded, so deletion is final). Delete the marker itself to restore every default.
- **Change how often it appears**: edit `chance` in its file.
- **Change where**: edit `biomes` / `tags` / the excludes.
- **Change the shape itself** (blocks, sizes, leaf density): the features live in the mod's datapack at
  `data/streamsreflowing/worldgen/configured_feature/` -- override any of them from a datapack.

Your own entries live here too, exactly like the defaults. Every matching entry runs (there is no
override contest like bank styles have).

> **Full guide** (every field, more examples, datapacks, and bank styles too): see `GUIDE.md` in this
> folder. This file is the quick reference.

## How to add one
1. Look in `examples/example.json` for the shape. 2. Copy it UP into this folder (or write your own) and
edit it. 3. Relaunch. (Files in `examples/` are reference-only and are NOT loaded.)

## Where features place
On the bank around water, following your **bank placement** setting (`allWaterBanks`): along ALL water
(rivers/lakes/oceans + our streams) when that is on, or only our own streams when it is set to streams-only.
Each listed feature is run AT the bank column, on sturdy ground, exactly as written -- trees and big
features included (you chose them).

## Fields
- `features` : a POOL of PLACED-feature ids -- ONE is chosen at random per placement. Required.
- `biomes` : exact biome ids to include.   `tags` : biome tags the biome must ALL have to include.
  (If both are empty, the entry matches EVERYWHERE.)
- `exclude_biomes` / `exclude_tags` : biomes/tags to drop AFTER the include -- so "all overworld but not
  frozen" is `tags: ["minecraft:is_overworld"]` + `exclude_tags: ["minecraft:is_frozen_ocean", ...]`
  (plus any snowy biomes/tags you want out).
- `chance` : 0-100, the percent chance per matching near-water column. Default 100.

## Shipping entries with a mod / datapack
Put `<name>.json` files at `data/<namespace>/streamsreflowing/bank_feature/` in any datapack or mod jar --
they load automatically and are merged with this folder.
