// Priority: 800

const SIDEBAR_CONFIG = {
    DEBUG: false,           // Set to true to print command results & stats to console
    UPDATE_TICKS: 20,      // 20 ticks = 1 second
    GLOBAL_MODE: true      // Set true for singleplayer/testing to guarantee visibility
}

// In-memory tracker that resets whenever scripts reload (/kjs reload server)
global.activeSidebars = {}

// ==========================================
// 1. COMMAND WRAPPER WITH LOGGING
// ==========================================
function execSidebarCmd(server, cmd) {
    if (SIDEBAR_CONFIG.DEBUG) {
        // runCommand prints Minecraft syntax errors to latest.log/console
        let code = server.runCommand(cmd)
        if (code === 0) {
            console.warn(`[Sidebar WARN] Command failed or returned 0: /${cmd}`)
        }
        return code
    }
    return server.runCommandSilent(cmd)
}

const CustomSidebar = {
    init(server, id, title) {
        execSidebarCmd(server, `cssidebar add ${id} "${title}"`)
        return this
    },
    set(server, id, property, value) {
        execSidebarCmd(server, `cssidebar set ${id} ${property} ${value}`)
        return this
    },
    clearLines(server, id) {
        execSidebarCmd(server, `cssidebar line clear ${id}`)
        return this
    },
    addText(server, id, style, text) {
        // Do NOT enclose text in quotes; cssidebar expects raw tokens
        execSidebarCmd(server, `cssidebar line add ${id} text ${style} "${text}"`)
        return this
    },
    addSpacer(server, id) {
        execSidebarCmd(server, `cssidebar line add ${id} spacer`)
        return this
    }
}

// ==========================================
// 2. SAFE DATA EXTRACTORS
// ==========================================


function getWeatherInfo(level) {
    if (level.isThundering()) return { label: 'Blizzard', style: 'critical' }
    if (level.isRaining()) return { label: 'Snowfall', style: 'warning' }
    return { label: 'Clear', style: 'accent' }
}

function getPhaseStyle(phase) {
    switch (phase) {
        case 'DAWN':
        case 'NOON':
            return 'accent'
        case 'AFTERNOON':
        case 'DUSK':
            return 'warning'
        case 'NIGHT':
        case 'MIDNIGHT':
        case 'ETERNAL_NIGHT':
            return 'critical'
        default:
            return 'normal'
    }
}

function getTempStyle(temp) {
    if (temp <= 25) return 'critical'
    if (temp <= 40) return 'warning'
    return 'accent'
}

// ==========================================
// 3. INITIALIZATION & REBUILD
// ==========================================
function setupPlayerSidebar(server, player, id) {
    if (SIDEBAR_CONFIG.DEBUG) {
        console.log(`[Sidebar] Initializing sidebar "${id}" for ${player.username}...`)
    }

    CustomSidebar.init(server, id, 'SURVIVAL')
        .set(server, id, 'layer', 'hud')
        .set(server, id, 'priority', 100)
        .set(server, id, 'global', SIDEBAR_CONFIG.GLOBAL_MODE)
        .set(server, id, 'exclusive', false)
        .set(server, id, 'anchor', 'top_right')
        .set(server, id, 'width', 130)
        .set(server, id, 'scale', 0.9)
        .set(server, id, 'offset_x', -5)
        .set(server, id, 'offset_y', 5)
        .set(server, id, 'theme', 'technological')

    // Fallback: If not using global, attempt client-side display command
    if (!SIDEBAR_CONFIG.GLOBAL_MODE) {
        execSidebarCmd(server, `execute as ${player.username} run cssidebar show ${id}`)
    }
}

function updatePlayerSidebar(server, player, overworld, id) {
    let data = overworld.persistentData
    let currentDay = data.getInt('custom_day') || 1
    let currentPhase = data.getString('current_phase') || 'DAWN'
    let weather = getWeatherInfo(overworld)
    let ambientTemp = get_ambient_temperature(player).toPrecision(3)

    let phaseStyle = getPhaseStyle(currentPhase)
    let tempStyle = getTempStyle(ambientTemp)

    if (SIDEBAR_CONFIG.DEBUG && player.age % 100 === 0) {
        console.log(`[Sidebar Tick] ${player.username} -> Day: ${currentDay}, Phase: ${currentPhase}, Temp: ${ambientTemp}, Weather: ${weather.label}`)
    }

    CustomSidebar.clearLines(server, id)
        .addText(server, id, 'section', '-- Forecast --')
        .addText(server, id, 'normal', `Day: ${currentDay}`)
        .addText(server, id, phaseStyle, `Time: ${currentPhase}`)
        .addText(server, id, weather.style, `Weather: ${weather.label}`)
        .addSpacer(server, id)
        .addText(server, id, tempStyle, `Ambient: ${ambientTemp}°`)
}

// ==========================================
// 4. TICK HOOK
// ==========================================
PlayerEvents.tick(event => {
    let player = event.player
    if (player.level.isClientSide()) return

    // Limit execution to once every second (20 ticks) per player
    if (player.age % SIDEBAR_CONFIG.UPDATE_TICKS !== 0) return

    let server = player.server
    let overworld = server.getLevel('minecraft:overworld')
    if (!overworld) return

    let sidebarId = SIDEBAR_CONFIG.GLOBAL_MODE ? 'surv_hud' : `surv_${player.username}`

    // Automatically re-initialize when missing (handles /kjs reload server cleanly)
    if (!global.activeSidebars[sidebarId]) {
        setupPlayerSidebar(server, player, sidebarId)
        global.activeSidebars[sidebarId] = true
    }

    updatePlayerSidebar(server, player, overworld, sidebarId)
})