// Priority: 800
// Server half of the survival HUD. It builds the lines and sends them to each player as a data packet
// ('winterheart_hud'); the panel itself is drawn by client_scripts/survival_hud.js. No sidebar mod is involved.

const SIDEBAR_CONFIG = {
    UPDATE_TICKS: 5       // 20 ticks = 1 second
}

// ==========================================
// 1. SAFE DATA EXTRACTORS
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
// 2. BUILD & SEND
// ==========================================
// Each line is sent as 'style|text'. Styles: section, normal, accent, warning, critical, muted, spacer.
// 'section' lines and the title are centred by the client; prefix any style with 'c:' (e.g. 'c:warning|text') to centre it too.
// 'weather' ('clear' | 'snow' | 'blizzard') picks how much snow sits on the HUD frame.
function updatePlayerSidebar(player, overworld) {
    let data = overworld.persistentData
    let currentDay = data.getInt('custom_day') || 1
    let currentPhase = data.getString('current_phase') || 'DAWN'
    let weather = getWeatherInfo(overworld)
    let ambientTemp = get_ambient_temperature(player).toPrecision(3)

    let lines = [
        'section|-- Forecast --',
        `normal|Day: ${currentDay}`,
        `${getPhaseStyle(currentPhase)}|Time: ${currentPhase}`,
        `${weather.style}|Weather: ${weather.label}`,
        'spacer|',
        `${getTempStyle(ambientTemp)}|Ambient: ${ambientTemp}°`
    ]

    // Appended by limb_poisoning.js when any limb is currently poised
    if (typeof getLimbPoiseSidebarLines === 'function') {
        let limbLines = getLimbPoiseSidebarLines(player)
        if (limbLines.length > 0) {
            lines.push('spacer|')
            lines.push('section|-- Limb Status --')
            limbLines.forEach(line => lines.push(`${line.style}|${line.text}`))
        }
    }

    let weatherKey = overworld.isThundering() ? 'blizzard' : overworld.isRaining() ? 'snow' : 'clear'
    player.sendData('winterheart_hud', { title: 'SURVIVAL', weather: weatherKey, lines: lines })
}

// ==========================================
// 3. TICK HOOK
// ==========================================
PlayerEvents.tick(event => {
    let player = event.player
    if (player.level.isClientSide()) return
    if (player.age % SIDEBAR_CONFIG.UPDATE_TICKS !== 0) return

    let overworld = player.server.getLevel('minecraft:overworld')
    if (!overworld) return

    updatePlayerSidebar(player, overworld)
})
