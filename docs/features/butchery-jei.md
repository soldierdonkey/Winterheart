# butchery-jei

Four JEI categories for Butchery's hard-coded processes: tanning, skull crushing, acid, and `winterheart_butchery:carcass`, showing Butchery's carcass workflows as one scrollable page per carcass: 102 pages: one for each carcass and humanoid corpse (colour variants separately, plus the skinned cat, pufferfish and ravager), and five for iron golems (the corpse, the repaired golem, and its arms, body and legs). The cow's behaviour is described in [carcass-butchering](carcass-butchering.md), which also holds the plan for [custom butchering](carcass-butchering.md#jei-support-planned).

## Files

- `mod-src/winterheart_butchery/`: the client-only mod. `client/ButcheryJei.java` (JEI plugin), `client/CarcassCategory.java` (slots), `client/CarcassWidget.java` (scrolling page), `client/CarcassModels.java` (entity and carcass rendering), `client/Tools.java` (tool tags), `client/CountlessItemRenderer.java` (ranged drop icons), `client/Carcass.java` (data)
- `mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/jei/carcasses.json`: generated page data
- `mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/lang/en_us.json`: labels and tooltips
- `python/butchery_jei/carcasses.json`: hand-kept table of each carcass's items, mob, preview stages, tools and loot tables
- `python/butchery_jei/processes.json`: hand-kept rules for tanning, skull crushing and acid
- `mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/jei/processes.json`: generated data for those three
- `mod-src/winterheart_butchery/src/main/java/io/github/soldierdonkey2/winterheart_butchery/client/`: also `Processes.java`, `ProcessCategory.java`, `TanningCategory.java`, `SkullCategory.java`, `AcidCategory.java`
- `python/butchery_jei/gen_carcass_data.py`: adds the drops to that table and writes the mod's `carcasses.json`
- `mods/winterheart_butchery-1.0.0.jar`: built jar
- `config/jei/recipe-category-sort-order.ini`: JEI added the category to its order

## How it works

Butchery hard-codes which steps a carcass has, so `python/butchery_jei/carcasses.json` records them (read from the 5.2 jar). Loot tables are not sent to the client, so `gen_carcass_data.py` reads each step's tables from the jar (or a datapack override) and writes the drops into the mod's `assets/winterheart_butchery/jei/carcasses.json`, using `Organs`, `Instant Bleeding` and `Farmers Delight` from `config/Butchery.toml`. `processes.json` goes through the same script. The plugin reads both when JEI starts. Tools come from the item tags `forge:`/`c:cleaver`, `forge:`/`c:skinning_knives` and `butchery:boss_tool`; knives that are also cleavers are left out of the skinning slot. The mob and carcass are drawn from the game's own entity and block models, so no Butchery assets are copied. A carcass drawn by a block entity renderer shows as its item. Block previews face the viewer and sway 30 degrees either way (`CarcassModels.angle`). The player corpse page draws a default-skin player.

Spider and cave spider pages preview the carcass laid down, because Butchery's hanging spider models cross the legs through each other.

Iron golems are taken apart with a hacksaw (`forge:`/`c:hacksaw`). A step that takes several clicks (iron golems 3, chicken plucking 4) says so on its tool slot, and its drops are the total over those clicks.

Steps list only what drops without Looting. The repaired golem page has a repair step in place of bleeding: the corpse and 3 iron ingots are its inputs. Each golem part page takes either the plain or the repaired part.

A slot with a range label (`5-6`) hides the item's stack count, which holds the minimum for JEI++, so the two don't overlap.

A page shows the steps for a carcass **laid down**. Where a hung carcass gives different totals, a footer line says so and lists them on hover.

### Tanning, skull crushing and acid

| Category | Pages | Inputs | Outputs | Behaviour shown |
| --- | --- | --- | --- | --- |
| `winterheart_butchery:tanning` | 1, cycling the 28 skins | skin, salt | `minecraft:leather` | Place on `butchery:skin_rack`, salt (`forge:`/`c:salt`), then wet with a wet `butchery:sponge` or `butchery:rag`, wait 90 s, take the leather |
| `winterheart_butchery:skull` | 1 per drop group (7) | placed head | the group's drops | One right-click with a hammer (`forge:`/`c:hammer`) |
| `winterheart_butchery:acid` | 1 per skeleton (25) | uncut carcass, `butchery:bottle_of_sulfuric_acid` | skeleton | 11 s; the glass bottle comes back, shown but not an output so JEI++ doesn't offer acid as a bottle source |

Which heads and carcasses a page lists comes from Butchery's block tags when JEI starts. Each head goes to the first group that matches, in the order of `processes.json`. A group or result whose tags are empty gets no page.

### Slot roles

[JEI++](https://www.curseforge.com/minecraft/mc-mods/jei-plus-plus) builds recipe trees from input and output slots only.

| Slot | Role | Effect |
| --- | --- | --- |
| Fresh carcass | input, plus an invisible output | A tree for any drop costs one carcass |
| Every drop | output | Count is the guaranteed minimum (1 for a drop that isn't guaranteed); the range is drawn on the slot |
| Tools, `butchery:hook`, `butchery:rope` | catalyst | Never counted |
| Drained or repaired carcass | catalyst, plus an invisible output | Never counted |
| Repair material | input | Counted: 3 iron ingots for a repaired golem |

Looking up a fresh or drained carcass with R (recipes) or U (uses) opens the page. Skinning a cat leaves `butchery:cat_carcass`, which is an output of each cat page and the input of the skinned cat page, so a tree for skinned cat meat runs through both. JEI++ ignores the invisible outputs, because its tree only accepts a recipe whose output slots produce the item.

### Scrolling

JEI's scroll widgets take only a uniform slot grid or plain text, so `CarcassWidget` is a custom `ISlottedRecipeWidget` that owns every slot, moves them as it scrolls and clips to the viewport. The viewport height is chosen from the window height (`CarcassCategory.getHeight`); the page scrolls only when it doesn't fit. Wheel and scroll bar both work the offset.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `Organs` | `config/Butchery.toml` | true | Whether organ drops are listed (needs regenerating) |
| `Instant Bleeding` | `config/Butchery.toml` | false | Drops the bleed time from the label (needs regenerating) |
| `Farmers Delight` | `config/Butchery.toml` | false | Uses the `_fd_compat` loot tables (needs regenerating) |
| `Vampires Delight Eye` | `config/Butchery.toml` | false | Uses the vampire skull table (needs regenerating) |

Regenerate and rebuild, from the instance root:

```sh
python3 python/butchery_jei/gen_carcass_data.py
cd mod-src/winterheart_butchery && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew build
```

## Changes

- 2026-11-26: Added the mod with the cow page. Builds.
- 2026-11-26: R and U on both the fresh and the drained cow carcass open the page. Headless load test (`New World`): pass, joined after 32 s, log line `[Butchery] JEI: added 1 carcass page(s)`, no errors from the mod.
- 2026-11-26: Added every other carcass (80 pages), boss tools, per-species steps, wrapping drop rows and the hanging warning. Tested headlessly in `New World` with a temporary probe, since removed, that built, drew and scrolled all 80 pages in the running game and looked up sample carcasses with R and U: pass, no failures. How the pages look, and mouse input, have not been tried in game.
- 2026-11-26: Added the 16 humanoid corpses and the ravager (97 pages), and stopped stack counts overlapping range labels. Tested the same way: a temporary probe built and drew all 97 pages and created their mobs in `New World` with no failures. Appearance not checked in game.
- 2026-11-26: Added five iron golem pages (102 pages). Fixed drops: the ravager listed its Looting bonus (`ravager_body_drop`) on every leg step, the enderman and sniffer showed several steps as one (the sniffer with the wrong tool), and chicken plucking showed 1 of 4 feathers. Every step was then compared with the jar's logic. `butchery:ravager_carcass` and four enderman carcass items open their pages. Spider pages preview the carcass laid down; the ender dragon and long mobs are sized to fit. Tested headlessly in `New World` with a temporary probe, since removed: all 102 pages built, and screenshots of sample pages checked. Mouse input not tried.
- 2026-11-27: Added the tanning, skull crushing and acid categories. Tested headlessly in `New World` with a temporary probe, since removed: all 33 pages built, sample R and U lookups found them, and screenshots were checked. Mouse input not tried.
- 2026-11-27: Block previews faced away from the viewer; they now face front and sway instead of turning a full circle.

## Gotchas

- Not every carcass tracks its stage in the `blockstate` property: the enderman, sniffer, ravager and boss carcasses use a saved counter. When rechecking the table, follow each stage of that counter, and keep apart the loot rolled in Looting loops. The ravager's leg steps roll `ravager_body_drop`, saddle included, only as a Looting bonus.
- Farmer's Delight extras that Butchery spawns directly (not through a loot table) are not listed. They only drop with the `Farmers Delight` setting on.

- Golem parts give iron ingots, so JEI++ may offer a golem part page when choosing how a tree gets iron.

- `python/butchery_jei/carcasses.json` goes stale on a Butchery update; the generator only notices missing or unfamiliar loot tables. `carcasses.json` in the mod also goes stale if a loot table or one of the three config keys changes.
- `getHeight` mirrors JEI's internal screen sizing, assuming the default `recipeGuiHeight` of 350. Recheck it after updating JEI.
- The page's input is the fresh carcass, so JEI++ doesn't credit a drained carcass already in the inventory.
- Drops that aren't guaranteed count as 1 in a JEI++ tree, so trees through them are optimistic.
- Hanging is shown for carcasses that bleed. Others that can be hung are shown without it.

Behaviour of Butchery 5.2 seen by static reading, not confirmed in game:

| Carcass | Behaviour |
| --- | --- |
| Acid on a horse or wolf carcass | Butchery looks for the block tags `horse` and `wolf`, which don't exist, so nothing converts and there is no page |
| Iron golem heads | The `butchery:iron_heads` block tag is empty, so a hammer gives nothing and there is no page |
| Tanning order | The guide book says wet then salt, and allows a water bottle. The logic wants salt first, then a wet sponge or rag |
| Cow, hanging | The first cut asks for the loot table `cow_cut_1_drop` without its namespace, so only the organs drop |
| Bat, hanging | No heart or lungs |
| Phantom | One fewer cut hanging than laid down, so less fat and membrane |
| Pillager | Hanging gives less fat and meat, and no bones |
| Piglin brute | Hanging gives 1 bone instead of 1-3 |
| Villager | Hanging gives one fewer intestines and stomach |
| Player | Hanging gives one more `butchery:rawhumanmeat` |
| Pufferfish | The drop on death asks for `butchery:blocks/pufferfish_carcass_loot`, which doesn't exist. The page shows no kill step; [fishing](fishing.md) gives the item |
| `butchery:black_cat_carcass` | Black cats drop `butchery:all_black_cat_carcass`; no mob was found that drops this one |
