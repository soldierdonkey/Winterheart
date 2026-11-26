# packfinder

Own mod (`packfinder`) that keeps every pack in `resourcepacks/` and `datapacks/` enabled, in a fixed priority order. It replaced Paxi, so third-party packs can stay where the CurseForge app installs them.

## Files

- `mod-src/packfinder/src/main/java/io/github/soldierdonkey2/packfinder/PackFinder.java`: mod entry point, adds the pack sources on `AddPackFindersEvent`.
- `mod-src/packfinder/src/main/java/io/github/soldierdonkey2/packfinder/FolderPacks.java`: lists one folder, reads its order file, sorts its packs.
- `mod-src/packfinder/src/main/java/io/github/soldierdonkey2/packfinder/mixin/PackRepositoryMixin.java`: applies the order to the selected packs.
- `mod-src/packfinder/src/main/resources/packfinder.mixins.json`, `mod-src/packfinder/build.gradle`, `mod-src/packfinder/settings.gradle`: mixin setup.
- `config/packfinder/resourcepack_order.json`: resource pack priority.
- `mods/packfinder-1.0.0.jar`: built jar.

## How it works

On `AddPackFindersEvent` the mod adds one `RepositorySource` per pack type: `resourcepacks/` for client resources, `datapacks/` for server data (created if missing). It finds zip and folder packs with vanilla's `FolderRepositorySource.discoverPacks` and builds each as a required pack positioned at the top.

| Pack type | Pack ID | Why |
| --- | --- | --- |
| Resource pack | `file/<name>` | Same ID as vanilla's own entry. Sources added by mods run after vanilla's and replace it, so the pack is listed once and can't be disabled. |
| Datapack | `<name>` | The IDs Paxi used, so existing worlds keep them, and no clash with a world's `file/` datapacks. |

`PackRepositoryMixin` runs after `PackRepository.rebuildSelected` and moves these packs above all other selected packs, in order-file order, whatever order `options.txt` or the world saved.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `loadOrder` | `config/packfinder/resourcepack_order.json` | all current packs | File or folder names, lowest priority first. Unlisted packs load alphabetically below the listed ones. |
| `loadOrder` | `config/packfinder/datapack_order.json` | file absent | Same, for `datapacks/`. |

Edits apply on F3+T (resource packs) or `/reload` (datapacks).

## Changes

- 2026-11-26: Added, replacing Paxi (`Paxi-1.20-Forge-4.0.jar` removed). The `winterheart_*` datapacks moved from `config/paxi/datapacks/` to `datapacks/`, and `winterheart_assets` and `TheForestCustomTextures.zip` to `resourcepacks/`. The resource order was seeded from the order the game loaded before the change. First headless run crashed: the newly enabled `Omega-Colorful-Lighting-Compat.zip` needs Colorful Lighting and broke shader compilation, so it and the other Colorful Lighting compat pack were removed. Second headless run (`--world "New World"`, with `/datapack list` and `/reload`) passed: joined in 38 s, 26 resource packs loaded once each in order-file order, all 8 `winterheart_*` datapacks enabled, `[Guides]` loaded 24 guides, and no new ERROR lines compared with the previous launch. Not checked by eye in game, and not tested on a new world.

## Gotchas

- Any pack a player drops into `resourcepacks/` or `datapacks/` is forced on. One that needs a missing mod can crash the game at load.
- A pack update that renames its file drops it to the unlisted group until the order file is updated. The log warns `[PackFinder] ... lists <name>, which is not a pack`.
- A world last played with Paxi logs `Missing data pack mod:paxi` once.
- `curseforge_project_id` is empty, so the jar ships bundled until the mod has a CurseForge project.
