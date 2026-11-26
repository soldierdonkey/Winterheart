# Guide editor

Edits `datapacks/winterheart_guides/data/winterheart/guides/guides.json`, the guide definitions read by the `winterheart_guides` mod.

    cd tools/guide_editor
    cargo run --release

It finds the instance root by walking up to the folder containing `options.txt` and `datapacks/` (override with `WINTERHEART_DIR`).

- The book preview uses the book texture and font from the resource packs enabled in `options.txt`, then the vanilla jar.
- Valid blocks, items, entities, damage types, advancements and their tags come from `tools/guide_editor/registry_ids.json`.
  Run `/winterheart dump_ids` in game (it writes `config/winterheart/registry_ids.json`), copy that file over the snapshot, then press "Reload registry", after adding mods.
- Edits are validated and written automatically about 0.4s after you stop typing. Invalid data is never written.
  The game re-reads the file every 2 seconds (or run `/guides reload`).
- Mouse wheel over the book, or its arrows, turns pages. The list order is the page order.
