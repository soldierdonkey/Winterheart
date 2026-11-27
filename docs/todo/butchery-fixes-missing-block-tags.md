# butchery-fixes-missing-block-tags

Butchery 5.2 reads block tags that are missing or empty, so sulfuric acid does nothing to horse and wolf carcasses, and a hammer does nothing to iron golem heads. Add the tags with a datapack.

- **Feature:** `butchery-fixes` (new; create `docs/features/butchery-fixes.md` from [../_template.md](../_template.md))
- **Added:** 2026-11-27
- **Progress:** 5/6 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [x] Create the pack `datapacks/winterheart_butchery/` with `README.md`, `CHANGELOG.md` and `pack.properties`
- [x] Add the block tags `forge:horse` and `forge:wolf` listing the fresh and drained carcasses; decide whether zombie horses belong
- [x] Add `butchery:iron_golem_head` and `butchery:repaired_iron_golem_head` to the block tag `butchery:iron_heads`
- [ ] Test headlessly, then in a running game: pour acid on a horse and a wolf carcass (lying and hanging), and hammer both golem heads
- [x] Check that JEI shows the horse and wolf acid pages and the iron head page
- [x] Update the feature doc, feature map, docs index, [butchery-jei](../features/butchery-jei.md) gotchas and CHANGELOG.md

## Files

Paths this todo is expected to touch (repo-relative, in backticks).

- `datapacks/winterheart_butchery/`
- `docs/features/butchery-fixes.md`
- `docs/features/butchery-jei.md`
- `docs/feature-map.md`
- `docs/README.md`
- `CHANGELOG.md`

## Notes

Decisions, open questions, dead ends. Newest at the bottom, dated.

- 2026-11-27: From static reading of the 5.2 jar, not confirmed in game. Acid looks for the block tags `forge:`/`c:horse` and `forge:`/`c:wolf`; neither exists. Butchery ships `forge:horse_carcass` and `forge:drained_horse_carcass` instead, which acid doesn't read; their lists are uneven (the first includes zombie horses and some drained carcasses, the second only five drained ones), so list the IDs rather than referencing them. Only the acid logic was found reading `forge:horse` and `forge:wolf` as block tags.
- 2026-11-27: `data/butchery/tags/blocks/iron_heads.json` in the jar is an empty file. The golem heads are in `butchery:unbreakable_heads`, which keeps them out of the ordinary head drops, so they should reach the iron head branch once tagged.
- 2026-11-27: The JEI pages read these tags when JEI starts, so no change to `winterheart_butchery` is expected.
- 2026-11-27: Zombie horses included, as in Butchery's own `forge:horse_carcass`. Correction to the note above: the jar has no `iron_heads` block tag file at all (only an item tag).
- 2026-11-27: Headless test passed and JEI shows the three new pages. Left: try acid and the hammer in a running game.
