# singleplayer-only

Winterheart is single player only (see [plot](../plot/plot.md)). winterheart-core refuses to load on a Forge dedicated server, so the server crashes at mod loading with a message saying the pack is singleplayer only.

## Files

- `mod-src/winterheart/src/main/java/io/github/soldierdonkey2/winterheart/Winterheart.java`: the check in the mod constructor, message in `SERVER_MESSAGE`.
- `mods/winterheart-1.0.0.jar`: the built jar.

## How it works

The first thing the `Winterheart` constructor does is check `FMLEnvironment.dist`. On `Dist.DEDICATED_SERVER` it logs `SERVER_MESSAGE` as an error and throws an `IllegalStateException` with the same message. Forge wraps it in a mod loading error (`winterheart has failed to load correctly`), with the message as the cause, and the server stops with a crash report in `server/crash-reports/`. The client, including its integrated server for singleplayer and LAN, is `Dist.CLIENT` and is not affected.

The Multiplayer button is not hidden yet (planned in the plot).

## Config / tunables

None. The check is always on.

## Changes

- 2026-11-26: Created. The dedicated server crashes on load with "Winterheart is a singleplayer-only modpack and can't run on a dedicated server. Play it in singleplayer from the Minecraft client." Binary: `mods/winterheart-1.0.0.jar`.
  Tested: headless client smoke test on `New World` passed (joined in 42 s, `winterheart loaded`, no Winterheart errors). The dedicated server crash was not tested: `server/` isn't installed, and installing it downloads Forge and needs the EULA accepted.

## Gotchas

- `./run-pack.sh --server` (and its `--send`/`--console`/`--run` RCON commands) can't start the pack any more. Use the client for testing.
