# butchery-jei

A JEI category, `winterheart_butchery:carcass`, showing Butchery's carcass workflow as one scrollable page per animal. Only the cow is covered. The behaviour it shows is described in [carcass-butchering](carcass-butchering.md).

## Files

- `mod-src/winterheart_butchery/`: the client-only mod. `client/ButcheryJei.java` (JEI plugin), `client/CarcassCategory.java` (slots), `client/CarcassWidget.java` (scrolling page), `client/CarcassModels.java` (entity and carcass rendering), `client/Tools.java` (tool tags), `client/Carcass.java` (data)
- `mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/jei/carcasses.json`: generated page data
- `mod-src/winterheart_butchery/src/main/resources/assets/winterheart_butchery/lang/en_us.json`: labels and tooltips
- `python/butchery_jei/gen_carcass_data.py`: generates `carcasses.json`
- `mods/winterheart_butchery-1.0.0.jar`: built jar
- `config/jei/recipe-category-sort-order.ini`: JEI added the category to its order

## How it works

Loot tables are not sent to the client, so `gen_carcass_data.py` copies the cow's drops from the Butchery jar (or a datapack override) into `carcasses.json`, reading `Organs` and `Instant Bleeding` from `config/Butchery.toml`. The plugin reads that file when JEI starts. Tools come from the item tags `forge:`/`c:cleaver` and `forge:`/`c:skinning_knives`; knives that are also cleavers are left out of the skinning slot. The animal and carcass are drawn from the game's own entity and block models, so no Butchery assets are copied.

### Slot roles

[JEI++](https://www.curseforge.com/minecraft/mc-mods/jei-plus-plus) builds recipe trees from input and output slots only.

| Slot | Role | Effect |
| --- | --- | --- |
| Fresh carcass | input, plus an invisible output | A tree for any drop costs one carcass |
| Every drop | output | Count is the guaranteed minimum; the range is drawn on the slot |
| Tools, `butchery:hook`, `butchery:rope` | catalyst | Never counted |
| `butchery:drained_cow_carcass` | catalyst, plus an invisible output | Never counted |

Looking up either carcass with R (recipes) or U (uses) opens the page. JEI++ ignores the invisible outputs, because its tree only accepts a recipe whose output slots produce the item.

### Scrolling

JEI's scroll widgets take only a uniform slot grid or plain text, so `CarcassWidget` is a custom `ISlottedRecipeWidget` that owns every slot, moves them as it scrolls and clips to the viewport. The viewport height is chosen from the window height (`CarcassCategory.getHeight`); the page scrolls only when it doesn't fit. Wheel and scroll bar both work the offset.

## Config / tunables

| Key | File | Default | Effect |
| --- | --- | --- | --- |
| `Organs` | `config/Butchery.toml` | true | Whether organ drops are listed (needs regenerating) |
| `Instant Bleeding` | `config/Butchery.toml` | false | Drops the bleed time from the label (needs regenerating) |

Regenerate and rebuild, from the instance root:

```sh
python3 python/butchery_jei/gen_carcass_data.py
cd mod-src/winterheart_butchery && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew build
```

## Changes

- 2026-11-26: Added the mod with the cow page. Builds.
- 2026-11-26: R and U on both the fresh and the drained cow carcass open the page. Headless load test (`New World`): pass, joined after 32 s, log line `[Butchery] JEI: added 1 carcass page(s)`, no errors from the mod. The page's rendering, scrolling and lookups have not been tried in game.

## Gotchas

- `carcasses.json` goes stale if Butchery, its loot tables or the two config keys change.
- A step holds at most 6 drops; another animal with more needs a second slot row in `CarcassWidget.layout`.
- The category height is sized for 5 steps, and several labels are fixed to the cow's flow (`1. Kill`, `2. Hang`, `3. Bleed`).
- `getHeight` mirrors JEI's internal screen sizing, assuming the default `recipeGuiHeight` of 350. Recheck it after updating JEI.
- The page's input is the fresh carcass, so JEI++ doesn't credit a drained carcass already in the inventory.
