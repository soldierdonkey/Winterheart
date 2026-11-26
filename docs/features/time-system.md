# time-system

The custom day/night cycle. The vanilla cycle is off (`gamerule doDaylightCycle false`); the mod advances `exact_time` at a speed that depends on the day, counts days, lets sleeping pass the day, brings an eternal night from day 50, drains sanity after midnight, and rewinds to morning when a player dies.

## Files

- `mod-src/winterheart_time/` (Java): `TimeSystem.java` (engine, sleep, death loop, hooks), `TimeConfig.java` (tuning + reload listener), `TimeCommands.java` (`/customtime`), `WinterheartTime.java`.
- `datapacks/winterheart_time/data/winterheart/time/config.json`: all tuning, re-read on `/reload`.
- `mod-src/winterheart/.../api/{Ticks,TestMode,Numbers,CapBackup}.java`: the tick counter, `/toggle_test`, number-to-text, capability backup (core).
- `mods/winterheart_time-1.0.0.jar`: the built jar.

## How it works

State lives in the world state ([winterheart-core](winterheart-core.md)), same keys as the KubeJS version: `custom_day`, `exact_time` (0-18000, a double), `current_phase`, `sleep_cooldown`, `death_cooldown`, `death_count`, `midnight_ticks`, `previous_sanity_<uuid>`, `has_loaded<uuid>`, `registering_checkpoint`, plus the capability backups `<prefix><uuid>_<uuid>_<capId>`.

- **Engine.** Every overworld tick (`TickEvent.LevelTickEvent`, END; skipped when `/toggle_test` is on). `exact_time += speed`, and the world's day time is set to it. Speed = day speed (`speed_intervals.day`, interpolated from day 1 to `eternal_night_day`) before dusk, blending to night speed between `DUSK` and `NIGHT`. Phases (`DAWN`, `NOON`, `AFTERNOON`, `DUSK`, `NIGHT`, `MIDNIGHT`) change at the `phases` thresholds. At `MIDNIGHT` time stops, and the sanity drain starts.
- **Midnight drain.** `midnight_ticks` counts up; every second each non-spectator, non-creative player loses `rate / 60` sanity, `rate = base_rate * 2^(minutes / doubling_minutes)`, capped at `max_rate`.
- **Eternal night.** From `eternal_night_day` (50): time is pinned to midnight, phase `ETERNAL_NIGHT`, thunder. Beds refuse (right-click message, cancelled).
- **Sleeping.** When every player in the overworld sleeps (and `sleep_cooldown` is 0), 95 ticks later (if still all asleep) the day increments, time resets to dawn, each player gets Recovery and Regeneration (`wake_up`), a checkpoint (`saveState`) and is woken.
- **Checkpoint (`saveState`).** Sanity +30, remembers it as `previous_sanity_<uuid>`, backs up the `retained_caps` capabilities, applies `unluck` (amplifier 60, 100 ticks) and blindness, runs MineZero's `setcheckpoint` and `setSubaruPlayer`, then removes `unluck` 5 ticks later. Also runs once per player at first login.
- **Death loop.** MineZero revives a dead player with `unluck`. A player tick that sees `unluck` while `registering_checkpoint` is false means a death: `death_count++`, rewinds to dawn (before day 50; sleep cooldown set), gives every player the ominous message, a heartbeat sound, sanity `max(previous - 10, 25)`, restores the retained capabilities, and shows a title. On day 1 it also runs `starterkit give`.
- **Hooks.** Each tick `triggerTimeHook`: weather by phase (every 10 s: clear at dawn, rain at noon after day 30, rain in the afternoon after day 20, thunder in the eternal night). On a phase change: `GuideApi.firePhase` for the `time_phase` guide triggers, and titles at `NIGHT` and `MIDNIGHT`.
- **Commands** `/customtime` (op): `query`, `debug`, `day <n>`, `time <ticks>`, `skipday`, `phase <dawn|noon|afternoon|dusk|night|midnight>`.

## Config / tunables

`config.json` keys: `debug`, `debug_interval`, `eternal_night_day` (50), `sleep_cooldown` (200), `wake_up.{enabled, recovery_effect, recovery_duration, recovery_amplifier, regen_effect, regen_amplifier, regen_heal_fraction, regen_ticks_per_hp}`, `midnight_drain.{enabled, base_rate, doubling_minutes, max_rate}`, `speed_intervals.{day, night}` (`[start, end]`), `phases` (name → tick), `retained_caps` (backup key prefix → capability id), `death_splash`. The guide editor reads the `phases` names from this file.

## Changes

- 2026-11-22: Ported from `kubejs/server_scripts/mechanics/time_system.js` (plus `utils/number_counting.js`, `utils/test_mode.js`, `utils/tests.js`) to the Java mod `winterheart_time` and the core. Step 4 of [general-kubejs-removal](../todo/general-kubejs-removal.md). Tuning moved from `TIME_CONFIG` in the script to `config.json`. Same formulas and state keys, so existing worlds continue (the world state imports KubeJS's data once). Differences: the world's time is set directly on each dimension instead of through the `time set` command; sleeping increments the day once (the script incremented it once per player, wrong with more than one player). `spawning.js` still runs in KubeJS and now reads the tick counter and test flag through `Ticks` / `TestMode`. This restores the `time_phase` guide trigger (fired from Java via `GuideApi`). Tested: a headless probe drove `TimeSystem.tick` directly (the hidden client ticks the server rarely): config loaded from the datapack (8 retained caps); commands registered; from the imported state (day 16, time 4620) 100 ticks advanced time by 127.3 (formula: 1.27 per tick); NOON and MIDNIGHT phases switched at their thresholds; day 50 gave ETERNAL_NIGHT; the temperature capability backed up, changed and restored (26 → 5 → 26); number text ("one hundred and seven", "Fall twelve times, stand up thirteen."). The real tick event also advanced the time once or twice. Not tested: sleeping through a day, the death loop (needs MineZero's revive), weather and title commands, `/customtime` output.

## Gotchas

- Restoring capabilities reloads the player from their NBT (`Entity.load`), as KubeJS's `mergeNbt` did.
- If the config file is missing, the defaults compiled into `TimeConfig` apply.
