# loop-winterheart-loop-mod

Replace MineZero (`mods/MineZero-1.1.0.jar` + `mods/MineZero Patch.jar`) with our own mod `winterheart_loop`. It checkpoints the whole world and the whole player when Kaamos sleeps, and on death restores both exactly, including changes no player made: mob griefing, fluids, fire, growth, pistons, entities wandering off. MineZero only restores what its event listeners catch, and saves a fixed list of player fields, so time-system already has to back up capabilities itself (`retained_caps`) and detect deaths by polling for MineZero's `unluck` revive effect.

- **Feature:** `loop` (doc to be written: `docs/features/loop.md`)
- **Added:** 2026-11-26
- **Progress:** 0/12 (keep in sync with the row in [../TODO.md](../TODO.md))

## Design

Three parts, all in `mod-src/winterheart_loop/`. Single player only (see [plot](../plot/plot.md#the-loop)), so one player, one checkpoint.

### 1. World: copy-on-write chunk journal

Every change to the world ends up as a chunk write, so the mod journals writes instead of listening for causes.

- **Checkpoint.** `server.saveEverything(false, true, true)`, then `synchronize(true)` every `IOWorker`, so the disk is the checkpoint. Clear the journal and start a new epoch.
- **Capture.** Mixin into `net.minecraft.world.level.chunk.storage.IOWorker#runStore` (runs on the IO thread, just before `RegionFileStorage.write(pos, tag)`). On the first write of a position in this epoch, `storage.read(pos)` and save the result to the journal, or record the position as absent if it returns null. The three workers per dimension cover `region/` (blocks, block entities, scheduled ticks, light, `ForgeCaps`), `entities/` and `poi/`.
- **Journal.** A vanilla `RegionFileStorage` pointed at `<world>/winterheart_loop/<dim>/<region|entities|poi>/`, plus `absent.dat` (positions with no data at the checkpoint). Size is bounded by the chunks written in one day.
- **Restore.** Needs the chunks unloaded (see part 3). Then flush the workers, pause capture, write each journaled copy back and `write(pos, null)` for absent positions (they regenerate from the seed). Keep the journal: the disk equals the checkpoint again, so it stays valid for the next death in the same epoch.
- **Spawn chunks** never unload in 1.20.1. During a restore, remove the overworld's `TicketType.START` ticket and add it back afterwards.
- **Fallback.** A chunk that still won't unload (forced by another mod) is restored in place: `ChunkSerializer.read` into a `ProtoChunk`, copy sections, block entities and heightmaps into the live `LevelChunk`, resend it to the client. Only build this if step 6 finds such chunks.

### 2. Player: whole snapshot minus a carry-forward list

- **Checkpoint.** `player.saveWithoutId(tag)`. This covers what MineZero's `PlayerData` misses: saturation and exhaustion, every Forge capability (Legendary Survival Overhaul temperature, thirst and body damage, Curios), `ForgeData`, ender chest, attributes, absorption, air, cooldowns, selected slot. Stored in the mod's `SavedData`. Stats and the recipe book live outside the player tag (`stats/`, `advancements/`) and are left alone, so they carry forward.
- **Restore.** Remove the carry-forward keys from a copy of the snapshot, copy in the player's current values for those keys, then `player.load(tag)`.
- **Carry-forward keys** live in `config/paxi/datapacks/winterheart_loop/data/winterheart/loop/config.json` (re-read on `/reload`), as NBT paths, e.g. `ForgeCaps.<cap id>`. Things the story moves forward through the loop belong here (see open question 1).
- **Client sync** comes from the trip through limbo (part 3): changing dimension makes vanilla resend abilities, effects, health, food and XP and rebuild the client player. Mods that don't resync their capability on `PlayerChangedDimensionEvent` need a nudge, found in step 9.

### 3. Death and the rewind sequence

- **Death.** Cancel `LivingDeathEvent` for the player while the loop is active, set health to 1 and start the rewind. A flag `LoopApi.setActive(false)` lets the bad ending and permadeath kill for real (replaces the bypass in [audits/minezero-vanilla-death.md](../audits/minezero-vanilla-death.md)).
- **Rewind.** Fade to black, teleport the player to `winterheart_loop:limbo` (an empty void dimension, `config/paxi/datapacks/winterheart_loop/data/winterheart_loop/dimension/limbo.json`). Each tick, check whether the affected dimensions have unloaded their chunks. Then restore the world, restore the player (which puts them back at the checkpoint position and dimension) and fade in. Time-out after ~10 s: fall back to in-place restore for whatever is still loaded, and log it.
- **Events for other mods.** `LoopEvents.Checkpoint` (after the checkpoint is written) and `LoopEvents.Rewound` (after the player is back). time-system listens to `Rewound` instead of polling for `unluck`.
- **Weather** is saved at the checkpoint and restored. **Time of day** is not touched: time-system already rewinds it to dawn.
- **Sound and item.** A restore sound of our own (MineZero is All Rights Reserved, so none of its assets can be reused). Drop the flute and the self-damage key unless the story needs them.

### What stays outside the restore

- `data/winterheart_state.dat` ([winterheart-core](../features/winterheart-core.md) `WorldState`): moves forward on purpose (day counter, `death_count`, sanity history). One catch: [structures](../features/structures.md) stores `fixed_structure_placed:<template>@<x>,<z>` there. A structure placed after the checkpoint would be rolled back while its flag stays set. Fix: on `Checkpoint`, remember which flags are new; on `Rewound`, clear the ones set during the epoch, so the structure is placed again.
- Other `data/*.dat` saved data (raids, maps, mod data) is held in memory by `DimensionDataStorage` and is not rolled back. Add types one at a time if a bug shows up.
- `stats/` and `advancements/` move forward (MineZero rolled back advancements; decide in open question 1).

## Steps

- [ ] Create the mod: `mod-src/_template/new-mod.sh winterheart_loop "Winterheart: Loop" "Return by death: restores the world and the player to the last sleep."`, enable mixins in `build.gradle`.
- [ ] Journal capture: `IOWorker#runStore` mixin, journal storage, epoch start on `/loop checkpoint` (op command for testing). Log under `[Loop]`. Check that the journal grows only for written chunks.
- [ ] Journal restore by command (`/loop restore`) with the player standing far from the changed chunks: break blocks, let a creeper explode, drop items, move a mob, then restore and walk back.
- [ ] Limbo dimension and the rewind sequence: teleport out, wait for unloads (including the spawn `START` ticket), restore, teleport back. Test restoring with the player standing in the changed chunks.
- [ ] Player snapshot and restore with the carry-forward list; config in `winterheart_loop` Paxi pack.
- [ ] Find chunks that won't unload (forced chunks from other mods); build the in-place fallback only if there are any.
- [ ] Death interception, `LoopApi.setActive`, `LoopEvents.Checkpoint` / `Rewound`.
- [ ] Port time-system: call the loop's checkpoint from `saveState` instead of `setcheckpoint` / `setSubaruPlayer`; listen to `Rewound` instead of polling `unluck`; drop the `unluck` / `registering_checkpoint` workaround; drop `retained_caps` and `CapBackup` if the player snapshot covers them.
- [ ] Check client sync for each mod with player data: Legendary Survival Overhaul (temperature, thirst, body damage), Curios, sanity, survival-hud. Add resync calls where needed.
- [ ] Structures: clear `fixed_structure_placed` flags set during the epoch on `Rewound`.
- [ ] Remove `mods/MineZero-1.1.0.jar`, `mods/MineZero Patch.jar`, `config/minezero-common.toml`; delete or mark obsolete `docs/audits/minezero-vanilla-death.md`. Headless load test (AGENTS.md rule 7) on `New World`.
- [ ] Write `docs/features/loop.md`, add the feature index row, update feature-map.md, time-system.md, structures.md, the plot's loop section and CHANGELOG.md.

## Files

- `mod-src/winterheart_loop/`
- `config/paxi/datapacks/winterheart_loop/`
- `mod-src/winterheart_time/src/main/java/io/github/soldierdonkey2/winterheart_time/TimeSystem.java`
- `mod-src/winterheart_time/src/main/java/io/github/soldierdonkey2/winterheart_time/TimeCommands.java`
- `mod-src/winterheart_time/src/main/java/io/github/soldierdonkey2/winterheart_time/TimeConfig.java`
- `mod-src/winterheart/src/main/java/io/github/soldierdonkey2/winterheart/api/CapBackup.java`
- `mod-src/winterheart_structures/src/main/java/io/github/soldierdonkey2/winterheart_structures/FixedStructures.java`
- `mods/MineZero-1.1.0.jar`
- `mods/MineZero Patch.jar`
- `config/minezero-common.toml`
- `docs/audits/minezero-vanilla-death.md`
- `docs/features/loop.md`
- `docs/features/time-system.md`
- `docs/features/structures.md`
- `docs/plot/plot.md`
- `docs/feature-map.md`
- `docs/README.md`
- `CHANGELOG.md`

## Testing

The headless client barely ticks the server, so the rewind can't be driven headlessly. Steps 2 to 9 are tested in a running game with the op commands (`/loop checkpoint`, `/loop restore`) and by dying. Each test changes the world in a way no player listener would catch (creeper, water, fire spread, a mob walking off, an item in a hopper) and checks all of it comes back. The headless test only covers loading (step 11).

## Open questions

1. **What carries forward through the loop?** Candidates: unlocked guides and journal pages, advancements, stats, anything sanity-related. This fills the carry-forward list and decides whether `advancements/` is restored. Check where [guides](../features/guides.md) stores what is unlocked.
2. **The first checkpoint.** MineZero's is set by time-system at first login. Keep that, or checkpoint when the world is created?
3. **Rewind length.** Unloading chunks takes a few seconds. Is a short fade with the death title enough cover, or does the rewind need its own screen?
4. **Other dimensions.** Restore every dimension written this epoch, or only the overworld? (The Nether and End may not be reachable at all.)

## Notes

- 2026-11-26: Plan written after decompiling MineZero 1.1.0 (`WorldData`, `PlayerData`, `CheckpointManager`, the event listeners). Only time-system calls MineZero (`setcheckpoint`, `setSubaruPlayer`, the `unluck` revive).
