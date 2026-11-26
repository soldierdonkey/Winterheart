# mob-spawning

The scripted mob spawner. Vanilla spawning is off (`gamerule doMobSpawning false`, `wendigoSpawns false`, `naturalRegeneration false`, set at server start); the mod places mobs itself, with limits that depend on the custom day ([time-system](time-system.md)).

## Files

- `mod-src/winterheart_spawning/` (Java): `Spawner.java` (the three controllers and the event hooks), `Placement.java` (block classification, hitbox fit, surface and cave search), `SpawnConfig.java` (tuning + reload listener), `WinterheartSpawning.java`.
- `datapacks/winterheart_spawning/data/winterheart/spawning/config.json`: all tuning, re-read on `/reload`.
- `mods/winterheart_spawning-1.0.0.jar`: the built jar.

## How it works

Every second (`Ticks.everySecond`, overworld level tick, skipped by `/toggle_test`), per player:

- **Hostiles.** The cap comes from the day (`hostile_caps`: 0 up to day 10, 6, 12, 20, 32 from day 41). Over the cap, the farthest non-`interacted` hostile within `despawn_radius` is discarded (the reaper). Otherwise a spawn is attempted when it is day 21 or later, or the player is below `surface_y` (a cave), or it is day 11+ and dusk or night. Caves: 8-16 blocks behind the player (opposite their facing, +-25 degrees), within 4 blocks of their height, from `hostile_pool_cave`. Surface: 24-60 blocks away on the surface (at or above `surface_y` - 5), from `hostile_pool_surface`. The spot must fit the mob's footprint (a solid floor and free headroom, `width` 1 or 2 by `height`) and be dark (light at most `max_spawn_light`, 7). Mobs get the tag `synthetic_hostile`; babies are set baby.
- **Livestock** (every 4 seconds). Until day 30, not at midnight or in the eternal night, and only for a player on the surface: if fewer than the day's quota (`livestock_quotas`) animals are within 60 blocks, a flock of 2-4 (2 from day 21) of one random species spawns around a surface anchor 24-60 blocks away. Tag `synthetic_livestock`.
- **Wendigo.** On each day in `wendigo.days`, after dusk, once (`wendigo_spawned_day_<day>` in the world state): one `antlers:wendigo` 40-60 blocks from a random player, tagged `scripted_wendigo` and persistent. Natural wendigo spawns (spawn types `natural`, `chunk_generation`, `structure`, `spawner`, `patrol`, `reinforcement`) are cancelled; the mod's own mad/flute transformations pass, and every wendigo form is made persistent on join.
- **No new livestock after day 30** (`block_livestock_after_day`). A species in `livestock_pool` that tries to enter the overworld (spawns, breeding, other mods) is cancelled and logged as `Blocked entity: ... from spawning!`. Animals loaded from saved chunks are left alone.
- **Audit and interaction.** Every mob that joins the world without the spawner is logged as `[External Mob Spawn]` (`audit_log`). A hostile hurting or being hurt by anything gets the tag `interacted`, which protects it from the reaper.

Never loads chunks: a column in an unloaded chunk counts as unusable.

## Config / tunables

`config.json` keys: `debug_verbose` (trace lines `[Spawner:...]`), `audit_log`, `block_livestock_after_day`, `surface_y`, `despawn_radius`, `max_spawn_light`, `hostile_caps`, `livestock_quotas`, `hostile_pool_surface` and `hostile_pool_cave` (a key listed twice is picked twice as often), `livestock_pool`, `mob_templates` and `livestock_templates` (key → `id`, `name`, `width`, `height`, `baby`), `wendigo.{days, types, blocked_spawn_types}`.

## Changes

- 2026-11-22: Ported from `kubejs/server_scripts/mechanics/spawning.js` to Java, the tables becoming `config.json` (step 6 of [general-kubejs-removal](../todo/general-kubejs-removal.md)). Same rules and numbers. Differences: chunks are no longer force-loaded by the block lookups; `Ticks` / `TestMode` come from the core mod. The old "extra spawn prevention" block (stop livestock after day 30) compared short pool names (`cow`) with registry ids (`minecraft:cow`) and never matched; it is brought back below. Left as it was: `stray` is in both hostile pools but has no template, so it is skipped when picked (it only wastes a slot); add a `stray` template to enable it. Tested: headless probe with the surrounding chunks loaded and a forced day: config loaded; caps (0/6/12/20/32) and quotas (80/20/10/0) read back; the gamerule was set; three livestock flocks spawned (sheep, rabbits, pigs); on day 20 at night the wendigo spawned once and set its flag; hostile attempts at noon were rejected for light, as intended. **Not tested:** a hostile spawn (needs darkness), the cave search, the reaper, the audit log and wendigo blocking and persistence.

- 2026-11-22: Brought back the block on new livestock after day 30, now matching the pool species by registry id and skipping animals loaded from disk, so existing farms survive. Tested: a probe added entities near the player; the log showed `Blocked entity` for a cow and a pig on day 31 and nothing for a cow on day 30 or a zombie on day 31.

## Gotchas

- `/toggle_test` stops all three controllers (and the time system).
- The livestock quota counts every `Animal` within 60 blocks, including pets and farm animals.
