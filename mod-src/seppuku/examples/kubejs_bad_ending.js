// Example Winterheart hook: drop into kubejs/server_scripts/mechanics/.
// Uses the pack's get_sanity() helper (server_scripts/utils.js/helper_functions.js)
// and the custom_day counter kept by time_system.js.

const BAD_ENDING_DAY = 50
const BAD_ENDING_SANITY = 20      // trigger at or below this sanity (0-100)
const BAD_ENDING_DEATH_TICKS = 60 // the animation is 59 ticks; it then holds the final pose

ServerEvents.tick(event => {
    const server = event.server
    if (server.tickCount % 20 !== 0) return

    const day = server.overworld().persistentData.getInt('custom_day') || 1
    if (day < BAD_ENDING_DAY) return

    server.players.forEach(player => {
        if (player.tags.contains('winterheart_bad_ending')) return
        if (get_sanity(player) > BAD_ENDING_SANITY) return

        player.addTag('winterheart_bad_ending')
        // Freeze the player for the cutscene.
        server.runCommandSilent(`effect give ${player.username} minecraft:slowness 10 255 true`)
        server.runCommandSilent(`effect give ${player.username} minecraft:jump_boost 10 128 true`)
        server.runCommandSilent(`seppuku play ${player.username}`)

        server.scheduleInTicks(BAD_ENDING_DEATH_TICKS, () => {
            player.kill()
            server.runCommandSilent(`seppuku stop ${player.username}`)
        })
    })
})
