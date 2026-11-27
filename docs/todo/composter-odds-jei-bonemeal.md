# composter-odds-jei-bonemeal

A mixin on JEI's composting page that shows each item's composter chance and the rough chance of getting a bone meal from it. JEI already shows the composter chance; the bone meal figure is missing.

- **Feature:** `composter-odds` (new; no feature doc yet)
- **Added:** 2026-11-27
- **Progress:** 0/5 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [ ] Pick the mod that holds the mixin (proposed: a new client-only mod in `mod-src/`) and check in a running game that composters follow the vanilla rules below
- [ ] Propose how to fit both numbers on the page and pick one
- [ ] Mixin into `mezz.jei.library.plugins.vanilla.compostable.CompostableRecipeCategory` (`createRecipeExtras`) to draw both
- [ ] Test headlessly and in a running game
- [ ] Add the feature doc, docs index row, feature map entry and CHANGELOG.md line

## Files

Paths this todo is expected to touch (repo-relative, in backticks).

- `mod-src/`
- `mods/`
- `docs/features/composter-odds.md`
- `docs/README.md`
- `docs/feature-map.md`
- `CHANGELOG.md`

## Notes

Decisions, open questions, dead ends. Newest at the bottom, dated.

- 2026-11-27: Replaces the todo `butchery-jei-composting`. The page covers every compostable item, not only Butchery's.
- 2026-11-27: JEI 15.56.0.205: the category is 120x18, with the slot on the left and "Chance: N%" (`gui.jei.category.compostable.chance`) centred in the space to its right. The recipe gives the chance through `IJeiCompostingRecipe.getChance()`.
- 2026-11-27: Bone meal math, from vanilla 1.20.1 rules as recalled (confirm in the first step). The composter needs 7 layers for 1 bone meal, and the first item in an empty composter always adds a layer. With chance p, one bone meal takes 1 + 6/p items on average, so the chance per item is p / (p + 6): 30% → 4.8% (1 per 21), 50% → 7.7% (1 per 13), 65% → 9.8%, 85% → 12.4%, 100% → 14.3% (1 per 7). It assumes the composter is filled with that item only.
- 2026-11-27: Not covered: Butchery's own composter right-click for the item tag `butchery:compostable`, which nothing was found to define. JEI's page reads only the vanilla composter list.
