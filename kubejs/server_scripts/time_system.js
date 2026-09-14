// Priority: 900

const TIME_CONFIG = {
    // Debug Settings
    DEBUG_MODE: false, // Enable debug messages in chat for all players
    DEBUG_INTERVAL: 200, // Ticks between debug messages (200 = 10 seconds)
    
    // Core Settings
    ETERNAL_NIGHT_DAY: 50,
    SLEEP_COOLDOWN: 200, // Ticks before sleep can trigger a day pass again (100 ticks = 5s)
    
    // Speed multipliers for configurable day/night lengths.
    // 1.0 = normal speed. 0.5 = twice as long. 2.0 = half as long.
    SPEEDS: {
        DAY: 0.8,   // Dawn to Dusk
        NIGHT: 1.2  // Dusk to Midnight
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
    
    // Only run time logic in the Overworld to prevent triple-ticking
    if (level.dimension.toString() !== 'minecraft:overworld' || level.isClientSide()) return

    let data = level.persistentData
    
    // Initialize custom timeline
    if (!data.contains('custom_day')) data.putInt('custom_day', 1)
    if (!data.contains('death_count')) data.putInt('death_count', 0)
    if (!data.contains('exact_time')) data.putDouble('exact_time', 0.0)
    if (!data.contains('current_phase')) data.putString('current_phase', 'DAWN')
    if (!data.contains('debug_timer')) data.putInt('debug_timer', 0)
    if (!data.contains('sleep_cooldown')) data.putInt('sleep_cooldown', 0)
    if (!data.contains('death_cooldown')) data.putInt('death_cooldown', 0)

    // Initialize data per player for sanity and checkpointing
    level.players.forEach(p => {
        if (!data.contains('previous_sanity_' + p.uuid)) data.putDouble('previous_sanity_' + p.uuid, 100.0)
    })

    let currentDay = data.getInt('custom_day')
    let exactTime = data.getDouble('exact_time')
    let currentPhase = data.getString('current_phase')
    let sleepCooldown = data.getInt('sleep_cooldown')
    let deathCooldown = data.getInt('death_cooldown')

    // Decrement cooldowns independently of daylight cycle
    if (sleepCooldown > 0) {
        data.putInt('sleep_cooldown', sleepCooldown - 1)
    }
    if (deathCooldown > 0) {
        data.putInt('death_cooldown', deathCooldown - 1)
    }

    // ----------------------------------------------------
    // PHASE: ETERNAL NIGHT (DAY 50+)
    // ----------------------------------------------------
    if (currentDay >= TIME_CONFIG.ETERNAL_NIGHT_DAY) {
        level.server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
        if (currentPhase !== 'ETERNAL_NIGHT') {
            data.putString('current_phase', 'ETERNAL_NIGHT')
            triggerTimeHook(level, currentDay, 'ETERNAL_NIGHT')
        }
        return // Halt all other time progression
    }

    // ----------------------------------------------------
    // CHECK SLEEP STATE (Pass Day Logic)
    // ----------------------------------------------------
    let players = level.players
    let allSleeping = players.length > 0 && players.every(p => p.isSleeping())

    if (allSleeping && sleepCooldown <= 0) {
        data.putInt('sleep_cooldown', TIME_CONFIG.SLEEP_COOLDOWN)

        // Advance to next day
        currentDay += 1
        data.putInt('custom_day', currentDay)
        data.putDouble('exact_time', 0.0)
        data.putString('current_phase', 'DAWN')
        level.server.runCommandSilent('time set 0')
        
        triggerTimeHook(level, currentDay, 'DAWN')

        // Set checkpoint immunity flag so the morning effect does not trigger the death handler
        data.putBoolean('registering_checkpoint', true)

        players.forEach(p => {
            p.stopSleeping()
            // Apply Earthquake Level 61 (amplifier 60) for 100 ticks (5 seconds)
            p.potionEffects.add('alexsmobs:earthquake', 10, 60, false, false)
            data.putDouble('previous_sanity_' + p.uuid, get_sanity(p))
            console.log(`[TimeSys] Player ${p.username} slept through the night. Day ${currentDay} begins.`)
        })

        // 5-tick timeout before registering checkpoint to allow effect to attach to NBT
        level.server.scheduleInTicks(5, () => {
            players.forEach(p => {
                let name = p.username
                p.server.runCommandSilent(`execute as ${name} run setcheckpoint @s`)
                p.server.runCommandSilent(`execute as ${name} run setSubaruPlayer @s`)
                p.tell(Text.gold(`You wake up. Day ${currentDay} begins...`))
            })

            // Clean up the effect from active players so they do not experience lingering screen shake
            level.server.scheduleInTicks(2, () => {
                players.forEach(p => {
                    p.removeEffect('alexsmobs:earthquake')
                })
                data.putBoolean('registering_checkpoint', false)
            })
        })
        return
    }

    // ----------------------------------------------------
    // NORMAL TIME PROGRESSION
    // ----------------------------------------------------
    if (exactTime < TIME_CONFIG.PHASES.MIDNIGHT) {
        let isNight = exactTime >= TIME_CONFIG.PHASES.DUSK
        let speed = isNight ? TIME_CONFIG.SPEEDS.NIGHT : TIME_CONFIG.SPEEDS.DAY
        
        exactTime += speed
        data.putDouble('exact_time', exactTime)
        
        level.server.runCommandSilent(`time set ${Math.floor(exactTime)}`)

        // Transition Checks
        let newPhase = currentPhase
        if (exactTime >= TIME_CONFIG.PHASES.MIDNIGHT) newPhase = 'MIDNIGHT'
        else if (exactTime >= TIME_CONFIG.PHASES.NIGHT && exactTime < TIME_CONFIG.PHASES.MIDNIGHT) newPhase = 'NIGHT'
        else if (exactTime >= TIME_CONFIG.PHASES.DUSK && exactTime < TIME_CONFIG.PHASES.NIGHT) newPhase = 'DUSK'
        else if (exactTime >= TIME_CONFIG.PHASES.AFTERNOON && exactTime < TIME_CONFIG.PHASES.DUSK) newPhase = 'AFTERNOON'
        else if (exactTime >= TIME_CONFIG.PHASES.NOON && exactTime < TIME_CONFIG.PHASES.AFTERNOON) newPhase = 'NOON'

        if (newPhase !== currentPhase) {
            data.putString('current_phase', newPhase)
            triggerTimeHook(level, currentDay, newPhase)
        }
    } else {
        // Time is frozen at Midnight. Waiting for players to sleep.
        level.server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
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
                    .append(Text.gray(`Cooldown: ${sleepCooldown}`)))
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

    // Debounce check: If cooldown is active, ignore repeated triggers
    let deathCooldown = data.getInt('death_cooldown') || 0
    if (deathCooldown > 0) return

    let death_count = data.getInt('death_count') || 0
    death_count += 1
    data.putInt('death_count', death_count)

    // Arm the debounce for 40 ticks (2 seconds)
    data.putInt('death_cooldown', 40)

    console.log(`[TimeSys] Player ${player.username} died on day ${day}. Resetting timeline to morning...`)

    // 1. Reset timeline back to morning (unless locked in Eternal Night)
    if (day < TIME_CONFIG.ETERNAL_NIGHT_DAY) {
        data.putDouble('exact_time', 0.0)
        data.putString('current_phase', 'DAWN')
        data.putInt('sleep_cooldown', TIME_CONFIG.SLEEP_COOLDOWN)
        server.runCommandSilent('time set 0')
        triggerTimeHook(overworld, day, 'DAWN')
    } else {
        server.runCommandSilent(`time set ${TIME_CONFIG.PHASES.MIDNIGHT}`)
    }

    // 2. Select and broadcast ominous motivational message
    let chosenSplash = OMINOUS_DEATH_SPLASH[0]
    let chosenMsg = GENERATE_OMINOUS_MESSAGE(death_count)
    let msgIndex = Math.floor(Math.random() * OMINOUS_DEATH_SPLASH.length)
    if (msgIndex < OMINOUS_DEATH_SPLASH.length) {
        chosenSplash = OMINOUS_DEATH_SPLASH[msgIndex]
    }

    server.players.forEach(p => {
        p.tell(Text.darkRed('☠ ').append(Text.of(chosenMsg).red().italic()))
        p.playNotifySound('minecraft:entity.warden.heartbeat', 'master', 1.0, 0.6)

        // Sanity penalty with a floor of 25 to avoid soft locks
        let new_sanity = Math.max(data.getDouble("previous_sanity_" + p.uuid) - 10, 25)
        set_sanity(p, server, new_sanity)
        data.putDouble("previous_sanity_" + p.uuid, new_sanity)
        
    })

    // 3. Cinematic screen title
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

    // Ignore while checkpoints are actively being initialized at morning
    if (data.getBoolean('registering_checkpoint')) return

    // Use KubeJS potionEffects API to verify and remove safely
    let effect = player.hasEffect('alexsmobs:earthquake')

    if (effect) {
        player.removeEffect('alexsmobs:earthquake')
        handleDeathLoopReset(player.server, player)
    }
})

// ==========================================
// 4. CUSTOM PHASE HOOKS
// ==========================================
function triggerTimeHook(level, day, phase) {
    switch(phase) {
        case 'DAWN':
            level.server.runCommandSilent('weather clear')
            break
            
        case 'NOON':
            break
            
        case 'AFTERNOON':
            if (day > 20) level.server.runCommandSilent('weather rain')
            break
            
        case 'DUSK':
            break
            
        case 'NIGHT':
            break
            
        case 'MIDNIGHT':
            level.players.forEach(p => p.tell(Text.darkRed("It is midnight. You must sleep to survive the cold.")))
            break
            
        case 'ETERNAL_NIGHT':
            level.server.runCommandSilent('weather thunder')
            break
    }
}

// ==========================================
// 5. DEBUG COMMAND REGISTRY
// ==========================================
ServerEvents.commandRegistry(event => {
    const { commands: Commands, arguments: Arguments } = event

    function sendFeedback(source, message) {
        if (source.player) {
            source.player.tell(message)
        } else {
            source.server.tell(message)
        }
    }

    function setPhaseManually(source, phaseName, ticks) {
        let level = source.server.getLevel('minecraft:overworld')
        let data = level.persistentData
        let day = data.getInt('custom_day') || 1

        data.putDouble('exact_time', Number(ticks))
        data.putString('current_phase', phaseName)
        level.server.runCommandSilent(`time set ${ticks}`)

        triggerTimeHook(level, day, phaseName)
        sendFeedback(source, Text.green(`[TimeSys] Set phase to `).append(Text.yellow(phaseName)).append(Text.green(` (Tick: ${ticks})`)))
    }

    event.register(
        Commands.literal('customtime')
            .requires(source => source.hasPermission(2))

            // --- QUERY STATUS ---
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

            // --- TOGGLE DEBUG ---
            .then(Commands.literal('debug')
                .executes(ctx => {
                    TIME_CONFIG.DEBUG_MODE = !TIME_CONFIG.DEBUG_MODE
                    let state = TIME_CONFIG.DEBUG_MODE ? Text.green('ENABLED') : Text.red('DISABLED')
                    sendFeedback(ctx.source, Text.gray('[TimeSys] Debug mode ').append(state))
                    return 1
                })
            )

            // --- SET DAY ---
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
                            triggerTimeHook(level, newDay, 'ETERNAL_NIGHT')
                        }

                        sendFeedback(ctx.source, Text.green(`[TimeSys] Set current day to: ${newDay}`))
                        return 1
                    })
                )
            )

            // --- SET EXACT TICKS ---
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

            // --- SKIP TO NEXT DAY ---
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

                    triggerTimeHook(level, nextDay, 'DAWN')

                    level.players.forEach(p => {
                        let name = p.username
                        p.server.runCommandSilent(`execute as ${name} run setcheckpoint @s`)
                        p.server.runCommandSilent(`execute as ${name} run setSubaruPlayer @s`)
                        p.tell(Text.gold(`[Admin Skip] Day ${nextDay} begins... Checkpoint updated.`))
                    })
                    return 1
                })
            )

            // --- JUMP TO SPECIFIC PHASE ---
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