# butchery-jei-composting

Show in JEI which Butchery items go in a composter. JEI may already cover all of them, so check that first.

- **Feature:** `butchery-jei` ([doc](../features/butchery-jei.md))
- **Added:** 2026-11-27
- **Progress:** 0/4 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [ ] In a running game, check whether JEI's own composting page lists the six rotten organs, and whether anything has the item tag `butchery:compostable`
- [ ] If items are missing, propose layouts and pick one; if none are, close this todo
- [ ] Build the page and test it headlessly and in a running game
- [ ] Update feature doc, feature map and CHANGELOG.md

## Files

Paths this todo is expected to touch (repo-relative, in backticks).

- `mod-src/winterheart_butchery/`
- `python/butchery_jei/`
- `docs/features/butchery-jei.md`
- `CHANGELOG.md`

## Notes

Decisions, open questions, dead ends. Newest at the bottom, dated.

- 2026-11-27: From static reading of the 5.2 jar, not confirmed in game. Butchery adds `butchery:rotten_heart`, `rotten_lungs`, `rotten_kidney`, `rotten_liver`, `rotten_intestines` and `rotten_stomach` to the vanilla composter list at 30%, which JEI's composting page reads. It also has its own composter right-click for the item tag `butchery:compostable` (30% to raise the level), but neither the jar nor Winterheart's datapacks were found to define that tag.
