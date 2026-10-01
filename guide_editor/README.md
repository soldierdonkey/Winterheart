# Guide editor

Edits `kubejs/server_scripts/questing/guides/guides.json`, the guide definitions read by the KubeJS guide engine.

    cd guide_editor
    cargo run --release

It finds the instance root by walking up to the folder containing `options.txt` and `kubejs/` (override with `WINTERHEART_DIR`).

- The book preview uses the book texture and font from the resource packs enabled in `options.txt`, then the vanilla jar.
- Valid blocks, items, entities, damage types and advancements come from ProbeJS (`kubejs/probe/generated/globals.d.ts`).
  Re-run `/probejs dump` in game, then press "Reload ProbeJS", after adding mods.
- Edits are validated and written automatically about 0.4s after you stop typing. Invalid data is never written.
  The game re-reads the file every 2 seconds (or run `/guides reload`).
- Mouse wheel over the book, or its arrows, turns pages. The list order is the page order.
