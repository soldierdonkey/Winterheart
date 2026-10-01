# Guide system

Pages of lore and instructions that unlock as players play. Each page belongs to a **book**. When a page unlocks,
every player gets a book with just that page and the server pauses until they close it. Anyone can also reread
everything they have unlocked with a guide book item.

## Files

| File | Role |
| --- | --- |
| `guides/guides.json` | All data: books, guides (pages) and their unlock triggers. Edited with the editor. |
| `guides/guides_engine.js` | Loads the JSON, evaluates triggers, opens books, handles the pause. |
| `guides/guides_commands.js` | `/guides ...` debug and admin commands. |
| `startup_scripts/items/guide_book.js` | Registers the item `kubejs:guide_book`. |
| `client_scripts/guide_book_watcher.js` | Tells the server when the popup book is closed. |
| `client_scripts/guide_book_jei.js` | Lists each book in JEI. |
| `guide_editor/` (instance root) | Rust/macroquad editor for `guides.json`. |

Small hooks live in `mechanics/time_system.js` (`triggerPhaseChangeHook`) and `mechanics/proximity.js` (the
"reached 10s threshold" branch). Each is one guarded call into `global.Guides`.

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
- **JSON can't hold code.** A regex is `{"regex": "zomb"}`. `test` (custom sweep) and `where` (extra filter) are
  the *names* of functions you register: `global.GuideFunctions.my_test = ctx => ...`.

## Triggers

A guide's `unlock` is an array; **any** trigger unlocks it, and it unlocks for the whole world no matter which
player triggered it. Unlocked ids are stored in the overworld's persistent data under
`winterheart_guides_unlocked`.

**Sweeps** run twice a second for every player and stay true while the condition holds:
`standing_on`, `standing_in`, `looking_at_block`, `looking_at_entity` (both take `distance`), `inventory`
(`item`, `count`), `holding` (`item`, `hand`), `time` (phase, `minDay`/`maxDay`), `custom` (`test`).

**Events** fire at the moment something happens:
`block_broken`, `block_placed`, `block_used`, `item_used`, `item_crafted`, `item_smelted`, `item_picked_up`,
`item_eaten`, `entity_killed`, `entity_hurt`, `player_hurt` (damage type, optional `by` attacker, `minDamage`),
`player_died`, `advancement`, `time_phase` (phase begins; `minDay`/`maxDay`), `proximity` (a rule id from
`proximity.js`), `custom` (fired from code with `global.Guides.fire(player, 'name')`).

**Matchers** (`match`, `item`, `by`, `holding`): an id, a `#tag`, a regex object, or an array of those. Omitting
`match` means anything.

**Extras on any trigger:** `holding` plus `hand` (`main`, `off`, or either) requires a held item as well, and `where`
adds a custom filter.

Damage types match either the message id (`inFire`) or the registry id (`minecraft:in_fire`).
`time_phase` has no player, so `where` gets no `ctx.player`, and a `holding` requirement is satisfied by any online player.

## Books and items

All books share one item, `kubejs:guide_book`. The item's NBT carries `guidebook: "<book id>"`, and its name and
lore come from the JSON when it is created, so adding a book needs no restart.

- Right-click opens that book's **unlocked** pages. An untagged item, or one whose book was deleted, opens the first book.
- Get one with `/guides give <book> [player]`.
- A book with a `texture` shows its own icon through `CustomModelData` (`model` is assigned once by the editor and
  kept stable). The editor writes the model files to `kubejs/assets/kubejs/models/item/` on every save; press F3+T
  in game to see texture changes.
- JEI lists one entry per book, read from the local `guides.json`. Rejoin the world after editing books.
  JEI++'s `nbtGroupingEnabled` must be off, or it folds the books into one stack.

## What happens on unlock

1. `guideUnlock` stores the id and queues a popup. Popups are shown one at a time.
2. For each player, a written book containing only that page is swapped into their main hand, synced, opened with
   `openItemGui`, and the real item is put back, all in the same tick. (The vanilla open-book packet only works on
   the held item.)
3. The server is paused with `/pause` (Multiplayer Server Pause), issued in the same tick. That command only
   *toggles*, and fails if every client is already on a pause screen. The engine reads the mod's `forcePause` flag
   so it never undoes a pause an op made by hand.
4. `guide_book_watcher.js` notices the book screen close and sends `winterheart_guide_closed`. When every viewer has
   closed it (or logged out), the engine unpauses and shows the next queued popup.

If a pause ever sticks, run `/guides resume`.

## Hot reload

The engine re-reads `guides.json` every 2 seconds. A valid change replaces the guides live and tells ops in chat.
An invalid file is rejected with the reason in the server log, and the old guides keep running. `/guides reload`
forces a re-read. Changes to the engine scripts themselves need `/kjs reload server_scripts`. New items need a game restart.

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
| `/guides probe` | Show what the sweeps see for you and whether each sweep trigger passes. |
| `/guides trace` | Toggle live output: sweep state on the action bar, every event in chat and the log. |
| `/guides fire <name>` | Fire a custom event as yourself. |
| `/guides reload`, `resume` | Re-read the file; release a stuck pause. |

## Editor

```
cd guide_editor && cargo run --release
```

It finds the instance root by walking up to the folder with `options.txt` and `kubejs/` (override with
`WINTERHEART_DIR`). It renders pages with the book texture and font from your enabled resource packs, takes valid
ids from ProbeJS (`kubejs/probe/generated/globals.d.ts`; re-run `/probejs dump` after adding mods), and reads time
phases and proximity rule ids from the two mechanics scripts. Edits are validated and written about 0.4 s after
you stop typing; invalid data is never written. See `guide_editor/README.md` for more.

The trigger kinds are a table in `guide_editor/src/model.rs` mirroring the engine, so a new kind added to the
engine needs a matching row there.

## Extending

- Unlock from other scripts: `global.Guides.unlock(server, 'id')` (pass `{ silent: true }` to skip the popup).
- Fire a custom event: `global.Guides.fire(player, 'name', data)`, matched by `{ "event": "custom", "match": "name" }`.
- Settings such as the sweep interval and pause behaviour are in `GUIDE_CONFIG` at the top of `guides_engine.js`.
