// ==========================================
// BEACON SINK
// Right-clicking a beacon shifts the four blocks orthogonally
// adjacent to it (N/S/E/W) down by one, overwriting what's below.
// ==========================================
BlockEvents.rightClicked('minecraft:beacon', event => {
    if (event.hand != 'MAIN_HAND') return

    let level = event.level
    let pos = event.block.pos
    let offsets = [[1, 0], [-1, 0], [0, 1], [0, -1]]

    offsets.forEach(o => {
        let from = pos.offset(o[0], 0, o[1])
        let state = level.getBlockState(from)
        if (state.isAir()) return

        level.setBlock(from.below(), state, 3)
        level.removeBlock(from, false)
    })
    event.cancel()
})
