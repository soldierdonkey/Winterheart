# beacon-base

Right-clicking a beacon turns the 3x3 of blocks directly beneath it into iron blocks, giving it a tier-1 pyramid base. The beacon screen does not open.

## Files

- `mod-src/winterheart_survival/src/main/java/io/github/soldierdonkey2/winterheart_survival/world/BeaconBase.java`

## How it works

`PlayerInteractEvent.RightClickBlock`, main hand only, on `minecraft:beacon`. On the server it sets the nine blocks at the beacon's Y - 1 to `minecraft:iron_block` (flag 3). On both sides it cancels the interaction with `InteractionResult.SUCCESS`, so the GUI stays closed and held items aren't used.

## Changes

- 2026-11-22: Ported from `kubejs/server_scripts/mechanics/beacon_base.js`. Step 3 of [general-kubejs-removal](../todo/general-kubejs-removal.md). Tested: a headless probe called `BeaconBase.buildBase` below the player and read back iron blocks at the centre, edge and corner of the 3x3 and air outside it. The right-click event itself wasn't triggered by hand.

## Gotchas

- It replaces whatever is under the beacon, including a real pyramid. Nothing checks for or refunds it.
