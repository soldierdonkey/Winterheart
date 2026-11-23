# sanity

Winterheart's glue around the Sanity Dimension mod (`sanitydim`): a Java API for reading and setting sanity, and the despawn of its stalkers once everyone is sane. Sanity is **0-100, 100 = sane** in Winterheart code; the mod stores 0-1 the other way round.

## Files

- `mod-src/winterheart/src/main/java/io/github/soldierdonkey2/winterheart/api/Sanity.java`: `get`, `set`, `add`. Reads `ForgeCaps."sanitydim:sanity"."sanity.sanity"` from the player's NBT and writes with the mod's `/sanity set <player> <0-100>`. Also `Temperature.java` (see [survival-data](survival-data.md)), `Cmd.java`, `Titles.java`, `Scheduler.java`, `Rand.java`: small helpers the other mods share.
- `mod-src/winterheart_survival/src/main/java/io/github/soldierdonkey2/winterheart_survival/sanity/SanityDespawn.java`: despawns `sanitydim:rotting_stalker` and `sanitydim:sneaking_terror`.
- `mod-src/winterheart_survival/src/main/java/io/github/soldierdonkey2/winterheart_survival/sanity/SeppukuTrigger.java`: see [seppuku](seppuku.md).
- `config/sanitydim/`: the Sanity Dimension mod's own config.

## How it works

- **Despawn.** Every 20 ticks: if at least one player is online and every online player's sanity is above `THRESHOLD` (75), every stalker in every dimension is discarded. Logs `All players are above 75 sanity: despawned N stalkers` when it removes any.
- **Reading.** `Sanity.get` serialises the player's NBT (`saveWithoutId`), so it is cheap enough for occasional checks but not for every tick of every player.
- **Writing.** Through the command, so the sanity mod's own change logic and sync run. Values are clamped to 0-100.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `THRESHOLD` | `SanityDespawn.java` | 75 | Sanity every player must exceed for stalkers to despawn |
| `MOBS` | `SanityDespawn.java` | stalker, terror | Entity IDs despawned |

## Changes

- 2026-11-22: Ported `sanity_despawn.js` to Java (`SanityDespawn`) and added the shared `Sanity`/`Temperature`/`Cmd`/`Titles`/`Scheduler`/`Rand` API to the core mod, matching `helper_functions.js`. Step 3 of [general-kubejs-removal](../todo/general-kubejs-removal.md). Tested: headless run. `Sanity.get` and `Temperature.get/getAmbient` returned the same values as the JS getters on the joined player (100 / 26 / 0), `Sanity.set` and `Temperature.set` took effect (5.0 and 20 read back). The despawn handler's event wiring was confirmed (it received the server tick event), but its despawn path was **not** confirmed: mobs added in a probe at login aren't visible to `getAllEntities()` yet, and the hidden headless client ticks the server only a few times per run (see the run-pack doc).

## Gotchas

- Sanity reads and writes depend on the capability's NBT layout and the `/sanity set` command of `sanitydim-mc1.20-1.1.0`. A mod update can break them silently.
- `SanityJS` (a KubeJS addon) was removed from `mods/` together with KubeJS: nothing used it.
