// Plays the seppuku animation when a player's sanity drops to the threshold

const SEPPUKU_SANITY_THRESHOLD = 10
const SEPPUKU_PROBE_INTERVAL = 200 // ticks (10 seconds)

// Players who have already played the animation; cleared once they recover above the threshold
const seppuku_triggered = {}

ServerEvents.tick(event => {
    const server = event.server
    if (server.tickCount % SEPPUKU_PROBE_INTERVAL !== 0) return

    server.players.forEach(player => {
        const name = player.username
        if (get_sanity(player) <= SEPPUKU_SANITY_THRESHOLD) {
            if (seppuku_triggered[name]) return
            seppuku_triggered[name] = true
            server.runCommandSilent(`seppuku play ${name}`)
        } else {
            delete seppuku_triggered[name]
        }
    })
})
