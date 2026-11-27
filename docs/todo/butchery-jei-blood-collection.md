# butchery-jei-blood-collection

Show in JEI how blood is taken from a blood grate.

- **Feature:** `butchery-jei` ([doc](../features/butchery-jei.md))
- **Added:** 2026-11-26
- **Progress:** 0/5 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [ ] Confirm containers, amounts and blood types from Butchery 5.2
- [ ] Propose layouts and pick one
- [ ] Build the page
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

- 2026-11-26: A bleeding carcass fills a `butchery:blood_grate` below it ([carcass-butchering](../features/carcass-butchering.md#4-bleeding)); a bottle or bucket takes the blood out. Infected blood has its own bottle and bucket. Not confirmed in game.
