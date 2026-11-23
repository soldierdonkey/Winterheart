# winterheart-core

Our own Forge mod `winterheart` (`mod-src/winterheart/`): the core the other Winterheart mods and the datapacks build on. It registers the pack's custom items and the limb poising effect, adds the recipe type the saltpeter chain needs, replaces MoreJS's brewing changes, and converts old `kubejs:` item IDs. Step 2 of [general-kubejs-removal](../todo/general-kubejs-removal.md).

## Files

- `mod-src/winterheart/`: mod source (Java, ForgeGradle 6). Own README and CHANGELOG. Built with `./gradlew build` in that folder, which copies the jar to `mods/`.
- `mod-src/winterheart/src/main/java/io/github/soldierdonkey2/winterheart/Winterheart.java`: entry point, wires everything below.
- `.../item/ModItems.java`: every item (`winterheart:<metal>_<part>` metal parts, `saltpeter`, `crude_nitrate_bottle`, `potassium_nitrate_bottle`, `guide_book`, `lost_page`). `TooltipItem.java`: grey tooltip from the lang key `<item key>.tooltip`.
- `.../item/SmeltPopOut.java`: returns the glass bottles when saltpeter is taken out of a furnace.
- `.../effect/ModEffects.java`: `winterheart:limb_poising` (the effect shown by [limb-poisoning](../feature-map.md#mechanics-kubejs)).
- `.../recipe/ModRecipes.java`, `.../recipe/RemainderShapelessRecipe.java`: recipe type `winterheart:shapeless_with_remainders`.
- `.../brewing/BrewingChanges.java`: removes every brewing recipe, adds crude nitrate + charred planks → potassium nitrate.
- `.../compat/IdRemap.java`: `kubejs:<item>` → `winterheart:<item>` for the five moved items.
- `mod-src/winterheart/src/main/resources/assets/winterheart/`: `textures/item/*.png`, `textures/mob_effect/limb_poising.png`, `models/item/*.json` and `lang/en_us.json` (the last two generated).
- `python/core/gen_item_assets.py`: generates the item models and `en_us.json`. Its `ITEMS` and `EXTRA_LANG` tables hold the display names and tooltips.
- `python/core/metal_parts.py`: draws the 24 metal part textures (needs Pillow).
- `python/structures/remap_item_ids.py`: rewrites item IDs inside structure NBT and SNBT files.
- `config/paxi/resourcepacks/winterheart_assets/`: Paxi resource pack holding the guide book item models (`models/item/guide_book*.json`), written by `tools/guide_editor`.
- `mods/winterheart-1.0.0.jar`: the built jar.

## How it works

**Items.** Registered with a `DeferredRegister`. Metal parts are a loop over `METALS` × `PARTS`; a plain item each. `crude_nitrate_bottle` and `potassium_nitrate_bottle` stack to 16, `guide_book` to 1. `lost_page` is a single dummy item: each page differs only by NBT (`lostpage: "<id>"`, defined in the guides' `guides.json`). `guide_book` has no model in the jar: its models come from `winterheart_assets`.

**Saltpeter chain.** Rich soil + 4 purified water → 4 crude nitrate bottles (recipe `winterheart:crude_nitrate_from_rich_soil`, type `winterheart:shapeless_with_remainders`, the soil becomes dirt) → brewing stand with charred planks → potassium nitrate bottle → furnace → saltpeter (`winterheart:saltpeter_from_smelting`) → gunpowder (`winterheart:gunpowder_from_saltpeter`). Recipes are in the [recipes](recipes.md) pack. `SmeltPopOut` listens to `PlayerEvent.ItemSmeltedEvent` for `winterheart:saltpeter`; it spawns one glass bottle per saltpeter at the furnace block the player is looking at (`player.pick`, 6 blocks), or gives them to the player if that block isn't a furnace.

**`winterheart:shapeless_with_remainders`.** A vanilla shapeless recipe plus `"remainders": {"<ingredient item>": "<item left behind>"}`. `getRemainingItems` fills a slot from the map only if the ingredient has no remainder of its own. Wraps the vanilla `ShapelessRecipe` serializer for JSON and network.

**Brewing.** On `FMLLoadCompleteEvent` (after every mod added its recipes) `BrewingChanges` empties the private `BrewingRecipeRegistry.recipes` list by reflection. That list holds the vanilla recipe (which covers all potions, splash and lingering) and any mod's. Then it adds the one recipe above. Forge has no API for removal, so a Forge change to that field would turn this into an error log (`Could not clear the brewing recipes`) and potions would work as in vanilla.

**ID remap.** `IdRemap` handles `MissingMappingsEvent` for the `kubejs` namespace and remaps `saltpeter`, `crude_nitrate_bottle`, `potassium_nitrate_bottle`, `guide_book` and `lost_page` to `winterheart:`. Forge also keeps the old name as an alias, so `kubejs:saltpeter` in item NBT of an old world's chests and inventories still resolves. It only fires for worlds whose saved registry lists these IDs (all existing ones). Item IDs inside structure templates in datapacks are not covered: those are rewritten in the files (`python/structures/remap_item_ids.py`).

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `METALS`, `PARTS` | `ModItems.java` and `python/core/gen_item_assets.py` | 4 metals × 6 parts | Metal part items (keep both in step) |
| `ITEMS`, `EXTRA_LANG` | `python/core/gen_item_assets.py` | see file | Names, tooltips, models |
| `ITEMS` (remap) | `IdRemap.java` | 5 items | Old `kubejs:` IDs that are converted |
| `CHARRED_PLANKS` | `BrewingChanges.java` | `immersive_weathering:charred_planks` | Brewing ingredient |

## Changes

- 2026-11-22: Created the mod (version 1.0.0) from the template. Moved the items, the `winterheart:limb_poising` effect and their textures out of `kubejs/startup_scripts/` and `kubejs/assets/winterheart/`; MoreJS's brewing changes, the saltpeter leaching recipe (`ServerEvents.recipes` + `replaceIngredient`) and the smelt pop-out (`ItemEvents.smelted`) are now Java. Same behaviour, new IDs: `kubejs:{saltpeter,crude_nitrate_bottle,potassium_nitrate_bottle,guide_book,lost_page}` → `winterheart:*`. Updated every reference: guides (`guides.json`, `guides_engine.js`, `guide_book_jei.js`, README), the guide editor (`tools/guide_editor`, now writes book models into `winterheart_assets`), `config/starterkit/kits/*.txt`, `config/jei_plus_plus/recipe_defaults.json`, `python/recipes/gen_recipes.py` and the structure templates (see [structures](structures.md)). The lang entry `biome.winterheart.spawn_clearing` moved from `kubejs/assets/winterheart/lang/en_us.json` into the mod's. Binary: `mods/winterheart-1.0.0.jar`.
  Tested: headless smoke test on a copy of the `Smoke Test` world saved with the old IDs. Passed (joined in 51–57 s). Log: `Removed 45 brewing recipes`, five `Remapped item kubejs:… -> winterheart:…` lines. A temporary probe script (removed afterwards) confirmed: all new items exist and the five old IDs resolve to them; the effect is registered; `winterheart:crude_nitrate_from_rich_soil` loads as `RemainderShapelessRecipe` and leaves dirt in the soil's slot and nothing else; the brewing registry holds 1 recipe; crude nitrate + charred planks brews potassium nitrate solution; water bottle + nether wart doesn't brew. Not tried by hand: crafting, the brewing stand GUI, the pop-out, item models and the book models in the inventory, tooltips.

## Gotchas

- The jar is built in `mod-src/winterheart/` and copied to `mods/` (tracked). Rebuild and commit the jar together with Java changes.
- `METALS`/`PARTS` exist in `ModItems.java` and in `python/core/gen_item_assets.py` (and `python/core/metal_parts.py`, `python/recipes/gen_recipes.py`). Change all of them together.
- Not yet in this mod (planned for the steps that need them, see the todo): the shared sanity/temperature API and test-utils.
- The pop-out finds the furnace by where the player looks, as the old script did. Taking items with shift-click from a furnace the player isn't looking at drops the bottles into the inventory.
