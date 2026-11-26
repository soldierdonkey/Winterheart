# spark

Third-party profiler (`spark-1.10.53-forge.jar`, from Modrinth) for diagnosing freezes, tick lag, garbage collection and heap use.

## Files

- `mods/spark-1.10.53-forge.jar`: the mod, untracked, listed in `mods.json`.
- `config/spark/config.json`: turns the background profiler off.
- `.gitignore`: ignores spark's temporary files and saved reports.

## How it works

`/spark` runs on the integrated server and `/sparkc` on the client; both share one Java heap. Run from a terminal with `./run-pack.sh --send "<command>"` ([run-pack](run-pack.md)). Replies arrive late, so read them in `logs/latest.log`.

| Command | Result |
| --- | --- |
| `spark gc` | Garbage collector counts and durations |
| `spark tickmonitor --threshold-tick 100` | Reports slow ticks and collections; run again to stop |
| `spark profiler start --force-java-sampler` | Starts a CPU profile. Always pass the flag on macOS |
| `spark profiler stop --save-to-file` | Saves a `.sparkprofile` to `config/spark/` |
| `spark heapsummary --save-to-file` | Saves a `.sparkheap` histogram to `config/spark/`; freezes the game briefly |

`--save-to-file` keeps a report local; without it spark uploads the report to its web service. Open saved files at <https://spark.lucko.me/>.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `backgroundProfiler` | `config/spark/config.json` | `false` (spark's own default is `true`) | Profile from server start. Leave off, see Gotchas |

## Changes

- 2026-11-26: Added, with the background profiler off. **Client headless (rule 7)** on a copy of `New World`, macOS (M3, 16 GB), Java 17, ZGC, `-Xmx7040m`:
  - Default config: **failed**, JVM exit 134 (`SIGBUS` in `libasyncProfiler`) right after `Starting background profiler...`.
  - Background profiler off: **passed**, joined 35 s after launch, 31 `/ERROR]` lines, none naming spark.
  - `spark gc`, `tickmonitor`, `heapsummary --save-to-file` and a Java-sampler profile ran. Heap histogram just after joining: about 1.9 GB. Resident memory: about 6.9 GB. ZGC: 32 cycles averaging 1.5 s about two minutes after joining, pauses 0 ms.
  - **Not tested:** active play, `/sparkc`.
- 2026-11-26: Retest of the same sequence after setting the local CurseForge profile to 5120 MB with no extra Java arguments (G1). **Client headless (rule 7)** on a fresh copy of `New World`: **passed**, joined in 27 s, 61 `/ERROR]` lines at exit (same as the first run). Heap histogram 1.5 GB (was 1.9 GB); resident memory 3.7 GB after joining and 2.8 GB at the end (was 6.9 GB; macOS may report ZGC memory high); G1 young pauses averaged 5.9 ms over 80, one old collection of 333 ms; no tick over 100 ms reported while `tickmonitor` ran. **Not tested:** active play.

## Gotchas

- The native sampler (async-profiler) crashed the JVM on macOS. `spark profiler start` without `--force-java-sampler` is likely to do the same.
- A ZGC "cycle" duration in `spark gc` or `tickmonitor` is mostly concurrent work, not a freeze of that length. "Pauses" are the stop-the-world part.
