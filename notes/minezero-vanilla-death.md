# Bypassing MineZero's death system (vanilla / hardcore death)

Findings from decompiling `mods/MineZero-1.1.0.jar` (Forge 1.20.1, KubeJS 6 / 2001.6.5). The scripts below have **not** been run in-game.

## How MineZero intercepts death

`boomcow.minezero.event.DeathEventHandler.onPlayerDeath` is a plain Forge `LivingDeathEvent` listener. It cancels the death (and schedules `CheckpointManager.restoreCheckpoint`) only when **both** are true:

1. An anchor is set: `CheckpointData.getAnchorPlayerUUID() != null`.
2. The dying player is the anchor, **or** gamerule `anyPlayerDeathTriggersRestore` is `true` (default `false`).

If either check fails the handler returns without touching the event, so vanilla death proceeds.

Non-anchor players therefore already die normally while `anyPlayerDeathTriggersRestore` is `false`.

`MineZero Patch.jar` only mixes into `CheckpointManager` (hunger handling); it does not change death interception.

## Vanilla death for the anchor player (no mixin needed)

Clear the anchor, kill, restore the anchor. `kill()` fires `LivingDeathEvent` synchronously, so the anchor is null when MineZero checks it. `setAnchorPlayerUUID` calls `setDirty()` itself, so the restore persists.

```js
// kubejs/server_scripts/vanilla_death.js
function vanillaKill(player) {
    const CheckpointData = Java.loadClass('boomcow.minezero.checkpoint.CheckpointData')
    const data = CheckpointData.get(player.level) // ServerLevel on the server
    const anchor = data.getAnchorPlayerUUID()
    data.setAnchorPlayerUUID(null)
    try {
        player.kill()
    } finally {
        data.setAnchorPlayerUUID(anchor)
    }
}
```

Caveats:
- Keep the try/finally. If the anchor isn't restored, MineZero stays off until someone runs `/setanchor` again.
- Do not `await` or schedule anything between clearing and restoring the anchor.
- `kubejs/config` has no `classfilter.txt`, so `Java.loadClass` of `boomcow.*` should be allowed. If it throws an access error, check that first.
- Method/field names are from MineZero 1.1.0; re-check after a mod update.

## Hardcore (no respawning)

### Why making the world hardcore doesn't solve it

- Hardcore is a world flag. Vanilla only checks `server.isHardcore()` when the player clicks Respawn (then puts them in spectator). MineZero cancels the death before that, so a hardcore world still restores the anchor. The `vanillaKill` bypass is needed either way.
- It is fixed at world creation (create-world toggle, or `hardcore=true` in `server.properties` for a *new* dedicated-server world).
- KubeJS can't flip it afterwards (final field on the level data; reflection is blocked).
- It would make every death permanent, including non-anchor deaths and other mods' deaths.

### Script option: kill, then spectator on respawn

```js
const HARDCORE_KEY = 'hardcore_dead' // stored on the server; per-player data may not carry over respawn

function hardcoreKill(player) {
    const data = player.server.persistentData
    if (!data.contains(HARDCORE_KEY)) data.put(HARDCORE_KEY, {})
    data.getCompound(HARDCORE_KEY).putBoolean(String(player.uuid), true)
    vanillaKill(player)
}

PlayerEvents.respawned(event => {
    const flags = event.server.persistentData.getCompound(HARDCORE_KEY)
    const id = String(event.player.uuid)
    if (flags.getBoolean(id)) {
        event.player.setGameMode('spectator')
    }
})
```

- Flagged players respawn as spectators and stay that way (matches vanilla hardcore).
- To let someone back in: `/gamemode survival <name>`, then clear their flag (`flags.remove(id)`), e.g. via a small admin command.
- Lock-out alternative: in the respawn handler use `event.server.runCommandSilent('ban ' + event.player.username + ' You died.')` (or `kick` for temporary). Closest to dedicated-server hardcore, but harsh in singleplayer/LAN since the host can't ban themselves.

### Not verified

- `PlayerRespawnedEventJS` exists in the KubeJS jar, but the exact method names (`persistentData`, `setGameMode`) are unconfirmed. ProbeJS hasn't generated usable typings; run `/probejs dump` in-game and check the `.d.ts` if one errors.

## Possible follow-up

`kubejs/server_scripts/mechanics/sanity_seppuku.js` plays the seppuku animation at sanity <= 10 but doesn't kill anyone. `hardcoreKill` could be wired in there if zero sanity should mean permanent death.
