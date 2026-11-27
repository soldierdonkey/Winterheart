# butchery-jei-salt-basin

Show in JEI how a basin makes salt.

- **Feature:** `butchery-jei` ([doc](../features/butchery-jei.md))
- **Added:** 2026-11-26
- **Progress:** 4/5 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [x] Confirm the conditions, time and yield from Butchery 5.2
- [x] Propose layouts and pick one
- [x] Build the page
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

- 2026-11-26: Butchery's guide book: fill `butchery:basin` with water; outdoors in daylight and without rain it leaves `butchery:salt` after about half a Minecraft day. Not confirmed in game.
- 2026-11-27: Confirmed by static reading of 5.2, not in game. A water bucket fills an empty basin. Every 2000 ticks, if it is day, the basin can see the sky and it isn't raining, it makes 1 `butchery:salt`, up to 6 per bucket (at least 12000 eligible ticks). Salt can be taken, one per right-click, only after all the water has gone. A full basin can be emptied back into a bucket.
- 2026-11-27: Chosen layout B1: one row, the basin cycling its stages. Built and tested headlessly. Left: check the pages in a running game.
