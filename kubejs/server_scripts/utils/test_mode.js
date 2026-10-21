// /toggle_test: freezes the custom time system at noon and blocks mob spawns.
// Not saved: resets to off on server restart. Guards live in time_system.js and spawning.js.
if (global.testMode === undefined) global.testMode = false

// Natural, spawner and mod biome spawns (scripted spawners are guarded in spawning.js)
EntityEvents.checkSpawn(event => {
    if (global.testMode) event.cancel()
})

ServerEvents.commandRegistry(event => {
    const { commands: Commands } = event

    event.register(
        Commands.literal('toggle_test')
            .requires(source => source.hasPermission(2))
            .executes(ctx => {
                global.testMode = !global.testMode
                let server = ctx.source.server
                if (global.testMode) server.runCommandSilent('time set 6000')

                let state = global.testMode ? Text.green('ON (always day, no mob spawns)') : Text.red('OFF')
                let msg = Text.gray('[Test] Test mode ').append(state)
                if (ctx.source.player) ctx.source.player.tell(msg)
                else server.tell(msg)
                return 1
            })
    )
})
