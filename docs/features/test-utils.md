# test-utils

Testing helpers: `/toggle_test` freezes the custom time at noon and blocks mob spawns; right-clicking some vanilla items prints or changes sanity and temperature.

## Files

- `mod-src/winterheart/src/main/java/io/github/soldierdonkey2/winterheart/api/TestMode.java`: `/toggle_test` (op), the spawn block, and `TestMode.enabled()`.
- `mod-src/winterheart/src/main/java/io/github/soldierdonkey2/winterheart/api/TestItems.java`: the right-click diagnostics.

## How it works

- `/toggle_test` flips an in-memory flag (off after a restart). When turned on it runs `time set 6000`. While on: the time system doesn't tick, and `MobSpawnEvent.FinalizeSpawn` is cancelled. The scripted spawner (`spawning.js`, until it is ported) checks `TestMode.enabled()` itself.
- Diagnostics: nether star = sanity +10, netherite ingot = sanity -10, quartz = temperature +5 (each prints before and after), end portal frame prints current and previous sanity, nether brick prints a line.

## Changes

- 2026-11-22: Ported from `kubejs/server_scripts/utils/test_mode.js` and `tests.js` to the core mod (step 4 of [general-kubejs-removal](../todo/general-kubejs-removal.md)). The spawn block uses `FinalizeSpawn` instead of KubeJS's `checkSpawn`. Tested: `/toggle_test` registered. The flag, the spawn block and the items weren't exercised.
