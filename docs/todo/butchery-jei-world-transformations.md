# butchery-jei-world-transformations

JEI pages for processes that change the world instead of giving a product: the special bone meals, and wetting and cleaning with a rag or sponge.

- **Feature:** `butchery-jei` ([doc](../features/butchery-jei.md))
- **Added:** 2026-11-26
- **Progress:** 5/6 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [x] Confirm each conversion from Butchery 5.2
- [x] Propose a layout for showing a block or fluid before and after, and pick one
- [x] Build the special bone meal pages
- [x] Build the wetting and cleaning pages
- [ ] Test headlessly, then check the pages in a running game
- [x] Update feature doc, feature map and CHANGELOG.md

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
- 2026-11-27: Confirmed by static reading of 5.2, not in game. `butchery:icey_bone_meal`: water source to ice, always. `butchery:sculkbonemeal`: grass block to sculk, 20%, used up either way. `butchery:wither_bone_meal` on the upper half of a rose bush: 20% a wither rose, otherwise the bush is destroyed. A water source or water cauldron wets a `butchery:rag` for 5 cleans and a `butchery:sponge` for 10. The cauldron seems not to lose water (likely a bug). Cleaning removes `butchery:blood_puddle`; no cleaning of butcher's tables was found, unlike the earlier note.
- 2026-11-27: Chosen layout M1 (before and after previews) and C1 (one page per cleaning tool). Built and tested headlessly. Left: check the pages in a running game.
