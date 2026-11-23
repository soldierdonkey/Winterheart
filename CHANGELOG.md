## Winterheart (unreleased)

### Features:

- winterheart-core: new core mod `winterheart` registers the metal parts, saltpeter and nitrate bottles, guide book, lost page and the limb poising effect (formerly KubeJS). The old `kubejs:` items in existing worlds convert automatically. Brewing is unchanged: only crude nitrate solution + charred planks.

- kubejs-removal: every KubeJS script (time system, spawning, guides, HUD, survival mechanics, structures, recipes) moved into seven Winterheart mods (`winterheart`, `winterheart_survival`, `winterheart_guides`, `winterheart_time`, `winterheart_hud`, `winterheart_spawning`, `winterheart_structures`) and Paxi datapacks. Behaviour is the same, and existing worlds carry over (items, unlocked guides, day counter, placed structures are converted or imported). Tuning that was in scripts is now JSON, re-read on `/reload`. KubeJS, Rhino, MoreJS, ProbeJS and SanityJS are removed from `mods/`.

### Fixes:

- proximity-effects: the foliage rule never fired (tag typo) and set sanity from the temperature; it now gives a small chance of +2 sanity near plants.
- time-system: sleeping with several players online now passes one day, not one per player.
