# fetch-mods

`./fetch-mods.sh` fills `mods/` from the tracked manifest `mods.json` and verifies every third-party jar by SHA-1. `./fetch-mods.sh --generate` rewrites the manifest. Only the `repo` jars are tracked in git; `.gitignore` ignores every other jar in `mods/`.

## Files

- `.gitignore`: ignores `mods/*.jar`, with a `!mods/...` line for each `repo` jar (own mods by pattern).
- `fetch-mods.sh`: wrapper. Builds the tool with cargo and sets `FETCH_MODS_GAME_DIR` to the instance root.
- `mods.json`: the manifest, one entry per shipped jar in `mods/`.
- `tools/fetch_mods/Cargo.toml`, `tools/fetch_mods/Cargo.lock`: Rust crate.
- `tools/fetch_mods/src/main.rs`: options.
- `tools/fetch_mods/src/manifest.rs`: manifest format, validation, hashing.
- `tools/fetch_mods/src/generate.rs`: `--generate`.
- `tools/fetch_mods/src/fetch.rs`: fetching, `--check`, manual download watching.

## How it works

```
./fetch-mods.sh              # download missing mods and verify
./fetch-mods.sh --check      # report drift, change nothing, exit 1 if out of sync
./fetch-mods.sh --generate   # after adding, updating or removing a mod
```

Run `./fetch-mods.sh` after cloning, and after any pull that changes `mods.json`. After changing mods in the CurseForge app, run `--generate`, read the `+`/`~`/`-` lines and commit `mods.json`.

### Sources

| `source` | Where the jar comes from | Set by `--generate` when |
| --- | --- | --- |
| `curseforge` | CurseForge CDN `url` | the launcher data matches the jar and `allowModDistribution` is not false |
| `modrinth` | Modrinth CDN `url` | not `curseforge`, and Modrinth knows the SHA-1 |
| `manual` | the user's browser, from `page` | the author opted out on CurseForge and Modrinth doesn't have the file |
| `repo` | git | the file is `<mod-src folder>-<version>.jar`, or the entry was already `repo` |

To keep any other jar in git, set its entry to `{"file": "...", "source": "repo"}`; regeneration preserves it. Also add `!mods/<file>` to `.gitignore`; `--generate` warns about a `repo` jar that is still ignored. `BetterSnowyFeatures`, `cinematic` and `tacz-firstperson-compat` are set this way: their `META-INF/mods.toml` declares MIT.

### Generate

Reads the jars in `mods/` and `minecraftinstance.json` (untracked, written by the CurseForge app). An existing entry with the same file name and SHA-1 is kept as it is, so a clone whose launcher data is incomplete doesn't lose sources. Jars that `.gitignore` names individually (`mods/<name>.jar`) are left out. A jar with no source stops the run without writing. The Modrinth lookup (`/v2/version_files`) sends only SHA-1 hashes.

### Fetch

1. Jars with the right size and SHA-1 are skipped and copied into the cache (`~/.cache/winterheart-mods/<sha1>.jar`, or `FETCH_MODS_CACHE`).
2. Missing or differing jars come from the cache, else are downloaded (8 at a time, 3 attempts) and verified before they replace anything.
3. Manual entries: the tool lists the pages, offers to open them, then checks `--downloads` (default `~/Downloads`) and `mods/` every second for a `.jar` with a matching size and SHA-1. The file name doesn't matter. Without a terminal, or with `--no-wait`, it lists them and exits 1.
4. `repo` entries are only checked for presence, because local rebuilds change their bytes.

A differing jar, or one that is no longer listed but matches what the tool recorded in `.cache/fetch-mods/installed.json`, is moved to `.cache/fetch-mods/replaced/`. Other unlisted jars only produce a warning.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `--downloads <dir>` | command line | `~/Downloads` | Folder watched for manual downloads |
| `--no-open` | command line | off | Don't offer to open pages |
| `--no-wait` | command line | off | Don't watch for manual downloads |
| `FETCH_MODS_CACHE` | environment | `~/.cache/winterheart-mods` | Download cache |

## Changes

- 2026-11-26: Untracked the 139 `curseforge`, `modrinth` and `manual` jars and ignored `mods/*.jar`. The files on disk did not change, so no game launch. Tested with a fresh local clone and an empty cache: 137 jars downloaded, only the 2 manual ones remained, and every jar present matched the instance byte for byte.
- 2026-11-26: Added. Tested on macOS in an empty scratch folder holding only `mods.json` and the `repo` jars, with an empty cache: 137 downloads and 2 manual pickups (a renamed copy in the watched folder, one saved into `mods/`; a wrong file and a `.crdownload` were ignored) finished in about 15 s, and all 150 jars were byte-identical to the instance's. Also tested re-run, `--check`, a tampered, a missing, an unlisted and a delisted jar, non-interactive manual, and a bad file name. Opening browser pages was not tested. No game launch, as no jar changed.

## Gotchas

- Pulling the commit that untracked the jars deletes them from an existing checkout (git refuses to if one was modified locally). Run `./fetch-mods.sh` right after.
- Git history still contains the old jars, so clones are no smaller.
- Checking out an older commit brings its jars back, and returning removes them again; run `./fetch-mods.sh` afterwards.
- A new own mod whose id doesn't start with `winterheart` needs its own `!mods/<id>-*.jar` line in `.gitignore`.
- Nothing runs this automatically (not `run-pack.sh`, not a git hook, not the CurseForge app).
- The first removal of an old jar needs a previous fetch run on that machine; before that it is only reported as unlisted.
- Resource packs and shader packs are not covered.
- Windows is untested, and the wrapper needs a bash shell.
- Seppuku's jar counts as our own (`repo`), although the launcher also lists it on CurseForge.
