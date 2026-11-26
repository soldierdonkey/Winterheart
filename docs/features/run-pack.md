# run-pack

Launches the Winterheart instance from the shell in offline mode, without the CurseForge app, as a client or as a Forge dedicated server. AGENTS.md rule 7 uses it for headless smoke tests. The client reuses the Forge install, libraries, assets and Java runtime that CurseForge already downloaded. The dedicated server is installed into `server/` on first use, which is the only time run-pack downloads anything. Ported from the Soulrend development instance.

## Files

- `run-pack.sh`: entry point. It parses options, picks Java, refuses to start if the instance is already running, then `exec`s the game. With `--headless` it runs the game in the background and watches it instead.
- `python/run_pack/launch_args.py`: reads the version JSONs and prints the full java argument list, NUL-separated.
- `python/run_pack/server_setup.py`: `install` downloads the Forge installer and runs `--installServer` into `server/`. `prepare` links mods, copies the shared dirs, and writes `server.properties`, `user_jvm_args.txt` and `eula.txt`.
- `python/run_pack/server_exclude.txt`: client-only mods left out of the server (globs on jar names).
- `python/run_pack/rcon.py`: stdlib RCON client behind `--send`, `--console` and `--run`.
- `.gitignore`: ignores `/server/`.
- `server/`: gitignored. Holds the Forge server install, `server.properties`, worlds, `logs/`, `crash-reports/` and `.run-pack.pid`.
- `local/run-pack/rcon-password`: gitignored. The server RCON password, generated on the first server launch.

## How it works

```sh
./run-pack.sh                      # offline player "Dev"
./run-pack.sh --username Tester    # different offline name
./run-pack.sh --dry-run            # print the java command, don't launch
./run-pack.sh --world "Smoke Test" # join a single-player world on start

./run-pack.sh --headless --world "Smoke Test"          # smoke test (rule 7): load the world, then stop
./run-pack.sh --headless --until 'Sound engine started' --timeout 300   # mods + resources loaded, no world
./run-pack.sh --headless --world "Smoke Test" --keep-running   # leave it running, hidden

./run-pack.sh --server                             # dedicated server in the foreground (its console works)
./run-pack.sh --server --headless --keep-running   # dedicated server in the background
./run-pack.sh --server --headless --run "list"     # run commands once it's up
./run-pack.sh --send "time query daytime"          # command in the running server, prints the output
./run-pack.sh --console                            # interactive prompt to the server; Ctrl-D quits
./run-pack.sh --send stop                          # stop the server
```

1. `launch_args.py` reads `baseModLoader.name` (`forge-47.4.10`), `allocatedMemory` and `javaArgsOverride` from `minecraftinstance.json`.
2. It loads `<install>/versions/forge-47.4.10/forge-47.4.10.json` and the vanilla JSON it `inheritsFrom` (`1.20.1`). It applies the macOS library and argument rules and merges the libraries. A Forge entry replaces a vanilla one with the same `group:artifact:classifier`.
3. The classpath is the library jars plus the Forge version jar. Natives come from `<install>/natives/forge-47.4.10`, which CurseForge already extracted. Assets come from `<install>/assets`.
4. Offline login: `--userType legacy` and access token `0`. The UUID is UUIDv3 of `OfflinePlayer:<name>`, the same one a vanilla offline server assigns.
5. On top of the version-JSON arguments, it adds the extra JVM flags CurseForge passes: `-Xms256m`, `-Dfml.ignorePatchDiscrepancies=true`, `-Dfml.ignoreInvalidMinecraftCertificates=true`, `-Duser.language=en`.

The game directory is the repo root, so a run uses the same `mods/`, `config/`, `saves/` and `options.txt` as a CurseForge launch.

### Headless mode

For the client, `--headless` means a hidden client. The macOS client can't run without a window, so the game runs normally with its app hidden. With `--server` there is no window, and the server simply runs in the background.

1. The game runs in the background with stdin from `/dev/null`. Its stdout/stderr go to `logs/run-pack-headless.out` (`run-pack-server.out` for the server). The readable log is still `logs/latest.log`.
2. Once a second, the script hides the app with `NSRunningApplication.hide` through `osascript -l JavaScript`. That needs no Accessibility permission.
3. If there's a pattern (`--until REGEX`, or `<username> joined the game` when `--world` is given), the script waits for it in `logs/latest.log`. It only reads the file after the inode changes, so the previous run's log can't match.
4. To stop the game, the script sends RCON `stop` to the server. The client has no command listener (see below), so it gets `SIGTERM`, and the shutdown hook saves the world. If the game is still running after 60 s, the script sends `SIGKILL`. Ctrl-C does the same.
5. At the end, it prints how many `/ERROR]` lines `logs/latest.log` has, plus the newest file in `crash-reports/` if a new one appeared.

| Outcome | Exit code | Game |
| --- | --- | --- |
| Pattern seen | 0 | Stopped (left running with `--keep-running`) |
| Timed out | 124 | Stopped |
| Game exited before the pattern | 1 | — |
| No pattern, game exited cleanly | 0 | — |
| Ctrl-C | 130 | Stopped |
| `--run` command failed | 1 | Stopped, or left running with `--keep-running` |

For rule 7, look at `logs/latest.log`: the Winterheart mods log under their own names.

### Dedicated server

Winterheart refuses to load on a dedicated server ([singleplayer-only](singleplayer-only.md)), so `--server` currently ends in a crash.

`--server` runs the pack on a Forge dedicated server, with its game dir in `server/`. On each launch:

1. **Install (first time only).** `server_setup.py install` downloads `forge-1.20.1-<v>-installer.jar` from `maven.minecraftforge.net` into `server/.installer/` and runs it with `--installServer server/`. It is skipped once `server/libraries/net/minecraftforge/forge/1.20.1-<v>/unix_args.txt` exists.
2. **EULA.** `server/eula.txt` is written only with `--accept-eula`, or after a y/N prompt that shows the EULA link. Without a terminal and without the flag, the launch stops.
3. **Mods.** `server/mods/` is rebuilt as symlinks to `../../mods/*.jar`. Jars matching `server_exclude.txt` are left out.
4. **Shared dirs.** `config`, `defaultconfigs`, `datapacks` and `tacz` are copied into `server/` with `rsync -a --delete`. They are copies, not symlinks: some mods throw on a symlinked `config/`, and copying keeps server-side edits out of tracked files.
5. **`server.properties`.** Sets `online-mode=false`, `enable-rcon=true`, `rcon.port=25575`, `rcon.password`, `broadcast-rcon-to-ops=false`, and `level-name` from `--world` (default `world`, i.e. `server/world/`).
6. **JVM args.** `server/user_jvm_args.txt` gets `-Xmx` from `MEMORY_MB` or `allocatedMemory`.
7. **Launch.** `java @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-<v>/unix_args.txt nogui` runs in `server/`. The ready pattern is `Done \(`.

A client-only mod that touches `net.minecraft.client` stops the server from starting. The typical error is `Attempted to load class net/minecraft/client/... for invalid dist DEDICATED_SERVER`. Add the jar to `server_exclude.txt` as a glob without the version, with the reason.

### On-demand commands (server only)

`--send CMD` (repeatable), `--console` and `--run CMD` use vanilla RCON on port 25575 with the password in `local/run-pack/rcon-password`. Soulrend also has a client-side listener in its dev-utils mod. Winterheart has no such mod, so these options refuse to run without a dedicated server.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `MC_INSTALL` | env | `~/Documents/curseforge/minecraft/Install` | CurseForge install dir |
| `FORGE_VERSION` | env | `baseModLoader.name` in `minecraftinstance.json` | Version JSON to launch |
| `JAVA` | env | `<install>/java/java-runtime-gamma/Contents/Home/bin/java` (Java 17), else `java` on PATH | Java binary |
| `MEMORY_MB` | env | `allocatedMemory` in `minecraftinstance.json`, else 7040 | `-Xmx` |
| `--username` | arg | `Dev` | Offline player name |
| `--world` | arg | none | Client: `--quickPlaySingleplayer <name>` (a folder in `saves/`), which also sets the default `--until`. Server: `level-name`. |
| `--until` / `--timeout` / `--keep-running` | arg | `<username> joined the game` / 600 s / off | Headless wait and stop behaviour |
| `--server` / `--accept-eula` | arg | off | Dedicated server in `server/` |
| `--run` / `--send` / `--console` | arg | — | Server RCON commands |
| `SERVER_RCON_PORT` | `run-pack.sh` | 25575 | Server RCON port |
| Excluded mods | `python/run_pack/server_exclude.txt` | — | Globs on jar names left out of `server/mods/` |

## Changes

- 2026-11-22: Ported from Soulrend. Removed the client RCON listener (it needs Soulrend's dev-utils mod), so `--run`, `--send` and `--console` are server-only and the client is stopped with SIGTERM. Shared server dirs are now `config, defaultconfigs, datapacks, kubejs, tacz`. Wrote a new `server_exclude.txt` for Winterheart's client-only jars. Tested:
  - `--dry-run --world "Test Post-Merge"`: builds `forge-47.4.10` with `-Xmx7040m`.
  - **Client headless (rule 7)** `--world "Smoke Test"` (a copy of `New World (4)`): joined in 43 s and stopped cleanly. `logs/latest.log` had 54 ERROR lines, all from third-party mods: NukeGpuParticle shaders (GL 4.3 unsupported on macOS), mixin config warnings, unparseable loot tables in overweight_farming/gore_edition/butchery, a twilightforest model. KubeJS loaded 8/8 startup and 25/25 server scripts with 0 errors.
  - Two earlier runs timed out after 600 s with `Failed to find a primary monitor` (Forge early window). The Mac's display was asleep, so GLFW found no monitor and Forge waited on its error dialog. They passed once the display was awake.
  - **Not tested:** `--server` (needs the EULA accepted, and the exclude list is unverified), `--send`/`--console`.

## Gotchas

- **The display must be awake.** If the Mac's display is asleep, the client fails at `Failed to find a primary monitor - this means LWJGL isn't working properly`. Forge then waits on an error dialog until the timeout (exit 124). Wake the screen and run it again.
- Agents running it from a sandboxed shell need the sandbox disabled for this command, because GLFW needs the window server.
- Stopping a headless client sends SIGTERM, which can segfault in GLFW teardown and make macOS show "Java quit unexpectedly". The world is saved before that. To end a run early, signal the **script** (`kill -INT <pid>`), not java.
- `--world` names a folder in `saves/`. Use a throwaway copy (e.g. `saves/Smoke Test`): joining a world writes chunks and player data to it.
- macOS only: the library rules evaluate `os.name` as `osx`, and the Java path is a macOS bundle path.
- Offline mode: multiplayer servers with `online-mode=true` reject the client.
- Forge must already be installed by CurseForge. Launch the instance from the app once after changing the Forge version.
- Headless: the hidden client loses focus, so the pause menu opens and the integrated server stops ticking unless `options.txt` has `pauseOnLostFocus:false`. Loading and joining aren't affected.
- Server: the Forge maven answers 403 to urllib's default User-Agent, so `server_setup.py` sends `winterheart-run-pack/1`.
- Server: RCON listens on all interfaces with `online-mode=false`. Use it on a trusted network, or set `server-ip=127.0.0.1` in `server/server.properties`.
