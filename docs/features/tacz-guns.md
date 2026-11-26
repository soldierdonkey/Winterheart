# tacz-guns

Timeless and Classics Zero (TACZ) guns, trimmed to authentic pre-1950s weapons, with their ammo and attachments, plus Winterheart's gunsmith recipes.

## Files

- `datapacks/winterheart_tacz/pack.mcmeta`: `filter` that hides everything outside the period set in TACZ's default gun pack. Its allow-lists are the source of truth for what survives.
- `datapacks/winterheart_tacz/data/tacz/data/blocks/{gun_smith_table,attachment_workbench}.json`: overrides of the workbench tabs (period tab icons, no RPG tab on the table; no stock, grip or laser tab on the attachment workbench). The ammo workbench uses TACZ's tabs.
- `datapacks/winterheart_tacz/data/gundb/gundb/tacz/{ak47,db_long,db_short,fn_fal,hk_g3,kar98,m1911,m870,rpk,springfield1873,uzi}.json`: per-gun durability and jamming values for `gundb` (Gun Durability).
- `datapacks/winterheart_tacz/{README.md,CHANGELOG.md,pack.properties}`: publishing files ([mod-publishing](mod-publishing.md)).
- `tacz/tacz-pre.toml`: `DefaultPackDebug = false`, so TACZ exports its own default pack.
- `python/tacz/gen_gunsmith_recipes.py`: generates the gunsmith recipes and the jar recipe overrides. Its `GUNSMITH`, `OVERRIDES` and `DISABLED` tables are the source of truth.
- `datapacks/winterheart_recipes/data/winterheart/recipes/tacz_workbench/`: generated `tacz:gun_smith_table_crafting` recipes, `winterheart:tacz_workbench/<namespace>_<id>`.
- `datapacks/winterheart_recipes/data/tacz/recipes/`: generated overrides of every recipe in TACZ's jar (`tacz:gunpowder` is switched off by `python/recipes/gen_recipes.py`, see [recipes](recipes.md)).
- `config/tacz-{common,client}.toml`: TACZ settings.

## How it works

On start TACZ exports the default gun pack from its jar to `tacz/tacz_default_gun/`, skipping it when `tacz/.export-state.json` shows it is unchanged. Both are untracked. `gundb` then adds `unjam` animations to the exported pack.

TACZ adds the packs in `tacz/` at the bottom of the datapack stack and loads them through the vanilla resource manager, so the `filter` in `winterheart_tacz` (above them) hides files by path:

| Filtered path (namespace `tacz`) | Kept |
| --- | --- |
| `index/guns/`, `data/guns/<id>_data.json` | listed guns |
| `index/ammo/` | listed ammo |
| `index/attachments/`, `data/attachments/<id>_data.json` | listed attachments |
| `recipes/{ammo,attachments,gun}/` | nothing |
| `tacz_loot_injectors/` | nothing (stock adds a removed gun to the bonus chest) |

The client builds its gun, ammo and attachment lists from the server's data, so filtered entries don't exist in game. Their models, textures and sounds stay in the exported pack. Attachment tags are stock and still name filtered attachments.

Winterheart's own `tacz:gun_smith_table_crafting` recipes for the kept guns, ammo, attachments and workbenches live in the datapack `winterheart_recipes`, made from Winterheart metal parts ([recipes](recipes.md)). To change them, edit `GUNSMITH` and rerun:

```sh
python3 python/tacz/gen_gunsmith_recipes.py   # then /reload
```

Tabs only appear when a recipe belongs to them. A recipe's tab comes from its result: guns and attachments by type, ammo `tacz:ammo` unless the result sets `group`, and `custom` results by `group`. TACZ accepts only `gun`, `ammo`, `attachment` and `custom` result types, so the workbench blocks are `custom` results in `tacz:misc`.

Every recipe in TACZ's jar is replaced at its own ID:

| `tacz:` recipe | Now |
| --- | --- |
| `gun_smith_table` | iron gears, plate and rods, anvil |
| `iron_ammo_box`, `gold_ammo_box`, `diamond_ammo_box` | planks around the matching `winterheart:*_plate` |
| `target` | iron plates, redstone, planks |
| `target_minecart` | target, iron bolt, minecart |
| `statue` | iron plate and rods, smooth stone |
| `ammo_workbench`, `attachment_workbench` | off; made at the gun smith table |
| `misc/blood_strike_1` (painting) | off |
| `gunpowder` | off ([recipes](recipes.md)) |

The script only deletes and rewrites files carrying its own `__comment__` marker, so it shares the pack with `python/recipes/gen_recipes.py`.

### Durability

`gundb` reads `data/<namespace>/gundb/`. Each kept gun has its own file, `gundb:tacz/<id>`, which targets `tacz:<id>` and beats the gun type's file. A file replaces the default entirely, with no merging. Each file sets `JamThreshold` to 0.9, so jamming starts once durability drops below 90%. Durability, jam chance and repair values are tuned per gun; the stat penalties are copies of what each gun used before.

| Guns | Copied from `gundb`'s |
| --- | --- |
| `ak47`, `fn_fal`, `hk_g3` | `rifle` |
| `db_long`, `db_short`, `m870` | `shotgun` |
| `kar98` | `sniper` |
| `m1911` | `pistol` |
| `rpk` | `mg` |
| `uzi` | `smg` |
| `springfield1873` | `tacz/springfield1873` (replaces it) |

### Repair

Every gun repairs on an anvil with `winterheart:iron_bolt`, its main material. A full repair costs about a quarter of the iron in the gun's `GUNSMITH` recipe, counted in bolts (plate 4, rod 2, ring 4, bolt 1, screw 1, gear 20, from the part chain in [recipes](recipes.md)). Copper, diamond, wood and redstone parts aren't counted.

| Gun | Iron in recipe (bolts) | Bolts for a full repair | XP levels for a full repair |
| --- | --- | --- | --- |
| `db_short` | 20 | 5 | 10 |
| `db_long` | 32 | 7 | 10 |
| `springfield1873` | 34 | 8 | 8 |
| `kar98` | 38 | 10 | 24 |
| `m1911` | 43 | 10 | 40 |
| `m870` | 78 | 20 | 10 |
| `fn_fal` | 100 | 24 | 8 |
| `ak47` | 108 | 28 | 8 |
| `hk_g3` | 108 | 27 | 8 |
| `rpk` | 148 | 38 | 12 |
| `uzi` | 150 | 39 | 18 |

Each bolt restores `MaxDurability / RepairCost` points, rounded down, so `RepairCost` is not always the bolt count. After changing a gun's `MaxDurability` or recipe, recompute its `RepairCost` and `XpCost` by hand.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `filter.block[].path` | `datapacks/winterheart_tacz/pack.mcmeta` | period set | Regexes; the negative lookahead lists what survives. A gun or attachment appears in both its `index/` and `data/` entry. |
| `MaxDurability`, `RepairItem`, `RepairCost`, `XpCost` | `data/gundb/gundb/tacz/<id>.json` | per gun | Durability and anvil repair |
| `Jam`, `JamChance`, `JamThreshold`, `FireOnZero` | same | per gun; `JamThreshold` is 0.9 in these files (0.85 when omitted), `FireOnZero` `false` | Jamming, and firing when worn out |
| `Stats[]` (`Type`, `ReductionThreshold`, `MaxReduction`) | same | per gun | Stat penalties as durability drops |
| `GUNSMITH` | `python/tacz/gen_gunsmith_recipes.py` | 30 recipes | Gun smith table recipes |
| `OVERRIDES`, `DISABLED` | same | see table below | Replacements for, and switched-off, recipes from TACZ's jar |
| `DefaultPackDebug` | `tacz/tacz-pre.toml` | `false` | `true` stops TACZ from updating the exported pack |

## Changes

- 2026-11-26: Fixed the ammo and attachment workbench gunsmith recipes, which used the unsupported result type `block` (now `custom`). Replaced or switched off every recipe in TACZ's jar. Removed the `ammo_workbench.json` tab override, which had no visible effect. Headless test (`--world "New World"`, then `/reload`): joined 30 s after the wait began and the log showed no parse errors for `tacz` or `winterheart` recipes. `/recipe give` reported `tacz:ammo_workbench`, `tacz:attachment_workbench`, `tacz:gunpowder` and `tacz:misc/blood_strike_1` as unknown and found the other seven and both workbench gunsmith recipes. Not checked in game: the workbench results and tabs in the GUI, and crafting.

- 2026-11-26: Retuned `MaxDurability` per gun (70 to 410, was 600 to 1200) and `JamChance` for `springfield1873` (0.3) and `uzi` (0.4). Loaded by the same `/reload` test as the repair change; the later `XpCost` edits weren't reloaded in a test.

- 2026-11-26: Guns repair with `winterheart:iron_bolt` instead of iron or copper ingots, with `RepairCost` proportional to the iron in each recipe. `XpCost` was rescaled to keep each full repair at its earlier XP total, then hand-tuned for `m1911` (4, 40 levels) and `springfield1873` (1, 8 levels). Tested with `/reload` in a running game: no `gundb` errors. Not repaired on an anvil in game.

- 2026-11-26: Added a per-gun `gundb` file for each kept gun. Tested with `/reload` in a running game: the log showed no `gundb` errors. Not checked in game that the files take effect.
- 2026-11-26: Set `JamThreshold` to 0.9 in all eleven gun files, so jamming starts below 90% durability (was 0.85). Tested with `/reload` in a running game: no `gundb` errors. Not checked in game.

- 2026-11-26: Replaced the committed, hand-trimmed `tacz/tacz_default_gun/` and the scripts `python/tacz/purge_{guns,attachments,ammo}.py` with the datapack `winterheart_tacz`, and set `DefaultPackDebug = false`. The exported pack matches the jar except for the `unjam` animations. Translations are now the jar's. Tested: headless on `New World` before and after, with a JVM class histogram. Both had 11 client gun, 7 ammo, 10 attachment and 3 block indexes, 22 `GunData`, 22 `AttachmentData`, 31 `GunSmithTableRecipe` instances and no `LootTableInjection`. Joined in 26 s, `winterheart_tacz` enabled, `/reload` ran, and the log showed no new TACZ errors. Not checked by eye: guns in hand, attachment fitting and workbench tabs.

- 2026-11-22: Moved the purge scripts out of the gun pack (`tacz/tacz_default_gun/remove{,2,3}.py`) to `python/tacz/purge_{guns,attachments,ammo}.py`, so the pack no longer contains Python files. They used to mix paths relative to the instance root and to the gun pack. Now all their target dirs are anchored to `GUNPACK`. Checked by importing each module (which doesn't run the purge): all six target dirs resolve and exist. The purges were not re-run.

- 2026-11-22: Moved the gunsmith recipes from `kubejs/server_scripts/recipes/guns.js` to JSON generated by `python/tacz/gen_gunsmith_recipes.py` (step 1 of [general-kubejs-removal](../todo/general-kubejs-removal.md)). Same 30 recipes. Their IDs changed from `kubejs:tacz_workbench/*` to `winterheart:tacz_workbench/*`. `tacz:gun_smith_table` is now overridden in place instead of removed and re-added. Tested: headless smoke test on `Smoke Test` passed with no recipe errors. A probe script confirmed `winterheart:tacz_workbench/tacz_9mm`, `tacz_hk_g3` and `tacz:gun_smith_table` load. Not checked in the gunsmith table GUI.

## Gotchas

- A new TACZ version may add folders the filter doesn't cover, or rename files. New guns, ammo and attachments are hidden automatically.
- The filter applies to every pack below `winterheart_tacz`, so a third-party gun pack using the `tacz` namespace is filtered too.
- A kept gun whose stat file isn't named `<id>_data.json` needs its own entry in the `data/guns/` pattern.
