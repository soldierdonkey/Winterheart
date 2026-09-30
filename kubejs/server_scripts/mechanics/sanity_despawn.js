// Despawns sanity-dimension stalkers once every player is above the sanity threshold

const SANITY_DESPAWN_THRESHOLD = 75
const SANITY_DESPAWN_MOBS = ['sanitydim:rotting_stalker', 'sanitydim:sneaking_terror']

ServerEvents.tick(event => {
    const server = event.server
    if (server.tickCount % 20 !== 0) return

    const players = server.players
    if (players.length === 0) return
    if (!players.every(p => get_sanity(p) > SANITY_DESPAWN_THRESHOLD)) return
    server.getAllLevels().forEach(level => {
        level.entities.forEach(entity => {
            if (SANITY_DESPAWN_MOBS.includes(String(entity.type))) {
                entity.discard()
                entity.kill()
            }
        })
    })
})
