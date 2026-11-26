# fishing

Fishing rods catch the real fish swimming in the water. `winterheart_fishing` is our Forge 1.20.1 port of Simple Fishing Overhaul 1.1.0 (pajic, MIT), which only ships for Fabric and NeoForge 26.x. Fish No Stuck fixes fish freezing in place (MC-182763).

## Files

- `mod-src/winterheart_fishing/`: the mod (Java, Gradle, mixins; own README/CHANGELOG). Package `io.github.soldierdonkey2.winterheart_fishing`.
- `.../Config.java`: reads the settings JSON on load and `/reload`.
- `.../fishing/`: `FishingSession` (per-bobber logic), `FishingLoot`, `FishingParticles`, `FishingUtil`, `FishState`/`FishAccess`/`HookAccess`, `FishEvents` (goals, drops, reload listener), `goal/{GetLuredGoal,GetCaughtGoal}`.
- `.../cast/CastCharging.java`: hold to charge a cast.
- `.../client/ClientSetup.java`: casting bar overlay.
- `.../mixin/`: `ButcheryCarcass`, `FishingHook`, `AbstractFish`, `FishMoveControl`, `RandomStrollGoal`, `FollowFlockLeaderGoal`; `client/` holds the two casting animations.
- `mod-src/winterheart_fishing/src/main/resources/assets/winterheart_fishing/textures/gui/cast_bar_{background,progress}.png`: from the original.
- `mod-src/winterheart_fishing/src/main/resources/META-INF/LICENSE-simple-fishing-overhaul`: the original's license, shipped in the jar.
- `mods/winterheart_fishing-1.0.0.jar`: built jar (tracked).
- `datapacks/winterheart_fishing/`: `data/winterheart/fishing/config.json`, README, CHANGELOG, `pack.properties`.
- `mods/fishnostuck-mc1.20.1-v1.0.3-forge.jar`: Fish No Stuck 1.0.3 (third-party, listed in `mods.json`, no config).

## How it works

1. Each server tick a bobber floats, the mixin runs `FishingSession.tickCatching` around vanilla's `FishingHook#catchingFish`. When it lands, and then every `intrigueRerollInterval` ticks, fish in the intrigue area may become interested (`FishState.hook`).
2. Vanilla's lure timer only runs while an `AbstractFish` is within `catchRadius`. When it ends, one fish is picked (closer and interested ones are favoured). `GetLuredGoal` swims it to the bobber, and the session holds vanilla's approach timer until it arrives.
3. On the bite `GetCaughtGoal` pins the fish under the bobber. Reeling in during the bite removes it and gives its `catchItems` item (a Butchery carcass for cod, salmon and pufferfish), or its entity loot table if it has no entry. With `junkAndTreasure` on, the non-`#minecraft:fishes` items from vanilla's fishing roll come with it. Only a caught fish gives XP.
4. Casting: right-click starts a use of up to 60 ticks; releasing (or a full charge) calls the rod's `use`, scales the bobber's velocity and sets a 30-tick cooldown. Reeling in is instant.
5. Killing a fish: `fishDropNoLoot` cancels `LivingDropsEvent`, and `fishDropNoCarcass` cancels Butchery's `{Cod,Salmon,Pufferfish}carcassdropProcedure#onEntityDeath` (a `@Pseudo` mixin), which also covers entities tagged `forge:cod` and the like. Butchery's `config/Butchery.toml` switches stay on, because they also control placing and cutting up carcasses.

Fish with an empty loot table, a custom name or (with `protectReleasedFish`) from a bucket are never caught. Rods extending `FishingRodItem` work. The spawn cabin's rod is in a barrel in `Spawn_v2.3.schem` (see [structures](structures.md)).

## Config / tunables

All in `datapacks/winterheart_fishing/data/winterheart/fishing/config.json`. Defaults are the original's.

| Key | Default | Effect |
| --- | --- | --- |
| `catchRadius` | 2 | Blocks around the bobber where a fish counts as catchable |
| `intrigueChance` | 0.33 | Chance a fish gets interested; Lure raises it up to 2x |
| `intrigueRadius`, `intrigueDepth` | 3, 9 | Area where fish can get interested |
| `lureBonusMultiplier` | 1.67 | Lure III radius multiplier (depth uses twice this) |
| `intrigueRerollInterval`, `intrigueRerollChanceMultiplier` | 40, 0.25 | Repeat rolls while floating |
| `minInterestTicks`, `maxInterestTicks`, `boredTicks` | 400, 800, 600 | How long interest and boredom last |
| `intriguedFishWeight` | 3 | Preference for interested fish |
| `maxApproachTicks`, `arrivalDistance` | 200, 1.5 | Approach time limit and bite distance |
| `minRelureTicks`, `maxRelureTicks` | 20, 60 | Wait after a failed approach |
| `castCharging`, `minCastDistanceMultiplier`, `maxCastDistanceMultiplier` | true, 0.25, 1.75 | Cast charging and its velocity range |
| `fishDropNoLoot` | true | Killed fish drop no vanilla loot |
| `fishDropNoCarcass` | true | Killed fish drop no Butchery carcass |
| `catchItems` | cod, salmon, pufferfish carcasses (empty if unset) | Entity id → item a catch gives |
| `junkAndTreasure` | false | Junk and treasure can come with a catch |
| `allowFishingWithoutFish` | false | Junk and treasure from water without fish (needs `junkAndTreasure` too) |
| `protectReleasedFish` | true | Bucket-released fish can't be caught |
| `castBar`, `firstPersonAnimation`, `thirdPersonAnimation` | true | Casting visuals |

## Changes

- 2026-11-26: Junk and treasure no longer come with a catch (new `junkAndTreasure` key, off). Tested headlessly on a copy of `New World` with a temporary probe (removed from the shipped jar): 40 cod catches dropped exactly 40 `butchery:cod_carcass` and nothing else. Final load of the shipped jar: joined in 30 s, 58 `ERROR` lines, none from the mod.
- 2026-11-26: Killed fish no longer drop Butchery carcasses (`fishDropNoCarcass`), and catching a cod, salmon or pufferfish gives its carcass (`catchItems`). Tested headlessly on a copy of `New World` with a temporary probe (removed from the shipped jar): catches gave `butchery:cod_carcass` (plus junk, `minecraft:leather`), `butchery:salmon_carcass`, `butchery:pufferfish` and `minecraft:tropical_fish`; nine killed cod, salmon and pufferfish left no item. Final load of the shipped jar: joined in 31 s, 58 `ERROR` lines, none from the mod. **Not tested:** placing and butchering a caught carcass.
- 2026-11-26: Added `winterheart_fishing` 1.0.0 and its datapack. Left out the original's netherite fishing rod (this pack blacklists netherite items), painting and config screen. Tested headlessly on a copy of `New World` with a temporary probe command (removed from the shipped jar) that ticked a bobber and a cod by hand in a glass tank: no bite in 4000 ticks in empty water or with a bucket cod; three wild cod bit (after 170 to 780 ticks), each gave 1 cod and 1 XP orb and was removed; a killed cod dropped only `butchery:cod_carcass`; a 1-tick charge cast at speed 0.32 and a full one at 2.04. Final load of the shipped jar: joined in 29 s, `[Fishing] config loaded`, no `ERROR` line from the fishing mods. **Not tested:** playing it with real input, the casting bar and animations on screen, Lure, `allowFishingWithoutFish`.
- 2026-11-26: Replaced Aquaculture 2.5.7, Aquaculture Delight 1.1.1 and Stardew Fishing 3.7 with Fish No Stuck 1.0.3. Removed their configs, the Neptunium rule in `config/item_obliterator.json5`, the minigame key in `config/defaultoptions/keybindings.txt`, their IDs in `tools/guide_editor/registry_ids.json` (edited by hand, not re-dumped) and their blocks in the schematics. Blacklisted Display Delight's `displaydelight:acd_*` display items, which it still registers without Aquaculture Delight and which have no textures. Headless load of a throwaway copy of `New World` reset to uninitialized, so the starter structure generated: pass, joined in 50 s; `data get block -8 66 20` showed the barrel with `minecraft:fishing_rod`; no `ERROR` line names the removed mods or Fish No Stuck. The `displaydelight:acd_*` blacklist line was added after that run; a second headless load passed (30 s, config read, 58 `ERROR` lines, the same count as earlier runs). Not tested: fishing itself, fish behaviour, loading a world that already holds removed items. Simple Fishing Overhaul was requested too, but it has no 1.20.1 Forge build.

## Gotchas

- Existing worlds lose every Aquaculture, Aquaculture Delight and Stardew Fishing item, block and entity. Their first load also logs one `Tried to load unrecognized recipe` error per unlocked recipe.
- `./fetch.sh --generate` keeps the Fish No Stuck entry, which was written into `mods.json` by hand with its CurseForge IDs; the CurseForge app does not know the jar was added.
- The starter structure is placed only when a world is created, so existing worlds don't get the barrel.
- Other mods that redirect the same calls in `FishingHook` would conflict (`defaultRequire` 1 makes it crash on load).
