EntityEvents.spawned(event => {
    let entity = event.entity
    let level = event.level
    if (level.dimension.toString() !== 'minecraft:overworld' || level.isClientSide()) return
    let data = level.persistentData
    let day = data.getInt('custom_day') || 1
    if (!entity || event.level.isClientSide()) return
    if (SPAWNER_CONFIG.LIVESTOCK_POOL.includes(entity.id) && day > 30) {
        console.log("Blocked entity: " + entity.id + " from spawning!")
        event.cancel()
        entity.discard()
    }
})

ServerEvents.loaded(event => {
    event.server.runCommandSilent('gamerule doMobSpawning false')
    console.log('[Spawner Engine] Gamerule doMobSpawning locked to false.')
})