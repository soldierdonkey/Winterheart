# run-pack

Launches the Winterheart instance from the shell in offline mode, without the CurseForge app. AGENTS.md rule 7 uses it for headless smoke tests. The client reuses the Forge install, libraries, assets and Java runtime that CurseForge already downloaded. Ported from the Soulrend development instance.

## Files

- `run-pack.sh`: entry point. A wrapper that builds `tools/run_pack` (`cargo build --release`, a no-op when up to date) and `exec`s `tools/run_pack/target/release/run-pack` with `RUN_PACK_GAME_DIR` set to the instance root. `./run-pack.sh --help` lists the options.
- `tools/run_pack/Cargo.toml`: the Rust crate (binary `run-pack`; deps `serde_json`, `regex`, `md-5`, `ctrlc`, `libc`).
- `tools/run_pack/src/main.rs`: parses options, picks Java, refuses to start if the instance is already running, then `exec`s the game. With `--headless` it runs the game in the background and watches it instead.
- `tools/run_pack/src/launch.rs`: reads the version JSONs and builds the client's java argument list.
- `tools/run_pack/.gitignore`: ignores `target/`.

## How it works

```sh
./run-pack.sh                      # offline player "Dev"
./run-pack.sh --username Tester    # different offline name
./run-pack.sh --dry-run            # print the java command, don't launch
./run-pack.sh --world "Smoke Test" # join a single-player world on start

./run-pack.sh --headless --world "Smoke Test"          # smoke test (rule 7): load the world, then stop
./run-pack.sh --headless --until 'Sound engine started' --timeout 300   # mods + resources loaded, no world
./run-pack.sh --headless --world "Smoke Test" --keep-running   # leave it running, hidden
```

1. `launch.rs` reads `baseModLoader.name` (`forge-47.4.10`), `allocatedMemory` and `javaArgsOverride` from `minecraftinstance.json`.
2. It loads `<install>/versions/forge-47.4.10/forge-47.4.10.json` and the vanilla JSON it `inheritsFrom` (`1.20.1`). It applies the macOS library and argument rules and merges the libraries. A Forge entry replaces a vanilla one with the same `group:artifact:classifier`.
3. The classpath is the library jars plus the Forge version jar. Natives come from `<install>/natives/forge-47.4.10`, which CurseForge already extracted. Assets come from `<install>/assets`.
4. Offline login: `--userType legacy` and access token `0`. The UUID is UUIDv3 of `OfflinePlayer:<name>`, the same one a vanilla offline server assigns.
5. On top of the version-JSON arguments, it adds the extra JVM flags CurseForge passes: `-Xms256m`, `-Dfml.ignorePatchDiscrepancies=true`, `-Dfml.ignoreInvalidMinecraftCertificates=true`, `-Duser.language=en`.

The game directory is the repo root, so a run uses the same `mods/`, `config/`, `saves/` and `options.txt` as a CurseForge launch.

### Headless mode

`--headless` means a hidden client. The macOS client can't run without a window, so the game runs normally with its app hidden.

1. The game runs in the background with stdin from `/dev/null`. Its stdout/stderr go to `logs/run-pack-headless.out`. The readable log is still `logs/latest.log`.
2. Once a second, run-pack hides the app with `NSRunningApplication.hide` through `osascript -l JavaScript`. That needs no Accessibility permission.
3. If there's a pattern (`--until REGEX`, or `<username> joined the game` when `--world` is given), run-pack waits for it in `logs/latest.log`. It only reads the file after the inode changes, so the previous run's log can't match.
4. To stop the game, run-pack sends it `SIGTERM` (the client has no command listener), and the shutdown hook saves the world. If the game is still running after 60 s, run-pack sends `SIGKILL`. Ctrl-C does the same.
5. At the end, it prints how many `/ERROR]` lines `logs/latest.log` has, plus the newest file in `crash-reports/` if a new one appeared.

| Outcome | Exit code | Game |
| --- | --- | --- |
| Pattern seen | 0 | Stopped (left running with `--keep-running`) |
| Timed out | 124 | Stopped |
| Game exited before the pattern | 1 | — |
| No pattern, game exited cleanly | 0 | — |
| Ctrl-C | 130 | Stopped |

For rule 7, look at `logs/latest.log`: the Winterheart mods log under their own names.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `MC_INSTALL` | env | `~/Documents/curseforge/minecraft/Install` | CurseForge install dir |
| `FORGE_VERSION` | env | `baseModLoader.name` in `minecraftinstance.json` | Version JSON to launch |
| `JAVA` | env | `<install>/java/java-runtime-gamma/Contents/Home/bin/java` (Java 17), else `java` on PATH | Java binary |
| `MEMORY_MB` | env | `allocatedMemory` in `minecraftinstance.json`, else 7040 | `-Xmx` |
| `--username` | arg | `Dev` | Offline player name |
| `--world` | arg | none | `--quickPlaySingleplayer <name>` (a folder in `saves/`), which also sets the default `--until` |
| `--until` / `--timeout` / `--keep-running` | arg | `<username> joined the game` / 600 s / off | Headless wait and stop behaviour |

## Changes

- 2026-11-26: Removed the dedicated server and RCON code: `--server`, `--accept-eula`, `--run`, `--send` and `--console` are gone, with `tools/run_pack/src/server.rs`, `tools/run_pack/src/rcon.rs` and `tools/run_pack/server_exclude.txt`. The pack can't run on a dedicated server ([singleplayer-only](singleplayer-only.md)), so they had no use. `.gitignore` no longer lists `/server/`. Tested: `--dry-run` gives the same command as before; `--server` is rejected as an unknown option (exit 2). **Client headless (rule 7)** `--world "New World"`: joined in 37 s, stopped cleanly, exit 0, 58 ERROR lines (same third-party sources).
- 2026-11-26: Rewrote `run-pack.sh` and its Python helpers (`launch_args.py`, `server_setup.py`, `rcon.py`) as the Rust tool `tools/run_pack`; `run-pack.sh` is now a build-and-exec wrapper, so all options and exit codes are unchanged. `server_exclude.txt` moved to `tools/run_pack/`. The headless game now runs in its own process group, so a terminal Ctrl-C reaches only run-pack, which stops the game itself. `--console` lost readline line editing and history. Tested:
  - `--dry-run --world "Smoke Test" -- --foo "a b"`: the same 70 java arguments as the bash/Python version (offline UUID included). `--server --dry-run`: same command.
  - RCON against a fake RCON server on port 25575 (instance dir pointed at a scratch dir with `RUN_PACK_GAME_DIR`): two `--send` commands, a 5000-byte reply split over two packets, `--console` from piped input ending in `stop`, and a wrong password (exit 1).
  - **Client headless (rule 7)** `--world "New World"`: joined in 42 s, stopped cleanly, exit 0. `logs/latest.log` had 58 ERROR lines, the same third-party sources as before.
  - `--headless --until 'Sound engine started'` interrupted with `kill -INT` on the script after 20 s: the game was stopped, exit 130, nothing left running.
  - **Not tested:** `--server` launch (the pack crashes on a dedicated server, see [singleplayer-only](singleplayer-only.md)).
- 2026-11-22: Ported from Soulrend. Removed the client RCON listener (it needs Soulrend's dev-utils mod), so `--run`, `--send` and `--console` are server-only and the client is stopped with SIGTERM. Shared server dirs are now `config, defaultconfigs, datapacks, kubejs, tacz`. Wrote a new `server_exclude.txt` for Winterheart's client-only jars. Tested:
  - `--dry-run --world "Test Post-Merge"`: builds `forge-47.4.10` with `-Xmx7040m`.
  - **Client headless (rule 7)** `--world "Smoke Test"` (a copy of `New World (4)`): joined in 43 s and stopped cleanly. `logs/latest.log` had 54 ERROR lines, all from third-party mods: NukeGpuParticle shaders (GL 4.3 unsupported on macOS), mixin config warnings, unparseable loot tables in overweight_farming/gore_edition/butchery, a twilightforest model. KubeJS loaded 8/8 startup and 25/25 server scripts with 0 errors.
  - Two earlier runs timed out after 600 s with `Failed to find a primary monitor` (Forge early window). The Mac's display was asleep, so GLFW found no monitor and Forge waited on its error dialog. They passed once the display was awake.
  - **Not tested:** `--server` (needs the EULA accepted, and the exclude list is unverified), `--send`/`--console`.

## Gotchas

- **The display must be awake.** If the Mac's display is asleep, the client fails at `Failed to find a primary monitor - this means LWJGL isn't working properly`. Forge then waits on an error dialog until the timeout (exit 124). Wake the screen and run it again.
- The first run after a change in `tools/run_pack/` rebuilds it, which needs `cargo` on PATH.
- Agents running it from a sandboxed shell need the sandbox disabled for this command, because GLFW needs the window server.
- Stopping a headless client sends SIGTERM, which can segfault in GLFW teardown and make macOS show "Java quit unexpectedly". The world is saved before that. To end a run early, signal the **script** (`kill -INT <pid>`), not java.
- `--world` names a folder in `saves/`. Use a throwaway copy (e.g. `saves/Smoke Test`): joining a world writes chunks and player data to it.
- macOS only: the library rules evaluate `os.name` as `osx`, and the Java path is a macOS bundle path.
- Offline mode: multiplayer servers with `online-mode=true` reject the client.
- Forge must already be installed by CurseForge. Launch the instance from the app once after changing the Forge version.
- Headless: the hidden client loses focus, so the pause menu opens and the integrated server stops ticking unless `options.txt` has `pauseOnLostFocus:false`. Loading and joining aren't affected.
