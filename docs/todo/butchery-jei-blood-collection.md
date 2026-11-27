# butchery-jei-blood-collection

Show in JEI how blood is taken from a blood grate.

- **Feature:** `butchery-jei` ([doc](../features/butchery-jei.md))
- **Added:** 2026-11-26
- **Progress:** 1/5 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [x] Confirm containers, amounts and blood types from Butchery 5.2
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
- 2026-11-27: Confirmed by static reading of 5.2, not in game. A glass bottle takes 333 mB, a bucket 1000 mB; the grate holds 16000 mB. Most carcasses give 1000 mB. Chicken, rabbit, cat, ocelot and bat give 250 mB. Phantom gives 1000 mB timed and 250 mB with `Instant Bleeding`. Zombie, zombie villager, zombified piglin, zoglin and zombie horse give `butchery:infected_blood`. Husks drop sand instead of blood. The grate is found up to 16 blocks below; the small carcasses and the phantom reach only 5 blocks on a timed bleed. Other grate modes (Blood Magic, Hexerei, Vampirism) need mods Winterheart doesn't have.
