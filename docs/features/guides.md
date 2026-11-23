# guides

Pages of lore and instructions that unlock as players play. Each page belongs to a **book**. When a page unlocks,
every player gets a book with just that page and the server pauses until they close it. Anyone can also reread
everything they have unlocked with a guide book item.

## Files

- `config/paxi/datapacks/winterheart_guides/data/winterheart/guides/guides.json`: all data: books, guides (pages), lost pages and their unlock triggers. Edited with the guide editor; `pack.mcmeta` next to it.
- `mod-src/winterheart_guides/`: the mod (Java). `GuideManager` (loading, unlock store, popup flow), `GuideData` (parsing, validation), `Spec` (matchers), `Sweeps` (sweep triggers), `EventHooks` (event triggers and the engine tick), `Books` (rendering, opening, items), `Pause` (Multiplayer Server Pause), `GuideCommands` (`/guides`), `GuideApi` (called by other mods), `GuideFunctions` (named predicates), `net/GuideNet`, `client/GuideClient` (tells the server the popup book was closed), `client/GuidesJei` (JEI plugin).
- `mod-src/winterheart/.../item/ModItems.java`: the items `winterheart:guide_book` and `winterheart:lost_page` ([winterheart-core](winterheart-core.md)).
- `config/paxi/resourcepacks/winterheart_assets/assets/winterheart/models/item/guide_book*.json`: book item models, written by the editor.
- `mods/winterheart_guides-1.0.0.jar`: the built jar.
- `tools/guide_editor/`: Rust/macroquad editor for `guides.json`.

The time system calls `GuideApi.firePhase` ([time-system](time-system.md)); proximity effects call `GuideApi.fireProximity` ([proximity-effects](proximity-effects.md)).

## Data format

```json
{
  "books": [
    { "id": "journal", "name": "Kaamos's Journal", "author": "...", "tooltip": "...",
      "titleColor": "dark_red", "texture": "minecraft:item/writable_book", "model": 1 }
  ],
  "guides": [
    { "id": "introduction", "title": "Introduction", "book": "journal",
      "content": "Text with § codes.", "unlock": [ { "event": "block_used", "match": "minecraft:spruce_door" } ] }
  ]
}
```

- **Page order** in a book is the order of its guides in the `guides` array.
- **Ids** are `a-z`, `0-9` and `_`. Unlock state is stored by guide id, so renaming a guide re-locks it.
- **Old files** with no `books` list get one default book, and guides with no `book` join the first book.
- A guide's page is a bold title (in the book's `titleColor`, default dark red), a blank line, then the content.
  A page fits 14 lines of 114 px. The editor warns when text overflows, and the game cuts the rest off.
- **Book fields:** `name` (item name and written-book title, cut to 32 characters because vanilla rejects longer
  titles), `author`, `tooltip` (item lore), `titleColor` (a color name or `#rrggbb`), `texture` and `model` (see below).
- **JSON can't hold code.** A regex is `{"regex": "zomb"}` (Java regex, found anywhere in the id). `test` (custom sweep) and `where` (extra filter) are
  the *names* of Java predicates registered with `GuideFunctions.register("my_test", ctx -> ...)`; an unknown name is a validation error.

## Triggers

A guide's `unlock` is an array; **any** trigger unlocks it, and it unlocks for the whole world no matter which
player triggered it. Unlocked ids are stored in the world state (`WorldState`, key
`winterheart_guides_unlocked`; imported from KubeJS's file on first load).

**Sweeps** run twice a second for every player and stay true while the condition holds:
`standing_on`, `standing_in`, `looking_at_block`, `looking_at_entity` (both take `distance`), `inventory`
(`item`, `count`), `holding` (`item`, `hand`), `time` (phase, `minDay`/`maxDay`), `position`, `custom` (`test`).

`position` checks where the player's feet are; every part is optional but at least one is needed:
`minY`/`maxY` (inclusive), `x` + `z` + `radius` (horizontal distance, a cylinder), and `dimension`.
Above Y 240 within 20 blocks of -1100, 0 (the beacon): `{ "sweep": "position", "minY": 240, "x": -1100, "z": 0, "radius": 20 }`.

**Events** fire at the moment something happens:
`block_broken`, `block_placed`, `block_used`, `item_used`, `item_crafted`, `item_smelted`, `item_picked_up`,
`item_eaten`, `entity_killed`, `entity_hurt`, `player_hurt` (damage type, optional `by` attacker, `minDamage`),
`player_died`, `advancement`, `time_phase` (phase begins; `minDay`/`maxDay`), `proximity` (a rule id from
the proximity rules), `custom` (fired from code with `GuideApi.fire(player, "name")`).

**Matchers** (`match`, `item`, `by`, `holding`): an id, a `#tag`, a regex object, or an array of those. Omitting
`match` means anything.

**Extras on any trigger:** `holding` plus `hand` (`main`, `off`, or either) requires a held item as well, and `where`
adds a custom filter.

Damage types match either the message id (`inFire`) or the registry id (`minecraft:in_fire`).
`time_phase` has no player, so `where` gets no `ctx.player`, and a `holding` requirement is satisfied by any online player.

## Books and items

All books share one item, `winterheart:guide_book`. The item's NBT carries `guidebook: "<book id>"`, and its name and
lore come from the JSON when it is created, so adding a book needs no restart.

- Right-click opens that book's **unlocked** pages. An untagged item, or one whose book was deleted, opens the first book.
- Get one with `/guides give <book> [player]`.
- A book with a `texture` shows its own icon through `CustomModelData` (`model` is assigned once by the editor and
  kept stable). The editor writes the model files to `config/paxi/resourcepacks/winterheart_assets/assets/winterheart/models/item/` on every save; press F3+T
  in game to see texture changes.
- JEI lists one entry per book, read from the local `guides.json`. Rejoin the world after editing books.
  JEI++'s `nbtGroupingEnabled` must be off, or it folds the books into one stack.

## Lost pages

`winterheart:lost_page` is a dummy item (empty map texture) that carries `lostpage: "<id>"` in its NBT. Pages are defined
by a top-level `"pages"` list in `guides.json` (also editable in the editor under "Lost pages"):

```json
"pages": [ { "id": "torn_page", "name": "Torn Page", "tooltip": "Water-stained." } ]
```

- Match one in any item matcher (`item`, `match` on item triggers, `holding`) with `{ "page": "torn_page" }`;
  the editor offers it as `page` / `+ lost page`. Plain `"winterheart:lost_page"` matches every lost page.
- Get one with `/guides page <id> [player]`; `/guides pages` lists them. JEI lists one entry per page.
- Adding the item needs a game restart once; adding pages afterwards does not.

## What happens on unlock

1. `GuideManager.unlock` stores the id and queues a popup. Popups are shown one at a time.
2. For each player, a written book containing only that page is swapped into their main hand, synced, opened with
   `openItemGui`, and the real item is put back, all in the same tick. (The vanilla open-book packet only works on
   the held item.)
3. The server is paused with `/pause` (Multiplayer Server Pause), issued in the same tick. That command only
   *toggles*, and fails if every client is already on a pause screen. The engine reads the mod's `forcePause` flag
   so it never undoes a pause an op made by hand.
4. `GuideClient` notices the book screen close and sends `winterheart_guide_closed`. When every viewer has
   closed it (or logged out), the engine unpauses and shows the next queued popup.

If a pause ever sticks, run `/guides resume`.

## Hot reload

`GuideManager` re-reads `guides.json` every 2 seconds. A valid change replaces the guides live and tells ops in chat.
An invalid file is rejected with the reason in the server log, and the old guides keep running. `/guides reload`
forces a re-read. Engine changes need a rebuilt jar and a restart.

## Commands (permission level 2)

| Command | Does |
| --- | --- |
| `/guides list`, `info <id>`, `books`, `status` | Inspect guides, books and engine state. |
| `/guides unlock <id>` | Real unlock: popup and pause for everyone. |
| `/guides grant <id\|all>` | Unlock silently. |
| `/guides lock <id\|all>` | Relock, so a trigger can be tested again. |
| `/guides popup <id>` | Replay the popup and pause without changing unlock state. |
| `/guides show <id>` | Preview one page on your own screen only. |
| `/guides book [<book>]`, `bookall <book>` | Open a book; `bookall` includes locked pages, marked `[locked]`. |
| `/guides give <book> [player]` | Give a guide book item. |
| `/guides pages`, `page <id> [player]` | List lost pages; give a lost page item. |
| `/guides probe` | Show what the sweeps see for you and whether each sweep trigger passes. |
| `/guides trace` | Toggle live output: sweep state on the action bar, every event in chat and the log. |
| `/guides fire <name>` | Fire a custom event as yourself. |
| `/guides reload`, `resume` | Re-read the file; release a stuck pause. |

## Editor

```
cd tools/guide_editor && cargo run --release
```

It finds the instance root by walking up to the folder with `options.txt` and `kubejs/` (override with `WINTERHEART_DIR`). It renders pages with the book texture and font from your enabled resource packs, takes valid ids from ProbeJS (`kubejs/probe/generated/globals.d.ts`), and reads time phases and proximity rule ids from the two mechanics scripts (these three sources go away with KubeJS: the editor needs new ones in steps 4, 5 and 9 of the removal todo). Edits are validated and written about 0.4 s after you stop typing; invalid data is never written. See `tools/guide_editor/README.md`.

The trigger kinds are a table in `tools/guide_editor/src/model.rs` mirroring the engine (`GuideData.SWEEP_KINDS` / `EVENT_KINDS`), so a new kind needs a matching row there.

## Extending (from Java)

- Unlock: `GuideApi.unlock(server, "id", silent)`.
- Fire a custom event: `GuideApi.fire(player, "name")`, matched by `{ "event": "custom", "match": "name" }`. Time phases and proximity rules: `GuideApi.firePhase`, `GuideApi.fireProximity`.
- Named predicates for `test` / `where`: `GuideFunctions.register`.
- Settings (sweep interval, pause, book title) are constants at the top of `GuideManager`.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `SWEEP_INTERVAL_TICKS` | `GuideManager.java` | 10 | Ticks between sweep checks |
| `RELOAD_CHECK_TICKS` | `GuideManager.java` | 40 | Ticks between `guides.json` re-reads |
| `PAUSE_ON_UNLOCK` | `GuideManager.java` | true | Pause the server while the popup is open |
| `LOOK_DISTANCE` | `Sweeps.java` | 16 | Ray length for `looking_at_*` |

## Changes

- 2026-11-22: Ported from KubeJS (`guides_engine.js`, `guides_commands.js`, `guide_book_watcher.js`, `guide_book_jei.js`) to the Java mod `winterheart_guides`, and moved `guides.json` from `kubejs/server_scripts/questing/guides/` to the Paxi pack `winterheart_guides` (step 7 of [general-kubejs-removal](../todo/general-kubejs-removal.md), done before 4 and 5 so Java can call it). Unlock state moved from KubeJS's overworld persistent data to the new `WorldState` (winterheart-core), which imports KubeJS's `kubejs_persistent_data.nbt` on a world's first load, so existing worlds keep their unlocked guides. The guide editor now reads and writes the new path. Differences: `test` / `where` name Java predicates instead of JS functions; `hasTag` and damage-type matching use Forge registry lookups; the `/guides probe` output drops the KubeJS-specific ray fields. The `time_phase` trigger and `time` sweep work again since the time system moved to Java (step 4), and the `proximity` trigger since proximity effects did (step 5).
  Tested: headless runs on a copy of a world saved by the KubeJS version. `GuideManager` loaded 24 guides in 2 books and 2 lost pages with no problems; 13 guides came back unlocked from the imported data and `custom_day` 16 was imported; unlock then lock round-tripped; the sweep snapshot and evaluation ran (one sweep passing); page JSON, the two book items and the lost page item had the expected NBT; `openFullBook` ran; a forced reload succeeded; `/guides` registered with 19 subcommands; the JEI plugin added 2 books and 2 pages. The popup opened for the player, took the pause (`ownsPause`) and the client started watching. **Not verified:** the close signal (the hidden client can't close the book, as in the old script), the pause release, `/guides` command output, event triggers (breaking, crafting, ...) and the right-click on the guide book item.

## Gotchas

- `guides.json` is read from the instance folder on the client too (JEI). On a dedicated server, clients need their own copy.
- JEI++'s `nbtGroupingEnabled` must be off, or it folds the books into one stack.
- The popup pauses the server with `/pause` and waits for every player to close the book. If a pause ever sticks, run `/guides resume`.
