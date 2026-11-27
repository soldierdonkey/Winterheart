# AGENTS.md

Rules for contributors working in this repo. For the project layout see [README.md](README.md).

**Start with [docs/feature-map.md](docs/feature-map.md).** It maps every custom file to a feature name. Use it to find what a change touches. Keep it current: when you add, move or remove custom files, or add a feature, update the map and the index in [docs/README.md](docs/README.md) in the same commit.

## Rules

1. **Document every feature.** Each feature gets `docs/features/<feature-name>.md`, created from [docs/_template.md](docs/_template.md). Docs cover backend behaviour, data flow and changes, to help contributors. They are not a player guide.
2. **Update docs after every change.** If you touch a feature's files, update its doc in the same commit.
3. **Map files to changes.** Each feature doc has a **Files** section listing every relevant path (repo-relative, one per line, in backticks). Add new paths as you touch them.
4. **Keep it greppable and consistent.**
   - Use one kebab-case feature name everywhere: doc filename, doc title, `docs/README.md` index row, CHANGELOG entry, commit prefix.
   - Use exact IDs in docs (`legendarysurvivaloverhaul:recovery`, `datapacks/winterheart_worldgen`), not paraphrases.
   - Use the same term for the same thing. Grep for old usages before renaming anything.
   - Put each fact in one place and link to it.
5. **Keep commits small.** One logical change per commit, staging only the intended files (`git add <paths>`, never `git add -A`). Prefix the message with the feature name: `time-system: shorten sleep cooldown`. For repo-wide files with no feature, use the file name: `readme:`, `agents:`. Docs go in the same commit as the change they describe.
6. **Update the changelog.** For every player-visible change, add a line to the topmost (unreleased) section of [CHANGELOG.md](CHANGELOG.md) under the matching heading, starting with the feature name.
7. **Smoke-test load-time changes headlessly.** Run `./run-pack.sh --headless --world "<save>"` after any change that:
   - alters control flow in a Winterheart mod (`mod-src/`),
   - adds, removes or updates a jar in `mods/`, or
   - edits a datapack (`datapacks/`).

   `<save>` is a folder name in `saves/`. Confirm that the game loads and joins the world, then check `logs/latest.log` for errors from the change (the Winterheart mods log under their own names, e.g. `[Guides]`, `[TimeSys]`, `[Spawner]`, `[FixedStructures]`). Record the result (pass/fail, load time, notable log lines) in the feature doc's Changes entry. Datapack JSON changes alone can be tested with `/reload`, and resource pack changes with F3+T, in a running game. Logic that depends on the server ticking (time, spawning, limb poising) can't be exercised by the hidden headless client, which barely ticks the server: drive it directly from a temporary probe or test in a running game, and say which you did. If the instance is already running and the test can't run, say so rather than skipping it silently.
8. **Commit once a change works.** When a change roughly works (the headless test passes, or the user confirms it), commit it without waiting to be asked. Follow rule 5: feature-name prefix, only intended files, docs in the same commit. The message's first line says what changed. The body says why, and how it was tested.

## Checklist before committing

- [ ] Feature doc created/updated, Files section current
- [ ] `docs/README.md` index row exists
- [ ] CHANGELOG.md updated (if player-visible)
- [ ] Only intended files staged; commit is one logical change
- [ ] Tested in the instance (or stated that it wasn't)
- [ ] Headless load test passed (if rule 7 applies)

## Conventions

- Commit directly to `main` unless asked otherwise. Commit automatically per rule 8. Don't push unless the user asks.
- Don't edit untracked or generated files (logs, saves, `minecraftinstance.json`).
- Binary mod or resource pack changes: note the file name and version in the feature doc and CHANGELOG.
- Third-party jars in `mods/` and zips in `resourcepacks/` are not tracked. After adding, updating or removing a mod or resource pack, run `./fetch.sh --generate` and commit `mods.json`. See [docs/features/fetch.md](docs/features/fetch.md).
- **Where things go:**
  - Datapack data (recipes as JSON, tags, worldgen, structures, loot tables, mod data files) goes in a pack `datapacks/winterheart_<area>/`; [packfinder](docs/features/packfinder.md) loads everything in that folder. Tuning that a Winterheart mod reads lives there too (JSON re-read on `/reload`).
  - Logic and registrations go in a Winterheart mod, never a script. Item and mob effect textures/models/lang go in the mod's `src/main/resources/assets/`; assets that tools write (guide book models) go in `resourcepacks/winterheart_assets/`.
  - An in-house pack has a `README.md`, `CHANGELOG.md` and `pack.properties` in its root so `./publish.sh` can upload it ([mod-publishing](docs/features/mod-publishing.md)).
  - Our own mods go in `mod-src/<mod_id>/`. Start new ones from `mod-src/_template/new-mod.sh`.
  - Python/shell generators and helper scripts go in `python/<area>/`. Rust apps go in `tools/<name>/`.
- Run scripts and tools from the instance root unless their docs say otherwise.

## Todos

Workflow in [docs/todo/README.md](docs/todo/README.md).

- **Bare todo path given** (e.g. `docs/todo/butchery-fixes-missing-block-tags.md`): first check whether the todo is fleshed out enough to complete (clear steps, decisions made, nothing left to ask). If yes, complete it. If no, run `/resolve` on it.
