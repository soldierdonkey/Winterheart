// Priority: 900

const TIME_CONFIG = {
    // Debug Settings
    DEBUG_MODE: false,
    DEBUG_INTERVAL: 200, // Ticks between debug messages (200 = 10s)
    
    // Core Settings
    ETERNAL_NIGHT_DAY: 50,
    SLEEP_COOLDOWN: 200, // Ticks before sleep can trigger a day pass again (200 = 10s)
    
    // [Start_Speed (Day 1), End_Speed (Day 50)]
    // Higher speed = phase passes faster. Lower speed = phase lasts longer.
    SPEED_INTERVALS: {
        DAY: [0.8, 1.8],   // Day speed increases -> Days get shorter
        NIGHT: [1.2, 0.4]  // Night speed decreases -> Nights get longer
    },

    // Vanilla time mappings for phases (0 to 24000)
    PHASES: {
        DAWN: 0,
        NOON: 6000,
        AFTERNOON: 9000,
        DUSK: 12542,
        NIGHT: 14000,
        MIDNIGHT: 18000
    }
}

// ==========================================
// TICK & TIMING HELPERS
// ==========================================
global.currentTick = 0

// Zero-argument timing helpers (can optionally accept an event)
function everySecond(event) {
    return global.currentTick % 20 === 0
}

function everyTenSeconds(event) {
    return global.currentTick % 200 === 0
}

// Helper: Linearly interpolates speed based on day progression (1 to ETERNAL_NIGHT_DAY)
function getDayScaledSpeed(day, interval) {
    let factor = Math.min(1.0, Math.max(0.0, (day - 1) / (TIME_CONFIG.ETERNAL_NIGHT_DAY - 1)))
    return interval[0] + factor * (interval[1] - interval[0])
}

// ==========================================
// 1. DISABLE VANILLA DAY/NIGHT CYCLE
// ==========================================
ServerEvents.loaded(event => {
    event.server.runCommandSilent('gamerule doDaylightCycle false')
})

// ==========================================
// 2. TIME ENGINE & PHASE LOGIC
// ==========================================
LevelEvents.tick(event => {
    const level = event.level
    
    // Only run time logic in the Overworld to prevent multi-dimension ticking
    if (level.dimension.toString() !== 'minecraft:overworld' || level.isClientSide()) return

    global.currentTick++

    let data = level.persistentData
    
    // Initialize custom timeline
    if (!data.contains('custom_day')) data.putInt('custom_day', 1)
    if (!data.contains('death_count')) data.putInt('death_count', 0)
    if (!data.contains('exact_time')) data.putDouble('exact_time', 0.0)
    if (!data.contains('current_phase')) data.putString('current_phase', 'DAWN')
    if (!data.contains('debug_timer')) data.putInt('debug_timer', 0)
    if (!data.contains('sleep_cooldown')) data.putInt('sleep_cooldown', 0)
    if (!data.contains('death_cooldown')) data.putInt('death_cooldown', 0)

    level.players.forEach(p => {
        if (!data.contains('previous_sanity_' + p.uuid)) data.putDouble('previous_sanity_' + p.uuid, 100.0)
    })

    let currentDay = data.getInt('custom_day')
    let exactTime = data.getDouble('exact_time')
    let currentPhase = data.getString('current_phase')
    let sleepCooldown = data.getInt('sleep_cooldown')
    let deathCooldown = data.getInt('death_cooldown')

    if (sleepCooldown > 0) data.putInt('sleep_cooldown', sleepCooldown - 1)
    if (deathCooldown > 0) data.putInt('death_cooldown', deathCooldown - 1)

    // ----------------------------------------------------
    // PHASE: ETERNAL NIGHT (DAY 50+)
    // ----------------------------------------------------
    if (currentDay >= TIME_CONFIG.ETERNAL_NIGHT_DAY) {
        if (global.currentTick % 10 === 0) {
            level.server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
        }
        let isPhaseChange = currentPhase !== 'ETERNAL_NIGHT'
        if (isPhaseChange) {
            data.putString('current_phase', 'ETERNAL_NIGHT')
            currentPhase = 'ETERNAL_NIGHT'
        }
        triggerTimeHook(level, currentDay, 'ETERNAL_NIGHT', isPhaseChange)
        return
    }

    // ----------------------------------------------------
    // CHECK SLEEP STATE (Pass Day Logic)
    // ----------------------------------------------------
    let players = level.players
    let allSleeping = players.length > 0 && players.every(p => p.isSleeping())

    if (allSleeping && sleepCooldown <= 0) {
        data.putInt('sleep_cooldown', TIME_CONFIG.SLEEP_COOLDOWN)
        data.putBoolean('registering_checkpoint', true)
        level.server.scheduleInTicks(95, () => {
            // Abort if any player wakes up during the delay
            allSleeping = players.length > 0 && players.every(p => p.isSleeping())
            if (allSleeping) {
                players.forEach(p => {
                    let name = p.username
                    currentDay += 1
                    data.putInt('custom_day', currentDay)
                    data.putDouble('exact_time', 0.0)
                    data.putString('current_phase', 'DAWN')
                    level.server.runCommandSilent('time set 0')
                    
                    triggerTimeHook(level, currentDay, 'DAWN', true)

                    p.potionEffects.add('alexsmobs:earthquake', 100, 60, false, false)
                    p.potionEffects.add('minecraft:blindness', 60, 0, false, false)

                    players.forEach(p => {
                        data.putDouble('previous_sanity_' + p.uuid, get_sanity(p))
                        console.log(`[TimeSys] Player ${p.username} slept through the night. Day ${currentDay} begins.`)
                    })
                    p.stopSleeping()
                    
                    p.server.runCommandSilent(`execute as ${name} run setcheckpoint @s`)
                    p.server.runCommandSilent(`execute as ${name} run setSubaruPlayer @s`)
                    p.tell(Text.gold(`You wake up. Day ${currentDay} begins...`))
                })
            }
            level.server.scheduleInTicks(5, () => {
                players.forEach(p => {
                    p.removeEffect('alexsmobs:earthquake')
                })
                data.putBoolean('registering_checkpoint', false)
            })
        })
        return
    }

    // ----------------------------------------------------
    // DYNAMIC SPEED CALCULATION & SMOOTH TRANSITION
    // ----------------------------------------------------
    let baseDaySpeed = getDayScaledSpeed(currentDay, TIME_CONFIG.SPEED_INTERVALS.DAY)
    let baseNightSpeed = getDayScaledSpeed(currentDay, TIME_CONFIG.SPEED_INTERVALS.NIGHT)
    let currentSpeed = baseDaySpeed

    // Smooth blend between Dusk and Night
    if (exactTime >= TIME_CONFIG.PHASES.DUSK && exactTime < TIME_CONFIG.PHASES.NIGHT) {
        let blendFactor = (exactTime - TIME_CONFIG.PHASES.DUSK) / (TIME_CONFIG.PHASES.NIGHT - TIME_CONFIG.PHASES.DUSK)
        currentSpeed = baseDaySpeed + blendFactor * (baseNightSpeed - baseDaySpeed)
    } else if (exactTime >= TIME_CONFIG.PHASES.NIGHT) {
        currentSpeed = baseNightSpeed
    }

    // ----------------------------------------------------
    // NORMAL TIME PROGRESSION
    // ----------------------------------------------------
    if (exactTime < TIME_CONFIG.PHASES.MIDNIGHT) {
        exactTime += currentSpeed
        data.putDouble('exact_time', exactTime)
        
        // Batch time synchronization to every 10 ticks
        if (global.currentTick % 10 === 0) {
            level.server.runCommandSilent(`time set ${Math.floor(exactTime)}`)
        }

        // Phase Transition Detection
        let newPhase = currentPhase
        if (exactTime >= TIME_CONFIG.PHASES.MIDNIGHT) newPhase = 'MIDNIGHT'
        else if (exactTime >= TIME_CONFIG.PHASES.NIGHT) newPhase = 'NIGHT'
        else if (exactTime >= TIME_CONFIG.PHASES.DUSK) newPhase = 'DUSK'
        else if (exactTime >= TIME_CONFIG.PHASES.AFTERNOON) newPhase = 'AFTERNOON'
        else if (exactTime >= TIME_CONFIG.PHASES.NOON) newPhase = 'NOON'

        let isPhaseChange = newPhase !== currentPhase
        if (isPhaseChange) {
            data.putString('current_phase', newPhase)
            currentPhase = newPhase
        }

        // Runs continuously every tick
        triggerTimeHook(level, currentDay, currentPhase, isPhaseChange)
    } else {
        if (global.currentTick % 10 === 0) {
            level.server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
        }
        triggerTimeHook(level, currentDay, 'MIDNIGHT', false)
    }

    // ----------------------------------------------------
    // DEBUG DIAGNOSTICS
    // ----------------------------------------------------
    if (TIME_CONFIG.DEBUG_MODE) {
        let timer = data.getInt('debug_timer') + 1
        if (timer >= TIME_CONFIG.DEBUG_INTERVAL) {
            timer = 0
            let timeColor = exactTime >= TIME_CONFIG.PHASES.MIDNIGHT ? Text.red : Text.green
            
            level.players.forEach(p => {
                p.tell(Text.gray('[TimeSys] ')
                    .append(Text.aqua(`Day: ${currentDay} | `))
                    .append(Text.yellow(`Phase: ${currentPhase} | `))
                    .append(timeColor(`Time: ${Math.floor(exactTime)} | `))
                    .append(Text.gold(`Speed: ${currentSpeed.toFixed(2)}`)))
            })
        }
        data.putInt('debug_timer', timer)
    }
})

// ==========================================
// 3. ETERNAL NIGHT BED BLOCKER
// ==========================================
BlockEvents.rightClicked(event => {
    if (event.block.hasTag('minecraft:beds')) {
        let day = event.level.persistentData.getInt('custom_day') || 1
        if (day >= TIME_CONFIG.ETERNAL_NIGHT_DAY) {
            event.player.tell(Text.darkRed("You close your eyes, but sleep won't come. The night is eternal."))
            event.cancel()
        }
    }
})

// ==========================================
// DEATH REWIND & OMINOUS BROADCAST HANDLER
// ==========================================
const OMINOUS_DEATH_SPLASH = [
    "FOCUS",
    "SURVIVE",
    "OUTLAST",
    "PERSIST",
    "PROLIFERATE"
]

function handleDeathLoopReset(server, player) {
    let overworld = server.getLevel('minecraft:overworld')
    let data = overworld.persistentData
    let day = data.getInt('custom_day') || 1

    let deathCooldown = data.getInt('death_cooldown') || 0
    if (deathCooldown > 0) return

    let death_count = data.getInt('death_count') || 0
    death_count += 1
    data.putInt('death_count', death_count)

    data.putInt('death_cooldown', 40)

    console.log(`[TimeSys] Player ${player.username} died on day ${day}. Resetting timeline to morning...`)

    if (day < TIME_CONFIG.ETERNAL_NIGHT_DAY) {
        data.putDouble('exact_time', 0.0)
        data.putString('current_phase', 'DAWN')
        data.putInt('sleep_cooldown', TIME_CONFIG.SLEEP_COOLDOWN)
        server.runCommandSilent('time set 0')
        triggerTimeHook(overworld, day, 'DAWN', true)
    } else {
        server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
    }

    let chosenSplash = OMINOUS_DEATH_SPLASH[Math.floor(Math.random() * OMINOUS_DEATH_SPLASH.length)]
    let chosenMsg = GENERATE_OMINOUS_MESSAGE(death_count)

    server.players.forEach(p => {
        p.tell(Text.darkRed('☠ ').append(Text.of(chosenMsg).red().italic()))
        server.runCommandSilent(`execute as ${p.username} run playSound minecraft:entity.warden.heartbeat master 1.0 0.6`)

        let new_sanity = Math.max(data.getDouble("previous_sanity_" + p.uuid) - 10, 25)
        set_sanity(p, server, new_sanity)
        data.putDouble("previous_sanity_" + p.uuid, new_sanity)
    })

    server.runCommandSilent('title @a times 10 60 20')
    server.runCommandSilent(`title @a title {"text":"${chosenSplash}","color":"dark_red","bold":true}`)
    server.runCommandSilent(`title @a subtitle {"text":"${chosenMsg}","color":"gray","italic":true}`)
}

// ==========================================
// RESPAWN STATUS EFFECT DETECTOR
// ==========================================
PlayerEvents.tick(event => {
    let player = event.player
    if (player.level.isClientSide()) return

    let overworld = player.server.getLevel('minecraft:overworld')
    let data = overworld.persistentData

    if (data.getBoolean('registering_checkpoint')) return

    let effect = player.hasEffect('alexsmobs:earthquake')
    if (effect) {
        player.removeEffect('alexsmobs:earthquake')
        handleDeathLoopReset(player.server, player)
    }
})

// ==========================================
// 4. CUSTOM PHASE HOOKS
// ==========================================
// Called continuously every tick with phase change awareness
function triggerTimeHook(level, day, phase, isPhaseChange) {
    switch(phase) {
        case 'DAWN':
            if (everyTenSeconds()) {
                level.server.runCommandSilent('weather clear')
            }
            break
            
        case 'NOON':
            break
            
        case 'AFTERNOON':
            if (day > 20 && everyTenSeconds()) {
                level.server.runCommandSilent('weather rain')
            }
            break
            
        case 'DUSK':
            break
            
        case 'NIGHT':
            break
            
        case 'MIDNIGHT':
            break
            
        case 'ETERNAL_NIGHT':
            if (everyTenSeconds()) {
                level.server.runCommandSilent('weather thunder')
            }
            break
    }
}

// ==========================================
// 5. DEBUG COMMAND REGISTRY
// ==========================================
ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    function sendFeedback(source, message) {
        if (source.player) source.player.tell(message)
        else source.server.tell(message)
    }

    function setPhaseManually(source, phaseName, ticks) {
        let level = source.server.getLevel('minecraft:overworld')
        let data = level.persistentData
        let day = data.getInt('custom_day') || 1

        data.putDouble('exact_time', Number(ticks))
        data.putString('current_phase', phaseName)
        level.server.runCommandSilent(`time set ${ticks}`)

        triggerTimeHook(level, day, phaseName, true)
        sendFeedback(source, Text.green(`[TimeSys] Set phase to `).append(Text.yellow(phaseName)).append(Text.green(` (Tick: ${ticks})`)))
    }

    event.register(
        Commands.literal('customtime')
            .requires(source => source.hasPermission(2))

            .then(Commands.literal('query')
                .executes(ctx => {
                    let level = ctx.source.server.getLevel('minecraft:overworld')
                    let data = level.persistentData
                    let day = data.getInt('custom_day') || 1
                    let exactTime = Math.floor(data.getDouble('exact_time') || 0)
                    let phase = data.getString('current_phase') || 'UNKNOWN'
                    let debugState = TIME_CONFIG.DEBUG_MODE ? Text.green('ON') : Text.red('OFF')

                    sendFeedback(ctx.source, Text.gray('--- [ Custom Time Status ] ---'))
                    sendFeedback(ctx.source, Text.aqua(`Day: ${day}`))
                    sendFeedback(ctx.source, Text.yellow(`Phase: ${phase}`))
                    sendFeedback(ctx.source, Text.white(`Exact Tick: ${exactTime}`))
                    sendFeedback(ctx.source, Text.gray('Debug Mode: ').append(debugState))
                    return 1
                })
            )

            .then(Commands.literal('debug')
                .executes(ctx => {
                    TIME_CONFIG.DEBUG_MODE = !TIME_CONFIG.DEBUG_MODE
                    let state = TIME_CONFIG.DEBUG_MODE ? Text.green('ENABLED') : Text.red('DISABLED')
                    sendFeedback(ctx.source, Text.gray('[TimeSys] Debug mode ').append(state))
                    return 1
                })
            )

            .then(Commands.literal('day')
                .then(Commands.argument('day', Arguments.INTEGER.create(event))
                    .executes(ctx => {
                        let level = ctx.source.server.getLevel('minecraft:overworld')
                        let data = level.persistentData
                        let newDay = Arguments.INTEGER.getResult(ctx, 'day')

                        data.putInt('custom_day', newDay)

                        if (newDay >= TIME_CONFIG.ETERNAL_NIGHT_DAY) {
                            data.putString('current_phase', 'ETERNAL_NIGHT')
                            data.putDouble('exact_time', TIME_CONFIG.PHASES.MIDNIGHT)
                            level.server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
                            triggerTimeHook(level, newDay, 'ETERNAL_NIGHT', true)
                        }

                        sendFeedback(ctx.source, Text.green(`[TimeSys] Set current day to: ${newDay}`))
                        return 1
                    })
                )
            )

            .then(Commands.literal('time')
                .then(Commands.argument('ticks', Arguments.INTEGER.create(event))
                    .executes(ctx => {
                        let level = ctx.source.server.getLevel('minecraft:overworld')
                        let data = level.persistentData
                        let ticks = Math.max(0, Math.min(24000, Arguments.INTEGER.getResult(ctx, 'ticks')))
                        
                        data.putDouble('exact_time', Number(ticks))
                        level.server.runCommandSilent(`time set ${ticks}`)

                        sendFeedback(ctx.source, Text.green(`[TimeSys] World time set to ${ticks} ticks.`))
                        return 1
                    })
                )
            )

            .then(Commands.literal('skipday')
                .executes(ctx => {
                    let level = ctx.source.server.getLevel('minecraft:overworld')
                    let data = level.persistentData
                    let nextDay = (data.getInt('custom_day') || 1) + 1

                    data.putInt('sleep_cooldown', TIME_CONFIG.SLEEP_COOLDOWN)
                    data.putInt('custom_day', nextDay)
                    data.putDouble('exact_time', 0.0)
                    data.putString('current_phase', 'DAWN')
                    level.server.runCommandSilent('time set 0')

                    triggerTimeHook(level, nextDay, 'DAWN', true)

                    level.players.forEach(p => {
                        let name = p.username
                        p.server.runCommandSilent(`execute as ${name} run setcheckpoint @s`)
                        p.server.runCommandSilent(`execute as ${name} run setSubaruPlayer @s`)
                        p.tell(Text.gold(`[Admin Skip] Day ${nextDay} begins... Checkpoint updated.`))
                    })
                    return 1
                })
            )

            .then(Commands.literal('phase')
                .then(Commands.literal('dawn').executes(ctx => { setPhaseManually(ctx.source, 'DAWN', TIME_CONFIG.PHASES.DAWN); return 1 }))
                .then(Commands.literal('noon').executes(ctx => { setPhaseManually(ctx.source, 'NOON', TIME_CONFIG.PHASES.NOON); return 1 }))
                .then(Commands.literal('afternoon').executes(ctx => { setPhaseManually(ctx.source, 'AFTERNOON', TIME_CONFIG.PHASES.AFTERNOON); return 1 }))
                .then(Commands.literal('dusk').executes(ctx => { setPhaseManually(ctx.source, 'DUSK', TIME_CONFIG.PHASES.DUSK); return 1 }))
                .then(Commands.literal('night').executes(ctx => { setPhaseManually(ctx.source, 'NIGHT', TIME_CONFIG.PHASES.NIGHT); return 1 }))
                .then(Commands.literal('midnight').executes(ctx => { setPhaseManually(ctx.source, 'MIDNIGHT', TIME_CONFIG.PHASES.MIDNIGHT); return 1 }))
            )
    )
})