# tacz-guns

Timeless and Classics Zero (TACZ) guns, trimmed to authentic pre-1950s weapons, with their ammo and attachments, plus Winterheart's gunsmith recipes.

## Files

- `tacz/tacz_default_gun/`: TACZ's default gun pack, kept in the repo with the non-period guns, attachments and ammo deleted.
- `tacz/tacz-pre.toml`: `DefaultPackDebug = true`, so TACZ doesn't overwrite our edited default pack on start.
- `tacz/.export-state.json`: TACZ's own export hash for the default pack.
- `python/tacz/purge_guns.py`, `purge_attachments.py`, `purge_ammo.py`: one-off scripts that deleted everything not in their `ALLOWED_*` sets from the gun pack's `data/tacz/{data/guns,index,recipes}/`.
- `kubejs/server_scripts/recipes/guns.js`: gunsmith recipes (`addGunSmithRecipe` helper matching TACZ's serializer).
- `config/tacz-{common,client}.toml`: TACZ settings.

## How it works

TACZ loads gun packs from `tacz/` in the instance. Because `DefaultPackDebug` is on, the trimmed `tacz_default_gun` stays as committed. `guns.js` registers `tacz:gun_smith_table_crafting` recipes (with explicit ids) for the kept guns, ammo and attachments.

The purge scripts resolve the gun pack from their own location (`GUNPACK`), so they can be run from anywhere:

```sh
python3 python/tacz/purge_guns.py   # deletes immediately; set dry_run=True in __main__ to preview
```

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `ALLOWED_GUNS` / `ALLOWED_ATTACHMENTS` / `ALLOWED_AMMO` | `python/tacz/purge_*.py` | period set | What survives a purge |
| `DefaultPackDebug` | `tacz/tacz-pre.toml` | `true` | Keeps TACZ from restoring the default pack |

## Changes

- 2026-11-22: Moved the purge scripts out of the gun pack (`tacz/tacz_default_gun/remove{,2,3}.py`) to `python/tacz/purge_{guns,attachments,ammo}.py`, so the pack no longer contains Python files. They used to mix paths relative to the instance root and to the gun pack. Now all their target dirs are anchored to `GUNPACK`. Checked by importing each module (which doesn't run the purge): all six target dirs resolve and exist. The purges were not re-run.

## Gotchas

- The purge scripts delete without asking (`dry_run=False` in `__main__`). Run them only on a clean tree, so git can undo them.
- With `DefaultPackDebug = false`, TACZ would restore every deleted gun on the next start.
