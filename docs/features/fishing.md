# fishing

Fishing is vanilla Minecraft, plus Fish No Stuck, which fixes fish freezing in place (MC-182763).

## Files

- `mods/fishnostuck-mc1.20.1-v1.0.3-forge.jar`: Fish No Stuck 1.0.3 (third-party, listed in `mods.json`, no config).

## How it works

Fish No Stuck is a set of mixins on the fish swim and school goals. It adds no items, blocks or data. The spawn cabin's fishing rod is in a barrel saved in `config/starterstructure/schematics/Spawn_v2.3.schem` (see [structures](structures.md)).

## Config / tunables

None.

## Changes

- 2026-11-26: Replaced Aquaculture 2.5.7, Aquaculture Delight 1.1.1 and Stardew Fishing 3.7 with Fish No Stuck 1.0.3. Removed their configs, the Neptunium rule in `config/item_obliterator.json5`, the minigame key in `config/defaultoptions/keybindings.txt`, their IDs in `tools/guide_editor/registry_ids.json` (edited by hand, not re-dumped) and their blocks in the schematics. Blacklisted Display Delight's `displaydelight:acd_*` display items, which it still registers without Aquaculture Delight and which have no textures. Headless load of a throwaway copy of `New World` reset to uninitialized, so the starter structure generated: pass, joined in 50 s; `data get block -8 66 20` showed the barrel with `minecraft:fishing_rod`; no `ERROR` line names the removed mods or Fish No Stuck. The `displaydelight:acd_*` blacklist line was added after that run; a second headless load passed (30 s, config read, 58 `ERROR` lines, the same count as earlier runs). Not tested: fishing itself, fish behaviour, loading a world that already holds removed items. Simple Fishing Overhaul was requested too, but it has no 1.20.1 Forge build.

## Gotchas

- Existing worlds lose every Aquaculture, Aquaculture Delight and Stardew Fishing item, block and entity.
- `./fetch.sh --generate` keeps the Fish No Stuck entry, which was written into `mods.json` by hand with its CurseForge IDs; the CurseForge app does not know the jar was added.
- The starter structure is placed only when a world is created, so existing worlds don't get the barrel.
