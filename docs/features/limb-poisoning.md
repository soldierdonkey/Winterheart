# limb-poisoning

Once a limb takes any damage (Legendary Survival Overhaul's per-limb HP drops below max) it is "poised": every few ticks its health takes a small biased random step up or down. The lower a limb's health, the more the walk is skewed towards decay; above a threshold it is skewed towards healing. The status effect `winterheart:limb_poising` and the HUD's "Limb Status" section show it. A limb at about 0 HP is dead; a dead HEAD or CHEST kills the player.

## Files

- `mod-src/winterheart_survival/src/main/java/io/github/soldierdonkey2/winterheart_survival/limb/LimbPoising.java`: the mechanic.
- `mod-src/winterheart_survival/src/main/java/io/github/soldierdonkey2/winterheart_survival/JsonConfig.java`: reads a datapack JSON on every `/reload`.
- `config/paxi/datapacks/winterheart_survival/data/winterheart/limb_poising/config.json`: all tuning.
- `mod-src/winterheart/.../effect/ModEffects.java` and `.../textures/mob_effect/limb_poising.png`: the effect ([winterheart-core](winterheart-core.md)).
- `mod-src/winterheart_hud/.../HudApi.java`: the "Limb Status" section is added through it ([survival-hud](survival-hud.md)).

## How it works

Reads use the player's `ForgeCaps."legendarysurvivaloverhaul:body_damage"` NBT (`<LIMB>_damage`, `<LIMB>_maxHealth`; a max of 0 counts as 8). Writes use LSO's `/bodydamage <player> set <LIMB> <health>`: writing the capability NBT directly was seen being silently reverted by LSO's routine packet sync.

- **Poise tick** (every `tick_interval` = 5 ticks per player): skipped while LSO's Recovery effect is active (the poising effect is dropped, tracked state kept). For each limb with damage: decay weight = `base_decay_weight + (1 - hp%) * low_health_decay_skew`; heal weight = `base_heal_weight`, plus `high_health_heal_bias` above `high_health_threshold`; a random step of `min_step_percent` to `max_step_percent` of max health in the chosen direction is written back. A limb at 98% damage is marked dead and ignored (in memory, until restart). Tier changes (stable, strained, wounded, critical) and full healing show on the action bar. Limbs below `visible_health_threshold` (80%) count towards the effect's amplifier (up to `max_effect_amplifier`) and the HUD.
- **Body regeneration:** main health regenerates `body_regen_per_tick` while every limb is above `body_regen_threshold`.
- **Vital check** (every `death_check_interval` = 2 ticks): a HEAD or CHEST at or under `death_health_buffer` (2%) of max is marked dead and the player is killed with `/damage <player> 1000 minecraft:generic` (a plain `kill` leaves LSO's capability in a NaN state).

## Config / tunables

All keys in `limb_poising/config.json`, re-read on `/reload`: `enabled`, `cap_id`, `tick_interval`, `min_step_percent`, `max_step_percent`, `base_decay_weight`, `base_heal_weight`, `low_health_decay_skew`, `high_health_threshold`, `high_health_heal_bias`, `max_effect_amplifier`, `effect_refresh_duration`, `recovery_effect_id`, `visible_health_threshold`, `body_regen_threshold`, `body_regen_per_tick`, `notify_actionbar`, `sidebar_enabled`, `vital_limbs`, `death_check_interval`, `death_health_buffer`, `death_damage_type`, `debug` (per-limb log lines).

## Changes

- 2026-11-22: Ported from `kubejs/server_scripts/mechanics/limb_poisoning.js` to Java (step 5 of [general-kubejs-removal](../todo/general-kubejs-removal.md)); `LIMB_POISE_CONFIG` became `config.json`. Same formulas and thresholds. Tested: the config loaded; the poise and vital checks ran on the joined player without errors and without effect (healthy head and chest); `HudApi` section registered. **Not tested:** the poise step itself, the effect and the Limb Status lines. LSO initialises the limbs' max health after login, so `/bodydamage` couldn't be made to stick in the probe, and the hidden headless client barely ticks the server.

## Gotchas

- Per-limb poise and dead-limb state is in memory only: a restart forgets which limbs were poised or dead (poised ones are re-detected from their damage).
- Needs LSO's `/bodydamage` command. A renamed command silently stops the poise from writing.
