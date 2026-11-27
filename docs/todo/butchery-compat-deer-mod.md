# butchery-compat-deer-mod

Add [Deer Mod by ArisuwuMei](https://www.curseforge.com/minecraft/mc-mods/deermod-by-arisuwumei) to the pack, and make its deer butcherable with Butchery through our own GPLv3 compat mod.

- **Feature:** `butchery-compat` (new; create `docs/features/butchery-compat.md` from [../_template.md](../_template.md))
- **Added:** 2026-11-26
- **Progress:** 0/11 (keep in sync with the row in [../TODO.md](../TODO.md))

## Steps

- [ ] Install `deermod-forge-0.6.1+1.20.1.jar` (CurseForge file `7355418`), check its dependencies, run `./fetch.sh --generate`
- [ ] Check the license in the jar (`LICENSE`, `META-INF/mods.toml`) and the source repo: asset exceptions, GPL-3.0 "only" or "or later"; update `THIRD_PARTY_NOTICES.md` if it needs an entry
- [ ] Smoke-test the pack headlessly with the new mod
- [ ] List the deer entity IDs, variants, drops and spawning; decide whether `datapacks/winterheart_spawning` needs rules for them
- [ ] Decide the carcass design: carcasses per variant, cut stages, loot, tools
- [ ] Create the compat mod in `mod-src/` from `mod-src/_template/new-mod.sh`, with its own GPLv3 `LICENSE` and credit to ArisuwuMei
- [ ] Make carcass and head models from the deer models, recording what changed and when; draw flesh, bone and cut textures ourselves
- [ ] Register blocks and items, handle deer death and butchering steps, add loot tables and tags, stop duplicate vanilla-style deer drops
- [ ] Add JEI pages for the deer: build the [planned JEI support](../features/carcass-butchering.md#jei-support-planned), then ship the data file
- [ ] Test headlessly, then butcher each variant in a running game
- [ ] Update feature doc, feature map, docs index and CHANGELOG.md

## Files

Paths this todo is expected to touch (repo-relative, in backticks).

- `mods.json`
- `THIRD_PARTY_NOTICES.md`
- `mod-src/winterheart_deer_butchery/`
- `datapacks/winterheart_spawning/`
- `docs/features/butchery-compat.md`
- `docs/feature-map.md`
- `docs/README.md`
- `CHANGELOG.md`

## Notes

Decisions, open questions, dead ends. Newest at the bottom, dated.

- 2026-11-26: CurseForge lists Deer Mod as GPLv3, with no separate asset terms on its project or license pages. The source repo (`https://dawn.wine/ArisuwuMei/DeerMod`) returned 403, so it is unchecked. The jar has not been inspected.
- 2026-11-26: Deer models may be used, traced and modified if the compat mod that contains them is GPLv3. Distributing it needs the license text, attribution, change notices and source, including editable model files. Keep those assets out of `resourcepacks/winterheart_assets/` and other Winterheart mods.
- 2026-11-26: `butchery-5.2-forge-1.20.1.jar` is All Rights Reserved. Don't copy or trace its models or textures, adapt its decompiled code, import its classes or mixin into them. Use IDs and tags only (e.g. `forge:cleaver`).
- 2026-11-26: Butchery's tag-only route (e.g. `forge:cow` in `entity_types`) reuses a vanilla carcass. A deer look needs new blocks, hence a Java mod. For reference, `CowcarcassdropProcedure` acts on `LivingDeathEvent`: skips babies, checks a config toggle and, with specific tools on, a cleaver or Butcher's Touch (from bytecode, not tested in game).
