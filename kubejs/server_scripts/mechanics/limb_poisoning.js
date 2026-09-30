// Priority: 850
//
// Limb Poising System
// --------------------
// Once a limb takes any damage (LSO's per-limb HP drops below max), it becomes
// "poised": every second its health takes a small biased random (Brownian) step
// up or down instead of sitting still or relying only on LSO's own regen.
//   - The lower a limb's health %, the more the walk is skewed towards further decay.
//   - Above HIGH_HEALTH_THRESHOLD, the walk is skewed slightly towards healing.
// A limb stops being tracked once it returns to full health.
// Status is surfaced via a custom status effect (winterheart:limb_poising,
// registered in startup_scripts/effects/limb_poising.js) whose amplifier
// reflects how many limbs are currently poised, plus actionbar notifications
// and lines appended to the custom sidebar (see custom_sidebars.js).
//
// IMPORTANT: writes go through the mod's own `/bodydamage <player> set <LIMB> <health>`
// command, not raw NBT (player.mergeNbt via ForgeCaps). Direct NBT writes to
// legendarysurvivaloverhaul:body_damage were observed getting silently reverted
// within ~20-40 ticks regardless of what was written (even LSO's own command had
// the same symptom when tested in isolation) -- LSO's config has a
// "Routine Packet Sync" setting (30 ticks by default) that re-syncs body_damage
// from its real authoritative state, which apparently isn't the same thing
// player.nbt.ForgeCaps lets us mutate. The command is the one interface confirmed
// to actually stick. Reads still go through ForgeCaps (getForgeCap), which appears
// accurate for reading, just not reliable for writing.

const LIMB_POISE_CONFIG = {
    ENABLED: true,

    // ForgeCap that stores per-limb damage/maxHealth (from Legendary Survival Overhaul)
    CAP_ID: 'legendarysurvivaloverhaul:body_damage',

    // 20 ticks = 1 second
    TICK_INTERVAL: 5,

    // Brownian step size, as a percentage of a limb's max health, rolled fresh each tick
    MIN_STEP_PERCENT: 0.0005,
    MAX_STEP_PERCENT: 0.001,

    // Baseline random-walk weights (decay vs heal) before any bias is applied
    BASE_DECAY_WEIGHT: 1.0,
    BASE_HEAL_WEIGHT: 0.1,

    // As health % drops, extra decay weight is added, scaled by (1 - healthPercent).
    // e.g. at 0% health this adds the full LOW_HEALTH_DECAY_SKEW to the decay weight.
    LOW_HEALTH_DECAY_SKEW: 2.5,

    // Above this health percent, the walk gets a mild upward (healing) bias
    HIGH_HEALTH_THRESHOLD: 0.8,
    HIGH_HEALTH_HEAL_BIAS: 5.5,

    // Status effect applied while at least one limb is poised
    EFFECT_ID: 'winterheart:limb_poising',
    EFFECT_REFRESH_DURATION: 60, // ticks; effect is re-applied every second while active
    MAX_EFFECT_AMPLIFIER: 3,

    // While a player has this effect, limb poising is paused (no decay/heal steps)
    RECOVERY_EFFECT_ID: 'legendarysurvivaloverhaul:recovery',

    // Limbs only surface in the sidebar / effect count / notifications once their
    // health % drops below this. Minor scratches above this threshold still decay
    // and heal in the background, they just aren't shown until they get serious.
    VISIBLE_HEALTH_THRESHOLD: 0.8,

    // Main health regenerates while every limb (dead ones count as 0%) is above this percent
    BODY_REGEN_THRESHOLD: 0.8,
    // Main health restored per poise tick (TICK_INTERVAL ticks). 0.025 per 5 ticks = 0.1 HP/s
    BODY_REGEN_PER_TICK: 0.025,

    NOTIFY_ACTIONBAR: true,
    SIDEBAR_ENABLED: true,

    // Limbs whose HP hits 0 stop being poised/processed entirely. If one of these
    // vital limbs hits 0, the player dies. Checked every DEATH_CHECK_INTERVAL ticks
    // (faster than the normal once-a-second poise tick) so death is responsive.
    VITAL_LIMBS: ['HEAD', 'CHEST'],
    DEATH_CHECK_INTERVAL: 2,
    // A limb counts as "dead" once its remaining HP drops to this percent of max
    // (or below), instead of requiring it hit exactly 0.
    DEATH_HEALTH_BUFFER: 0.02,
    // Vanilla damage type used to actually kill the player when a vital limb hits 0.
    // "generic" bypasses armor (fitting for an internal organ failing) and produces
    // a normal death event/message, unlike player.kill() which was observed to leave
    // LSO's body_damage cap in a broken (NaN) state.
    DEATH_DAMAGE_TYPE: 'minecraft:generic',

    // Set true (or run `/kjs run LIMB_POISE_CONFIG.DEBUG = true`) to print per-limb,
    // per-tick decay/heal math to console/latest.log.
    DEBUG: false
}

const LIMB_POISE_LIMBS = [
    { key: 'HEAD', label: 'Head' },
    { key: 'CHEST', label: 'Chest' },
    { key: 'LEFT_ARM', label: 'Left Arm' },
    { key: 'RIGHT_ARM', label: 'Right Arm' },
    { key: 'LEFT_LEG', label: 'Left Leg' },
    { key: 'RIGHT_LEG', label: 'Right Leg' },
    { key: 'LEFT_FOOT', label: 'Left Foot' },
    { key: 'RIGHT_FOOT', label: 'Right Foot' }
]

const LIMB_POISE_TIER_STYLES = {
    stable: 'accent',
    strained: 'normal',
    wounded: 'warning',
    critical: 'critical'
}

// In-memory per-player, per-limb tracking state. Resets on /kjs reload server.
global.limbPoiseState = global.limbPoiseState || {}

function getPlayerLimbState(player) {
    let uuid = player.uuid.toString()
    if (!global.limbPoiseState[uuid]) global.limbPoiseState[uuid] = {}
    return global.limbPoiseState[uuid]
}

// Limbs that have hit 0 HP and are permanently excluded from further processing.
global.limbDeadState = global.limbDeadState || {}

function getPlayerDeadLimbs(player) {
    let uuid = player.uuid.toString()
    if (!global.limbDeadState[uuid]) global.limbDeadState[uuid] = {}
    return global.limbDeadState[uuid]
}

function isLimbDead(player, limbKey) {
    return !!getPlayerDeadLimbs(player)[limbKey]
}

/**
 * Marks a limb as dead (0 HP): stops it from being poised/processed any further.
 * Killing the player if the limb is vital (HEAD/CHEST) is the caller's responsibility.
 */
function markLimbDead(player, limb) {
    let dead = getPlayerDeadLimbs(player)
    if (dead[limb.key]) return
    dead[limb.key] = true

    delete getPlayerLimbState(player)[limb.key]

    if (LIMB_POISE_CONFIG.DEBUG) {
        console.log(`[LimbPoise] ${player.username} ${limb.key}: reached 0 HP -- marked dead, no longer processed`)
    }

    if (LIMB_POISE_CONFIG.NOTIFY_ACTIONBAR) {
        let json = JSON.stringify({ text: `Your ${limb.label} has bled dry!`, color: 'dark_red', bold: true })
        player.server.runCommandSilent(`title ${player.username} actionbar ${json}`)
    }
}

function getLimbPoiseTier(healthPercent) {
    if (healthPercent >= 0.8) return 'stable'
    if (healthPercent >= 0.5) return 'strained'
    if (healthPercent >= 0.25) return 'wounded'
    return 'critical'
}

function notifyLimbPoise(player, limb, tier, healed) {
    if (!LIMB_POISE_CONFIG.NOTIFY_ACTIONBAR) return

    let msg, color
    if (healed) {
        msg = `${limb.label} has fully healed.`
        color = 'green'
    } else {
        switch (tier) {
            case 'critical':
                msg = `${limb.label} is critically wounded!`
                color = 'dark_red'
                break
            case 'wounded':
                msg = `${limb.label} is bleeding badly.`
                color = 'red'
                break
            case 'strained':
                msg = `${limb.label} is aching.`
                color = 'gold'
                break
            default:
                msg = `${limb.label} is recovering.`
                color = 'yellow'
                break
        }
    }

    let json = JSON.stringify({ text: msg, color: color, italic: true })
    player.server.runCommandSilent(`title ${player.username} actionbar ${json}`)
}

/**
 * Writes a limb's current health (not damage) via LSO's own `/bodydamage` command,
 * which is the one interface observed to actually stick (see file header comment).
 */
function setLimbHealthViaCommand(player, limbKey, newHealth) {
    player.server.runCommandSilent(`bodydamage ${player.username} set ${limbKey} ${newHealth.toFixed(4)}`)
}

/**
 * Advances one limb's poise state by a single tick, writing the result via the
 * /bodydamage command. Returns the limb's current health percent if it is (still)
 * poised, or null if it is untracked/healthy.
 */
function tickLimbPoise(player, capData, limb) {
    if (isLimbDead(player, limb.key)) return null

    let dmgKey = `${limb.key}_damage`
    let maxKey = `${limb.key}_maxHealth`
    let damage = capData[dmgKey] || 0
    let maxHealth = capData[maxKey] || 8.0
    let state = getPlayerLimbState(player)

    if (LIMB_POISE_CONFIG.DEBUG && (damage > 0 || (state[limb.key] && state[limb.key].active))) {
        let lastSet = state[limb.key] ? state[limb.key].lastSetDamage : undefined
        let healingPerTick = capData[`${limb.key}_healingPerTicks`]
        let remainingHealTicks = capData[`${limb.key}_remainingHealingTicks`]
        console.log(
            `[LimbPoise] ${player.username} ${limb.key}: raw read damage=${damage.toFixed(4)}` +
            (lastSet !== undefined ? ` (we last set it to ${lastSet.toFixed(4)} last tick${Math.abs(lastSet - damage) > 0.0001 ? ' -- MISMATCH, something else is changing this!' : ''})` : '') +
            ` | LSO regen state: healingPerTick=${healingPerTick} remainingHealTicks=${remainingHealTicks} ` +
            `healingTickTimer=${capData.healingTickTimer} proportionalRegenTimer=${capData.proportionalRegenTimer} customHealthRegenTimer=${capData.customHealthRegenTimer}`
        )
    }

    if (maxHealth <= 0) return null

    if (damage <= 0) {
        if (state[limb.key] && state[limb.key].active) {
            if (LIMB_POISE_CONFIG.DEBUG) {
                console.log(`[LimbPoise] ${player.username} ${limb.key}: damage=${damage.toFixed(4)} (<=0) -> untracking, was active`)
            }
            notifyLimbPoise(player, limb, 'stable', true)
        }
        delete state[limb.key]
        return null
    }

    let healthPercent = Math.max(0, Math.min(1, 1 - (damage / maxHealth)))

    // --- Weighted Brownian motion ---
    let decayWeight = LIMB_POISE_CONFIG.BASE_DECAY_WEIGHT + (1 - healthPercent) * LIMB_POISE_CONFIG.LOW_HEALTH_DECAY_SKEW
    let healWeight = LIMB_POISE_CONFIG.BASE_HEAL_WEIGHT
    if (healthPercent >= LIMB_POISE_CONFIG.HIGH_HEALTH_THRESHOLD) {
        healWeight += LIMB_POISE_CONFIG.HIGH_HEALTH_HEAL_BIAS
    }

    let decaying = Math.random() * (decayWeight + healWeight) < decayWeight

    let stepPercent = LIMB_POISE_CONFIG.MIN_STEP_PERCENT +
        Math.random() * (LIMB_POISE_CONFIG.MAX_STEP_PERCENT - LIMB_POISE_CONFIG.MIN_STEP_PERCENT)
    let stepAmount = maxHealth * stepPercent

    let newDamage = Math.max(0, Math.min(maxHealth, damage + (decaying ? stepAmount : -stepAmount)))
    setLimbHealthViaCommand(player, limb.key, maxHealth - newDamage)

    if (newDamage >= maxHealth * (1 - LIMB_POISE_CONFIG.DEATH_HEALTH_BUFFER)) {
        markLimbDead(player, limb)
        return null
    }

    let newHealthPercent = Math.max(0, Math.min(1, 1 - (newDamage / maxHealth)))
    let newTier = getLimbPoiseTier(newHealthPercent)

    if (LIMB_POISE_CONFIG.DEBUG) {
        console.log(
            `[LimbPoise] ${player.username} ${limb.key}: ` +
            `hp%=${(healthPercent * 100).toFixed(1)} dmg=${damage.toFixed(4)}/${maxHealth} | ` +
            `decayW=${decayWeight.toFixed(2)} healW=${healWeight.toFixed(2)} -> ${decaying ? 'DECAY' : 'HEAL'} ` +
            `step=${stepAmount.toFixed(4)} | newDmg=${newDamage.toFixed(4)} newHp%=${(newHealthPercent * 100).toFixed(1)} tier=${newTier} ` +
            `| cmd: bodydamage ${player.username} set ${limb.key} ${(maxHealth - newDamage).toFixed(4)}`
        )
    }

    let isVisible = newHealthPercent < LIMB_POISE_CONFIG.VISIBLE_HEALTH_THRESHOLD

    if (!state[limb.key]) {
        state[limb.key] = { active: true, tier: newTier }
        if (isVisible) notifyLimbPoise(player, limb, newTier, false)
    } else if (state[limb.key].tier !== newTier) {
        state[limb.key].tier = newTier
        if (isVisible) notifyLimbPoise(player, limb, newTier, false)
    }
    state[limb.key].lastSetDamage = newDamage

    if (newDamage <= 0) {
        notifyLimbPoise(player, limb, 'stable', true)
        delete state[limb.key]
        return null
    }

    return newHealthPercent
}

function applyLimbPoiseEffect(player, activeCount) {
    if (activeCount <= 0) {
        if (player.hasEffect(LIMB_POISE_CONFIG.EFFECT_ID)) {
            if (LIMB_POISE_CONFIG.DEBUG) console.log(`[LimbPoise] ${player.username}: removing effect (0 visible limbs)`)
            player.removeEffect(LIMB_POISE_CONFIG.EFFECT_ID)
        }
        return
    }

    let amplifier = Math.min(activeCount - 1, LIMB_POISE_CONFIG.MAX_EFFECT_AMPLIFIER)
    if (LIMB_POISE_CONFIG.DEBUG) {
        console.log(`[LimbPoise] ${player.username}: applying effect, visibleCount=${activeCount} amplifier=${amplifier}`)
    }
    player.potionEffects.add(LIMB_POISE_CONFIG.EFFECT_ID, LIMB_POISE_CONFIG.EFFECT_REFRESH_DURATION, amplifier, true, false)
}

/**
 * Builds the sidebar lines for any limbs currently poised on this player.
 * Consumed by custom_sidebars.js. Returns [] when nothing is poised.
 */
function getLimbPoiseSidebarLines(player) {
    if (!LIMB_POISE_CONFIG.SIDEBAR_ENABLED) return []

    let state = getPlayerLimbState(player)
    let capData = getForgeCap(player, LIMB_POISE_CONFIG.CAP_ID)
    if (!capData) return []

    let lines = []
    LIMB_POISE_LIMBS.forEach(limb => {
        let limbState = state[limb.key]
        if (!limbState || !limbState.active) return

        let damage = capData[`${limb.key}_damage`] || 0
        let maxHealth = capData[`${limb.key}_maxHealth`] || 8.0
        let healthPercent = Math.max(0, Math.min(1, 1 - (damage / maxHealth)))
        if (healthPercent >= LIMB_POISE_CONFIG.VISIBLE_HEALTH_THRESHOLD) return

        let style = LIMB_POISE_TIER_STYLES[limbState.tier] || 'normal'

        lines.push({ style: style, text: `${limb.label}: ${Math.round(healthPercent * 100)}%` })
    })

    return lines
}

/**
 * Slowly restores the player's main health while every limb is above
 * BODY_REGEN_THRESHOLD. Re-reads the cap so it sees this tick's writes.
 */
function tickBodyRegen(player) {
    if (!player.isAlive() || player.health >= player.maxHealth) return

    let capData = getForgeCap(player, LIMB_POISE_CONFIG.CAP_ID)
    if (!capData) return

    for (let limb of LIMB_POISE_LIMBS) {
        let maxHealth = capData[`${limb.key}_maxHealth`] || 0
        if (maxHealth <= 0) continue
        let damage = capData[`${limb.key}_damage`] || 0
        let healthPercent = 1 - (damage / maxHealth)
        if (isLimbDead(player, limb.key) || !(healthPercent > LIMB_POISE_CONFIG.BODY_REGEN_THRESHOLD)) return
    }

    player.heal(LIMB_POISE_CONFIG.BODY_REGEN_PER_TICK)
}

// ==========================================
// TICK HOOK
// ==========================================
PlayerEvents.tick(event => {
    if (!LIMB_POISE_CONFIG.ENABLED) return

    let player = event.player
    if (player.level.isClientSide()) return

    // Once every second (20 ticks) per player
    if (player.age % LIMB_POISE_CONFIG.TICK_INTERVAL !== 0) return

    let capData = getForgeCap(player, LIMB_POISE_CONFIG.CAP_ID)
    if (!capData) return

    // While LSO's Recovery effect is active, poising is suspended: no decay/heal steps
    // and the poising effect is dropped. Tracked limb state is kept, so it resumes after.
    if (player.hasEffect(LIMB_POISE_CONFIG.RECOVERY_EFFECT_ID)) {
        applyLimbPoiseEffect(player, 0)
        tickBodyRegen(player)
        return
    }

    let visibleCount = 0
    LIMB_POISE_LIMBS.forEach(limb => {
        let healthPercent = tickLimbPoise(player, capData, limb)
        if (healthPercent !== null && healthPercent < LIMB_POISE_CONFIG.VISIBLE_HEALTH_THRESHOLD) {
            visibleCount++
        }
    })
    applyLimbPoiseEffect(player, visibleCount)
    tickBodyRegen(player)
})

/**
 * Checks a single vital limb for death. Returns true if it killed the player.
 * Uses a small float tolerance on the "reached max health" check since the
 * /bodydamage command rounds to 4 decimal places, so damage may never land on
 * an exact bit-for-bit equal to maxHealth.
 */
function checkVitalLimbDeath(player, capData, limbKey) {
    let limb = LIMB_POISE_LIMBS.find(l => l.key === limbKey)
    let maxHealth = capData[`${limbKey}_maxHealth`] || 0
    let damage = capData[`${limbKey}_damage`] || 0
    let dead = isLimbDead(player, limbKey)
    let remaining = maxHealth - damage

    // Only log once a vital limb actually has damage (or is dead) -- otherwise this
    // fires every 2 ticks for two limbs forever and floods the log.
    if (LIMB_POISE_CONFIG.DEBUG && (dead || damage > 0)) {
        console.log(
            `[LimbPoise] ${player.username} ${limbKey} (vital check): dead=${dead} damage=${damage.toFixed(4)}/${maxHealth} remaining=${remaining.toFixed(4)}`
        )
    }

    if (dead) return false
    if (maxHealth <= 0) return false
    if (remaining > maxHealth * LIMB_POISE_CONFIG.DEATH_HEALTH_BUFFER) return false

    markLimbDead(player, limb)
    console.log(`[LimbPoise] ${player.username}: vital limb ${limbKey} destroyed -- killing player`)

    // player.kill() bypasses the normal death event and leaves LSO's body_damage
    // cap in a broken (NaN) state. Dealing real lethal damage through /damage
    // goes through the proper death event instead.
    let result = player.server.runCommandSilent(`damage ${player.username} 1000 ${LIMB_POISE_CONFIG.DEATH_DAMAGE_TYPE}`)
    if (LIMB_POISE_CONFIG.DEBUG) {
        console.log(`[LimbPoise] ${player.username}: damage command result=${result} isAlive=${player.isAlive()}`)
    }
    return true
}

// Vital limbs (HEAD/CHEST) are checked much more often than the once-a-second poise
// tick above, so death is detected quickly regardless of what damaged them (our own
// decay, combat, etc.) rather than waiting up to a second for the next poise cycle.
PlayerEvents.tick(event => {
    if (!LIMB_POISE_CONFIG.ENABLED) return

    let player = event.player
    if (player.level.isClientSide()) return
    if (player.age % LIMB_POISE_CONFIG.DEATH_CHECK_INTERVAL !== 0) return

    let capData = getForgeCap(player, LIMB_POISE_CONFIG.CAP_ID)
    if (!capData) return

    LIMB_POISE_CONFIG.VITAL_LIMBS.forEach(limbKey => {
        checkVitalLimbDeath(player, capData, limbKey)
    })
})
