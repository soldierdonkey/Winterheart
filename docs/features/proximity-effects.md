# proximity-effects

Every 5 seconds the server scans a sphere around each player for blocks matching a set of rules (mostly block tags such as `#winterheart:gore`). A rule that matches on two scans in a row fires: a one-time title, and/or repeating sanity, temperature or potion effects.

## Files

- `mod-src/winterheart_survival/src/main/java/io/github/soldierdonkey2/winterheart_survival/proximity/Proximity.java`: the scan, rule matching and action runner.
- `datapacks/winterheart_survival/data/winterheart/proximity/rules.json`: the rules and their actions (re-read on `/reload`). The guide editor reads the rule ids from it.
- `datapacks/winterheart_survival/data/winterheart/tags/blocks/{gore,rotten_gore,carcass,cooking,furniture,warmth,foliage}.json`: the block tags the rules match.
- `datapacks/winterheart_survival/data/butchery/tags/blocks/skeletons.json`: fills in `#butchery:skeletons` (Butchery doesn't ship it). `#winterheart:carcass` uses it.

## How it works

1. `PlayerTickEvent`, every 100 ticks (5 s) per player: scans every block within `radius` (5) of the player's feet (a sphere), skipping air. A block counts for a rule if it is in the rule's `tag` or `blocks` list. A rule with `level: true` ignores blocks more than one below the player's feet. Each rule is reported at most once per scan.
2. The rule ids found are compared with the previous scan, kept in the player's persistent data (`sweep_last_blocks`). A rule found on both scans (about 5-10 s nearby) fires, unless a rule in its `overtaken_by` was also found (`gore` yields to `rotten_gore`; `carcass` to both):
   - `GuideApi.fireProximity(player, ruleId)` for guide triggers,
   - `on_once` actions if the rule isn't in `sweep_triggered_once` yet (it is added, and removed again when the player leaves range),
   - `on_repeat` actions every time.
3. A phase's actions run together after a random delay (`delay: [min, max]` ticks). Each action has an optional `chance` (percent) and one effect: `sanity` or `temperature` (add), `effect` (`id`, `duration` as a number or a list to pick from, `amplifier`), `title` (`messages`, `bold`, `italic`; shown on the action bar) or `command` (`{player}` = the player's name).

Rules: `gore`, `rotten_gore`, `carcass`, `cooking`, `furniture`, `warmth`, `foliage`, `diamond_ore`, `lapis_ore`, `redstone_ore` (vanilla `#minecraft:*_ores`), `deepslate` (a block list; can summon `whos_there:white_eye`).

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `rules` | `proximity/rules.json` | see file | Rule ids, blocks and tags, effects |
| `radius` | `proximity/rules.json` | 5 | Scan radius in blocks |
| `debug` | `proximity/rules.json` | false | Log confirmed rules |
| Block tag contents | `data/winterheart/tags/blocks/*.json` | see files | Which blocks count for each rule |

## Changes

- 2026-11-22: Moved the block tags from `kubejs/server_scripts/tags/proximity_tags.js` into the Paxi pack `winterheart_survival` as tag JSON (`"replace": false`, same entries). Step 1 of [general-kubejs-removal](../todo/general-kubejs-removal.md). Tested: headless smoke test on `Smoke Test` passed with no tag errors. A probe script counted the resolved tags: gore 145, rotten_gore 16, carcass 80, cooking 4, furniture 13, warmth 51, foliage 137, `butchery:skeletons` 29 blocks.

- 2026-11-22: Ported from `kubejs/server_scripts/mechanics/proximity.js` to Java, with the rules as data in `rules.json` (step 5 of [general-kubejs-removal](../todo/general-kubejs-removal.md)). Same rules, chances, delays and messages. Fixed two bugs: the `foliage` rule looked up `#winterheart:folieage` (typo), so it never fired; and its effect set sanity from the temperature (now sanity +2 with a 7.5% chance, so walking among plants can now very occasionally raise sanity). The `proximity` guide trigger works again. Tested: the rules loaded; with a campfire, anvil, diamond ore and lapis ore placed next to the player the scan returned `warmth`, `furniture`, `lapis_ore`, `diamond_ore`; two sweeps stored `warmth` in `sweep_last_blocks` and `sweep_triggered_once`. The delayed actions (titles, effects, sanity changes) didn't run in the probe (the hidden headless client barely ticks the server).

## Gotchas

- Unlike KubeJS, a datapack tag with an entry for a block that doesn't exist fails to load entirely. Mark entries for optional mods as `{"id": "...", "required": false}`.
