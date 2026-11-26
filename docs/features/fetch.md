# fetch

`./fetch.sh` fills `mods/` and `resourcepacks/` from the tracked manifest `mods.json` and verifies every third-party file by SHA-1. `./fetch.sh --generate` rewrites the manifest. Only the `repo` files are tracked in git; `.gitignore` ignores every other jar in `mods/` and zip in `resourcepacks/`.

## Files

- `THIRD_PARTY_NOTICES.md`: license notice for the third-party jars tracked in git.
- `.gitignore`: ignores `mods/*.jar` and `resourcepacks/*.zip`, with a `!` line for each `repo` file (own mods by pattern).
- `fetch.sh`: wrapper. Builds the tool with cargo and sets `FETCH_GAME_DIR` to the instance root.
- `mods.json`: the manifest, one entry per shipped jar (`mods`) and resource pack zip (`resourcepacks`).
- `tools/fetch/Cargo.toml`, `tools/fetch/Cargo.lock`: Rust crate.
- `tools/fetch/src/main.rs`: options.
- `tools/fetch/src/manifest.rs`: manifest format, validation, hashing.
- `tools/fetch/src/generate.rs`: `--generate`.
- `tools/fetch/src/fetch.rs`: fetching, `--check`, manual download watching.

## How it works

```
./fetch.sh              # download missing mods and resource packs, and verify
./fetch.sh --check      # report drift, change nothing, exit 1 if out of sync
./fetch.sh --generate   # after adding, updating or removing a mod or resource pack
```

Run `./fetch.sh` after cloning, and after any pull that changes `mods.json`. After changing mods or resource packs in the CurseForge app, run `--generate`, read the `+`/`~`/`-` lines and commit `mods.json`.

Both lists work the same way. For resource packs:

- Only `.zip` files directly in `resourcepacks/` are listed. Pack folders (`winterheart_assets`) are tracked in git.
- `datapacks/` isn't covered; every pack there is our own.
- A new or renamed pack may also need its line in `config/packfinder/resourcepack_order.json` ([packfinder](packfinder.md)).

### Sources

| `source` | Where the file comes from | Set by `--generate` when |
| --- | --- | --- |
| `curseforge` | CurseForge CDN `url` | the launcher data matches the jar and `allowModDistribution` is not false |
| `modrinth` | Modrinth CDN `url` | not `curseforge`, and Modrinth knows the SHA-1 |
| `manual` | the user's browser, from `page` | the author opted out on CurseForge and Modrinth doesn't have the file |
| `repo` | git | the file is `<mod-src folder>-<version>.jar`, or the entry was already `repo` |

To keep any other file in git, set its entry to `{"file": "...", "source": "repo"}`; regeneration preserves it. Also add `!mods/<file>` or `!resourcepacks/<file>` to `.gitignore`; `--generate` warns about a `repo` file that is still ignored. `BetterSnowyFeatures` and `tacz-firstperson-compat` are set this way: their `META-INF/mods.toml` declares MIT. [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md) carries their license notice; add or remove a row there when a third-party `repo` file changes.

### Generate

Reads the jars in `mods/`, the zips in `resourcepacks/` and `minecraftinstance.json` (untracked, written by the CurseForge app). An existing entry with the same file name and SHA-1 is kept as it is, so a clone whose launcher data is incomplete doesn't lose sources. Files that `.gitignore` names individually (`mods/<name>.jar`) are left out. A file with no source stops the run without writing. The Modrinth lookup (`/v2/version_files`) sends only SHA-1 hashes.

### Fetch

1. Files with the right size and SHA-1 are skipped and copied into the cache (`~/.cache/winterheart-mods/<sha1>.jar` or `.zip`, or `FETCH_CACHE`).
2. Missing or differing jars come from the cache, else are downloaded (8 at a time, 3 attempts) and verified before they replace anything.
3. Manual entries: the tool lists the pages, offers to open them, then checks `--downloads` (default `~/Downloads`) and the target folders every second for a `.jar` or `.zip` with a matching size and SHA-1. The file name doesn't matter. Without a terminal, or with `--no-wait`, it lists them and exits 1.
4. `repo` entries are only checked for presence, because local rebuilds change their bytes.

A differing file, or one that is no longer listed but matches what the tool recorded in `.cache/fetch/installed.json`, is moved to `.cache/fetch/replaced/` (packs under `replaced/resourcepacks/`). Other unlisted files only produce a warning.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `--downloads <dir>` | command line | `~/Downloads` | Folder watched for manual downloads |
| `--no-open` | command line | off | Don't offer to open pages |
| `--no-wait` | command line | off | Don't watch for manual downloads |
| `FETCH_CACHE` | environment | `~/.cache/winterheart-mods` | Download cache |

## Changes

- 2026-11-26: Added `THIRD_PARTY_NOTICES.md` for the two third-party `repo` jars. Documentation only.
- 2026-11-26: Removed the unused `cinematic-1.1.1.jar` (a `repo` jar). Headless load of `New World`: pass, joined in 30 s; the only related lines are the expected missing-mod warnings (`cinematic (version 1.1.1 -> MISSING)`, `Missing data pack mod:cinematic`).
- 2026-11-26: Renamed from `fetch-mods` to `fetch`: the script, `tools/fetch/`, the crate and binary, the environment variables (`FETCH_*`) and the state folder (`.cache/fetch/`). The download cache path is unchanged. Tested with `./fetch.sh --check`; no game launch, as no jar changed.
- 2026-11-26: Added the `resourcepacks` list and untracked the 24 third-party resource pack zips; the files on disk did not change, so no game launch. Tested in an empty scratch folder with an empty cache and only the resource pack list: 20 downloads and 4 manual pickups (renamed copies in the watched folder) left all packs byte-identical to the instance's. Also tested `--check`, a tampered, a missing and a delisted pack. The `mods` list came out unchanged.
- 2026-11-26: Rewrote history with `git filter-repo`, dropping `mods/`, `saves/`, `tacz_backup/`, `screenshots/` and two removed resource packs, then re-added the `repo` jars. The tracked files were identical before and after; the git data went from about 591 MiB to 100 MiB.
- 2026-11-26: Untracked the 139 `curseforge`, `modrinth` and `manual` jars and ignored `mods/*.jar`. The files on disk did not change, so no game launch. Tested with a fresh local clone and an empty cache: 137 jars downloaded, only the 2 manual ones remained, and every jar present matched the instance byte for byte.
- 2026-11-26: Added. Tested on macOS in an empty scratch folder holding only `mods.json` and the `repo` jars, with an empty cache: 137 downloads and 2 manual pickups (a renamed copy in the watched folder, one saved into `mods/`; a wrong file and a `.crdownload` were ignored) finished in about 15 s, and all 150 jars were byte-identical to the instance's. Also tested re-run, `--check`, a tampered, a missing, an unlisted and a delisted jar, non-interactive manual, and a bad file name. Opening browser pages was not tested. No game launch, as no jar changed.

## Gotchas

- A clone made before the rewrite must be replaced with a fresh clone. Pushing from it would restore the old history.
- History was rewritten on 2026-11-26 to drop old jars. Older commits contain none, and `mods.json` exists only from the commit that added this tool.
- A new own mod whose id doesn't start with `winterheart` needs its own `!mods/<id>-*.jar` line in `.gitignore`.
- Nothing runs this automatically (not `run-pack.sh`, not a git hook, not the CurseForge app).
- The first removal of an old jar needs a previous fetch run on that machine; before that it is only reported as unlisted.
- A checkout that ran the tool before the rename keeps its record in the old state folder under `.cache/`; rename it to `.cache/fetch/` to keep it.
- Shader packs are not covered.
- Pulling the commit that untracked the resource packs deletes them from an existing checkout; run `./fetch.sh` right after.
- Windows is untested, and the wrapper needs a bash shell.
- Seppuku's jar counts as our own (`repo`), although the launcher also lists it on CurseForge.
