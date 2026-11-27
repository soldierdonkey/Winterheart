# butchery-fixes

Fills block tags that Butchery 5.2 reads but leaves missing, so sulfuric acid works on horse and wolf carcasses and a hammer breaks iron golem heads.

## Files

- `datapacks/winterheart_butchery/data/forge/tags/blocks/horse.json`: the 16 fresh and drained horse carcasses, zombie horses included
- `datapacks/winterheart_butchery/data/forge/tags/blocks/wolf.json`: the fresh and drained wolf carcass
- `datapacks/winterheart_butchery/data/butchery/tags/blocks/iron_heads.json`: the plain and repaired iron golem head
- `datapacks/winterheart_butchery/{pack.mcmeta,pack.properties,README.md,CHANGELOG.md}`: pack metadata and publishing text

## How it works

[packfinder](packfinder.md) loads the pack. Butchery checks the block tags itself, so no code is involved.

| Tag | Read by | Result |
| --- | --- | --- |
| `forge:horse` | Butchery's horse acid logic (also reads `c:horse`) | An uncut carcass becomes `butchery:skeleton_horse_carcass` after 11 s |
| `forge:wolf` | Butchery's wolf acid logic (also reads `c:wolf`) | An uncut carcass becomes `butchery:wolf_skeleton` after 11 s |
| `butchery:iron_heads` | Butchery's head breaking | A hammer breaks the head and rolls `butchery:blocks/iron_heads_breakdown` (2 redstone, 1-8 iron nuggets, sometimes 1-3 iron ingots) |

The golem heads are also in `butchery:heads`, but `butchery:unbreakable_heads` keeps them out of that branch, so they reach the iron one.

[butchery-jei](butchery-jei.md) reads the same tags when JEI starts, so its acid and skull pages appear without a change to the mod.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |

## Changes

- 2026-11-27: Added the pack. Headless load test (`New World`): pass, joined after 27 s, `Found new data pack winterheart_butchery`, and JEI went from 7 to 8 skull pages and from 25 to 27 acid pages, so the three tags load. No log errors about them. Pouring acid and hammering heads has not been tried in game.
- 2026-11-27: Zombie horses stay included (confirmed). Closed the todo on the user's word; no separate record of an in-game check of acid or the hammer.

## Gotchas

- Butchery's own `forge:horse_carcass` and `forge:drained_horse_carcass` block tags are uneven and not read by acid, so the IDs are listed instead of referencing them.
- Zombie horses are included on purpose, as in Butchery's `forge:horse_carcass`.
- As with other carcasses, acid used on one that has already been cut is spent and does nothing (from static reading).
- Recheck after updating Butchery: it may start shipping these tags.
