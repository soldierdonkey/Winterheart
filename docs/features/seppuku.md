# seppuku

Our own Forge mod that plays a seppuku animation on a player (PlayerAnimator, vanilla player model) for the bad ending. A KubeJS script triggers it once when a player's sanity drops to 10.

## Files

- `mod-src/seppuku/`: mod source (Java, Gradle 8.8, ForgeGradle 6). It has its own README (credits, permission, converter usage) and CHANGELOG.
- `mod-src/seppuku/src/main/java/io/github/soldierdonkey2/seppuku/`: `Seppuku.java` (entry point), `SeppukuCommand.java` (`/seppuku`), `SeppukuApi.java` (server API), `network/` (animation sync packet), `client/SeppukuClient.java` (PlayerAnimator layer).
- `mod-src/seppuku/src/main/resources/assets/seppuku/player_animation/seppuku.json`: the generated animation.
- `mod-src/seppuku/tools/convert_epicfight.py`: converts the Epic Fight animation into the PlayerAnimator JSON.
- `mod-src/seppuku/examples/kubejs_bad_ending.js`: example trigger (day 50 / low sanity).
- `mod-src/seppuku/docs/nexo-permission.png`: Nexo's distribution permission.
- `mods/seppuku-1.0.0.jar`: the built jar.
- `kubejs/server_scripts/mechanics/sanity_seppuku.js`: the in-pack trigger.

## How it works

1. `sanity_seppuku.js` checks every player every 200 ticks. When `get_sanity(player)` (0–100, from `kubejs/server_scripts/utils/helper_functions.js`) is ≤ `SEPPUKU_SANITY_THRESHOLD` (10), it runs `seppuku play <name>` once. It arms again after the player recovers above the threshold. The armed state is in memory only, so a server restart resets it.
2. `/seppuku play <targets> [animation]` (permission level 2; default `seppuku:seppuku`) sends an `AnimationPacket` to the target and to every player tracking them.
3. On the client, `SeppukuClient` plays the animation on a PlayerAnimator layer. It runs for 59 ticks (about 3 s), then holds the kneeling pose until `/seppuku stop <targets>` or respawn.

The mod does not kill the player or change sanity. Any consequence belongs in the KubeJS script.

KubeJS API, server side:

```js
const SeppukuApi = Java.loadClass('io.github.soldierdonkey2.seppuku.SeppukuApi')
SeppukuApi.play(player)
SeppukuApi.stop(player)
```

Build: `cd mod-src/seppuku && JAVA_HOME=<JDK 17 or 21> ./gradlew build`. The `copyJarToMods` task replaces `mods/seppuku-*.jar`.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `SEPPUKU_SANITY_THRESHOLD` | `kubejs/server_scripts/mechanics/sanity_seppuku.js` | 10 | Sanity (0–100) at or below which the animation plays |
| `SEPPUKU_PROBE_INTERVAL` | `kubejs/server_scripts/mechanics/sanity_seppuku.js` | 200 | Ticks between checks |
| `player_animator_version` | `mod-src/seppuku/gradle.properties` | 1.0.2-rc1+1.20 | PlayerAnimator compiled against |
| `curseforge_project_id` | `mod-src/seppuku/gradle.properties` | empty | Needed before publishing (see [mod-publishing](mod-publishing.md)) |

## Changes

- 2026-11-22: Moved the source from the separate `~/Documents/Seppuku` repo (1 commit plus uncommitted README/permission-image changes, copied without history) into `mod-src/seppuku/`. Added the template's `copyJarToMods` task and CurseForge publish block (requires `playeranimator`). Added CHANGELOG.md. `./gradlew build` with Temurin 21 passes. The rebuilt jar has the same entries and contents as the shipped `mods/seppuku-1.0.0.jar`, so the shipped jar was left unchanged.

## Gotchas

- Requires PlayerAnimator on both client and server.
- The animation is All Rights Reserved (Nexo and sidtheplayer, Epic Fight: Dawn Day) and used with permission. Keep the credits in `mod-src/seppuku/README.md`.
- Gradle 8.8 can't run on Java 22+. The default `jdk-27` on this machine fails, so set `JAVA_HOME`.
