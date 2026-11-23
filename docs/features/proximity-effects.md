# proximity-effects

Every 5 seconds the server scans a sphere around each player for blocks matching a set of rules (mostly block tags such as `#winterheart:gore`). A rule that matches on two scans in a row fires: a one-time title, and/or repeating sanity, temperature or potion effects.

## Files

- `kubejs/server_scripts/mechanics/proximity.js`: the rules (`RULES`), the scan and the trigger logic.
- `config/paxi/datapacks/winterheart_survival/data/winterheart/tags/blocks/{gore,rotten_gore,carcass,cooking,furniture,warmth,foliage}.json`: the block tags the rules match.
- `config/paxi/datapacks/winterheart_survival/data/butchery/tags/blocks/skeletons.json`: fills in `#butchery:skeletons` (Butchery doesn't ship it). `#winterheart:carcass` uses it.
- `kubejs/server_scripts/utils/helper_functions.js`: `get_sanity`, `set_sanity`, `get_temperature`, `set_temperature`, `showCustomTitle`, `randomChance`, `randomDelay`, `rollrandom`.

## How it works

1. `PlayerEvents.tick`, every 100 ticks per player: scans every block within `RADIUS` (5) of the player's feet, skipping air. For each rule not yet hit this scan, checks the block against the rule's `tag` or `blocks`. Rules with `level: true` ignore blocks more than one below the player's feet.
2. The rule IDs hit this scan are compared with the previous scan, stored in `player.persistentData.sweep_last_blocks`. A rule hit on both scans (about 10 s nearby) fires:
   - it is skipped if a rule in its `overtaken_by` was also hit (`gore` is suppressed by `rotten_gore`; `carcass` by both),
   - `global.Guides.fireProximity(player, ruleId)` lets guides trigger on it,
   - `onOnce` runs if the rule ID isn't in `player.persistentData.sweep_triggered_once` yet, and is then added to it,
   - `onRepeat` runs every time.

Rules: `gore`, `rotten_gore`, `carcass`, `cooking`, `furniture`, `warmth`, `foliage`, `diamond_ore`, `lapis_ore`, `redstone_ore` (vanilla `#minecraft:*_ores`), `deepslate` (block list; can summon `whos_there:white_eye`).

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `RULES` | `kubejs/server_scripts/mechanics/proximity.js` | see file | Rule IDs, tags/blocks and effects |
| `RADIUS` | same | `5` | Scan radius in blocks |
| `PROXIMITY_DEBUG` | same | all `false` | Debug logging to chat/console |
| Block tag contents | `config/paxi/datapacks/winterheart_survival/data/winterheart/tags/blocks/*.json` | see files | Which blocks count for each rule |

## Changes

- 2026-11-22: Moved the block tags from `kubejs/server_scripts/tags/proximity_tags.js` into the Paxi pack `winterheart_survival` as tag JSON (`"replace": false`, same entries). Step 1 of [general-kubejs-removal](../todo/general-kubejs-removal.md). Tested: headless smoke test on `Smoke Test` passed with no tag errors. A probe script counted the resolved tags: gore 145, rotten_gore 16, carcass 80, cooking 4, furniture 13, warmth 51, foliage 137, `butchery:skeletons` 29 blocks.

## Gotchas

- Unlike KubeJS, a datapack tag with an entry for a block that doesn't exist fails to load entirely. Mark entries for optional mods as `{"id": "...", "required": false}`.
- Known bug: the `foliage` rule looks up `#winterheart:folieage` (typo), so it never fires. To be fixed when the script is ported to `winterheart_survival` (step 5 of the KubeJS removal).
- Known bug: the `foliage` rule's effect sets sanity to `get_temperature(...) + 2`, not `get_sanity(...) + 2`. Same fix point.
