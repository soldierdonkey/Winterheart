# butchery-jei-world-transformations

JEI pages for processes that change the world instead of giving a product: the special bone meals, and wetting and cleaning with a rag or sponge.

- **Feature:** `butchery-jei` ([doc](../features/butchery-jei.md))
- **Added:** 2026-11-26
- **Progress:** 0/6 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [ ] Confirm each conversion from Butchery 5.2
- [ ] Propose a layout for showing a block or fluid before and after, and pick one
- [ ] Build the special bone meal pages
- [ ] Build the wetting and cleaning pages
- [ ] Test headlessly, then check the pages in a running game
- [ ] Update feature doc, feature map and CHANGELOG.md

## Files

Paths this todo is expected to touch (repo-relative, in backticks).

- `mod-src/winterheart_butchery/`
- `python/butchery_jei/`
- `docs/features/butchery-jei.md`
- `CHANGELOG.md`

## Notes

Decisions, open questions, dead ends. Newest at the bottom, dated.

- 2026-11-26: Bone meals (Butchery's guide book): `butchery:icey_bone_meal` turns water source blocks to ice, `butchery:sculkbonemeal` turns grass blocks to sculk, `butchery:wither_bone_meal` can turn roses into wither roses (by chance).
- 2026-11-26: Wetting and cleaning: `butchery:rag` and `butchery:sponge` are wetted on a water source or a full cauldron, then clean blood off floors and butcher's tables. Not confirmed in game.
