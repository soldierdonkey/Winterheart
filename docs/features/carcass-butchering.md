# carcass-butchering

Clean-room behaviour spec of how Butchery (`butchery-5.2-forge-1.20.1.jar`) turns a dead animal into a carcass and then into items. It is written so that someone who has never looked inside that jar can build the same behaviour, for example for [butchery-compat](../todo/butchery-compat-deer-mod.md). Winterheart does not implement any of it; [butchery-jei](butchery-jei.md) only displays it.

## Files

- `docs/features/carcass-butchering.md`: this spec.
- `config/Butchery.toml`: Butchery's settings; the keys below are read from it (owned by [mod-configs](mod-configs.md)).

## How it works

### Clean-room rules

- **Source:** static reading of the 5.2 jar (data files, in-game guide book, compiled logic) and `config/Butchery.toml`. Nothing here was confirmed in a running game.
- **Contents:** behaviour, numbers and public IDs only. No code, class names, models, textures, hitbox shapes or text from Butchery, which is All Rights Reserved.
- **Implementers:** work from this document. Don't open or decompile the jar. Whoever wrote or extends this spec from the jar should not write the implementation. If something is missing, ask for the spec to be extended.
- **Scope:** the **cow** is the reference animal. Other species differ in drops and sometimes in stages and tools, and this spec does not describe them.

"Must" below describes the observed behaviour. Behaviour that looks like a bug is listed under [Gotchas](#gotchas) so an implementer can decide whether to copy it.

### Overview

```
adult cow dies
  -> fresh carcass (item)
  -> hung from a hook or rope          cleaver: bleed, 45 s
  -> drained carcass (hanging, or picked up and laid down)
  -> cleaver: head
  -> skinning knife: skin
  -> cleaver: cut 1
  -> cleaver: cut 2
  -> cleaver: cut 3, carcass gone
```

### Terms and IDs

| Thing | ID or tag |
| --- | --- |
| Fresh carcass (block and item) | `butchery:cow_carcass` |
| Drained carcass (block and item) | `butchery:drained_cow_carcass` |
| Hanging supports | `butchery:hook`, `butchery:rope` |
| Blood collector | `butchery:blood_grate` |
| Cleaver (held item) | item tag `forge:cleaver` or `c:cleaver` |
| Skinning knife (held item) | item tag `forge:skinning_knives` or `c:skinning_knives` |
| Animals that give a cow carcass | entity type tag `forge:cow` or `c:cow` (Butchery lists `minecraft:cow`) |

A carcass block has a horizontal facing, a pose (lying or hanging), a fresh or drained condition and, once drained, a butchering stage.

### 1. Getting a carcass

When an entity in the cow tag dies, a carcass drops only if all of these hold:

1. It is not a baby.
2. The species switch (`Cow Carcass`) is on.
3. If `Only Cleavers Drop Carcasses` is on, the killer's main hand holds a cleaver or an item with Butchery's Butcher's Touch enchantment. If the setting is off, any death counts, including deaths with no killer.

Then:

- One fresh carcass item spawns where the animal died, with a 10-tick pickup delay.
- One tick later, every `minecraft:beef` and `minecraft:leather` item entity within 20 blocks is deleted. This is how vanilla drops are replaced.

If the conditions fail, vanilla drops are left alone.

### 2. Carrying

While `Weighted Carcasses` is on, a non-creative player with a carcass anywhere in their inventory gets Slowness, refreshed every tick. Carcasses are tagged light (`butchery:weighted_carcass_l`) or heavy (`butchery:weighted_carcass_ll`). Fresh and drained cow carcasses are heavy.

| Player's Strength | Light | Heavy |
| --- | --- | --- |
| none | Slowness I | Slowness II |
| Strength I | none | Slowness I |
| Strength II | none | none |
| Strength III or higher | none | Slowness II (see Gotchas) |

### 3. Placing and hanging

- **Lying:** placing the item as an ordinary block gives a lying carcass.
- **Hanging:** right-click a hook or rope while holding a fresh or drained carcass in either hand. The block directly below the support must be air. A hanging carcass of the same condition appears there, oriented to match the support, and one item is consumed (not in creative). The hook plays a chain sound. The rope plays its own sound.
- **Rope wear:** each hang advances the rope's visual state. After three hangs the rope is full, and right-clicking it with a carcass breaks the rope with no drop. Whether anything resets the rope was not traced.

A drained carcass counts as drained however it was placed.

### 4. Bleeding

**Trigger:** right-click a fresh, **hanging** carcass with a cleaver in the main hand, while it is not already bleeding. The cleaver takes no damage.

On the click: arm swing, a sweep-attack sound and a sticky (honey block) hit sound. The carcass is marked as bleeding.

**Timed bleed** (`Instant Bleeding` off):

- Blood drip particles fall from the carcass for about the whole duration: several in the first second, then one about every 28 ticks.
- Every 45 ticks, starting immediately, 50 mB of blood goes into a grate below, for 20 transfers (1000 mB in total).
- After 900 ticks (45 s) the block becomes a drained, hanging carcass with the same facing.
- If the carcass is removed before then, no further blood is transferred and nothing converts.

**Instant bleed** (`Instant Bleeding` on): on the click the carcass becomes drained, 1000 mB goes into a grate below at once, and one particle burst plays.

**Blood grate:** the carcass looks straight down up to 16 blocks and uses the first grate that is set to accept its blood. Blocks in between don't matter. The fluid is `butchery:blood`. The grate has other modes for other mods' blood, cycled with Butchery's butcher's papers item.

**Blood puddle** (`Blood Puddle` on): 40 ticks after a cleaver click on a fresh carcass, if there is no grate within 16 blocks below, a puddle block appears on the first floor below. With `Blood Spread` on it can then grow. The spreading pattern was not traced.

### 5. Butchering stages

Only drained carcasses can be butchered. No table is needed: both the lying and the hanging pose work, wherever the block is (but see the hanging first cut under [Gotchas](#gotchas)). Each right-click with the right tool in the main hand does exactly one stage, in this order:

| Stage | Tool | Loot table (`butchery:blocks/…`) | Drops (cow) | With `Organs` on |
| --- | --- | --- | --- | --- |
| 1. Head | cleaver | `cow_head_drop` | 1 `butchery:cow_head` | nothing extra |
| 2. Skin | skinning knife | `cow_skin_drop` | 1–2 `butchery:cow_skin` | nothing extra |
| 3. Cut 1 | cleaver | `cow_cut_1_drop` | `butchery:animal_fat`, `butchery:raw_chuck_steak`, `butchery:raw_ribeye_steak`, 2 `butchery:hoof` | `butchery:heart`, `butchery:lungs` (`organs_drop_1`) |
| 4. Cut 2 | cleaver | `cow_cut_2_drop` | `butchery:animal_fat`, `butchery:raw_rump_steak`, `butchery:raw_sirloin_steak` | 2 `butchery:kidney`, `butchery:liver` (`organs_drop_2`) |
| 5. Cut 3 | cleaver | `cow_cut_3_drop` | `butchery:animal_fat`, `butchery:raw_tbone_steak`, 2 `butchery:hoof`, 1–4 `minecraft:bone` | `butchery:intestines`, `butchery:stomach` (`organs_drop_3`) |

Quantities are 1 unless stated. Totals without Looting: 1 head, 1–2 skins, 3 fat, 5 steaks, 4 hooves, 1–4 bones, plus 7 organ items.

The wrong tool for the current stage does nothing.

Every successful stage:

- Costs the tool 1 durability (normal Unbreaking rules apply). The tool breaks when it runs out.
- Swings the arm and plays an axe-strip sound (volume 0.3) and a sticky (honey block) step sound.
- Shows block-break particles of the carcass.
- Changes the carcass model to show what has been removed.
- Drops its items at the carcass with a 10-tick pickup delay.

Stage 5 removes the block and adds bone-block break particles. It leaves no skeleton.

### 6. Looting

With `Looting Enchantment` on, Looting on the held tool can re-roll a stage's loot table. This applies to stages 2–5, and to the organ table when organs are on. Stage 1 (head) never gets a bonus. The extra rolls for each table are decided independently:

| Looting level | 0 extra | 1 extra | 2 extra |
| --- | --- | --- | --- |
| I | always | – | – |
| II | 1/2 | 1/2 | – |
| III | 1/3 | 4/9 | 2/9 |
| IV or higher | always | – | – |

The drop tables list Fortune bonuses, but they are rolled with no tool in context, so Fortune has no effect.

### 7. Breaking a placed carcass

Applies when a player breaks the block. In creative nothing drops.

| State when broken | Drop |
| --- | --- |
| Fresh | the fresh carcass item |
| Drained, no stage done | the drained carcass item |
| Drained, one or more stages done | 1 `minecraft:beef`; remaining loot is lost |

The block's own loot table drops nothing, so other kinds of destruction (such as explosions) give nothing. Block hardness is 1 and blast resistance 10.

### Acceptance checks

1. Killing an adult cow gives one carcass and no vanilla beef or leather. Killing a calf gives no carcass.
2. A lying fresh carcass cannot be bled or butchered.
3. A hung carcass clicked with a cleaver drains after 45 s and fills a grate below with 1000 mB.
4. A drained carcass gives drops only in the order head, skin, cut 1, cut 2, cut 3, then disappears.
5. A whole drained carcass can be picked up and butchered on the ground with the same results.
6. Breaking a part-butchered carcass gives one raw beef.

### Not covered

Other species and their special tools (hacksaws, hammers), boss carcasses, butcher's tables, hook and rope placement and support rules, what happens when a support is broken, head and skin processing, the meat items and their recipes, and Farmer's Delight or Vampirism compatibility.

## JEI support (planned)

How custom butchering, such as [butchery-compat](../todo/butchery-compat-deer-mod.md), should appear in the carcass pages of [butchery-jei](butchery-jei.md). This section describes Winterheart's own mod, not Butchery. **Not built yet:** the mod reads only its own data file.

### Goal

A mod adds pages by shipping a data file. It needs no Java code and no dependency on `winterheart_butchery` or JEI. Its pages behave like the built-in ones: R and U on the carcasses, JEI++ recipe trees, scrolling.

### Changes needed in `winterheart_butchery`

1. Read `assets/<namespace>/jei/carcasses.json` from every namespace and merge the `animals` lists. Skip and log a file that fails to parse, without losing the others.
2. Name each page `<namespace>:carcass/<id>`, so two mods can use the same `id`. JEI++ saves recipe choices by this name, so existing `winterheart_butchery` names must stay.
3. Move what is fixed in code into optional fields, defaulting to today's behaviour: `supports` (the hook and rope items), `tools` (item tags per tool name) and `state_property` (the preview block property).
4. Let a step name be any translation key, falling back to the built-in names.
5. Show the category when any file has pages, even without Butchery installed.

### Data format

`{"animals": [animal, ...]}`. Fields marked new don't exist yet.

| Field | Type | Meaning |
| --- | --- | --- |
| `id` | string | Page name, unique within the namespace |
| `entity` | entity ID, optional | Mob that drops the carcass. Without it there is no kill step |
| `entity_nbt` | SNBT string, optional | Picks a variant |
| `fresh` | item IDs | Carcass that a recipe tree costs |
| `drained` | item IDs, optional | Bled carcass. Required when `bleeds` is true |
| `bleeds` | boolean | Shows the hang and bleed steps |
| `bleed_seconds` | integer | 0 shows no time |
| `tray` | boolean | Notes that it is butchered on a metal tray |
| `preview` | list of `[block ID, state]` | Stages of the rotating model, in order; at least one. `state` is a string value of the block's `blockstate` property |
| `steps` | list | One per right-click, in order |
| `steps[].name` | string | `head`, `skin`, `plucking`, `armor`, `wing`, `leg`, `spike`, `heart`, `cut` (numbered) or `all` |
| `steps[].tool` | string | `cleaver`, `skinning_knife` or `boss` |
| `steps[].drops[]` | list | `item`, `min`, `max`, `chance` (true if not guaranteed), `organ` |
| `hanging_differences[]` | list, optional | `item`, `laid`, `hanging`: totals shown when hanging changes them |
| `supports`, `tools`, `state_property` | new, optional | See step 3 above |

### What a custom butchering mod must provide

- A preview block with an ordinary block model. A block drawn by a block entity renderer shows only as its item.
- Item and block IDs for every carcass, stage and drop.
- Drop numbers that match its loot tables. Generate the file from those tables so they can't drift apart; `python/butchery_jei/gen_carcass_data.py` shows the approach for Butchery.
- If it reuses Butchery's tools, nothing else: the default tool names already point at Butchery's tags.

### Acceptance checks

1. A mod that ships only the data file, items and blocks gets a page.
2. R and U on its fresh and drained carcasses open that page.
3. A JEI++ tree for one of its drops costs one fresh carcass and no tools.
4. A broken file logs an error and leaves other pages working.

## Config / tunables

All in `config/Butchery.toml`. "Pack" is the value Winterheart ships.

| Key | Section | Pack | Effect |
| --- | --- | --- | --- |
| `Cow Carcass` | `Mobs` | true | Cows drop carcasses |
| `Only Cleavers Drop Carcasses` | `General` | false | Restrict carcass drops to cleaver or Butcher's Touch kills |
| `Instant Bleeding` | `General` | false | Skip the 45 s bleed |
| `Organs` | `General` | true | Organ drops in cuts 1–3 |
| `Weighted Carcasses` | `General` | true | Carry slowness |
| `Looting Enchantment` | `General` | true | Looting bonus rolls |
| `Blood Puddle` | `General` | true | Puddles when no grate is below |
| `Blood Spread` | `General` | true | Puddles can spread |

## Changes

- 2026-11-26: Wrote the spec from `butchery-5.2-forge-1.20.1.jar`. Documentation only; nothing was tested in game.
- 2026-11-26: Added the planned [JEI support](#jei-support-planned) for custom butchering, and the hanging first cut gotcha, found by static reading.

## Gotchas

Observed in Butchery, or inferred from its logic where marked. An implementation does not need to copy these.

- **Drop cleanup is too broad:** it deletes all raw beef and leather item entities within 20 blocks, including ones players dropped.
- **Hanging first cut:** on a hanging cow, stage 3 asks for its loot table under an ID that doesn't exist, so only the organ drops appear. A lying cow gives the full stage 3 drops.
- **Looting I does nothing**, and so do levels above III.
- **Strength III or higher** brings back full heavy-carcass slowness.
- **Lying fresh carcass:** a cleaver click does nothing visible but still marks it as bleeding and can leave a puddle.
- **Repeat clicks** on a fresh carcass, even one already bleeding, can each trigger a puddle.
- **Replaced carcass:** the 45 s finish timer only checks that a hanging fresh cow carcass is at that position, so a replacement can drain early.
- **Stuck bleed (inferred):** the bleeding mark is saved with the block but the timer is not, so a carcass that is bleeding when the world closes should never finish. Breaking and rehanging it should reset it.
- **Dual-purpose knives:** Butchery puts `#farmersdelight:tools/knives` in both tool tags. An item in both is treated as a cleaver, so it can never skin. This reaches Winterheart, which ships `FarmersDelight-1.20.1-1.3.4.jar` and does not override those tags.
- **Creative mode** still wears down the tool when butchering.
