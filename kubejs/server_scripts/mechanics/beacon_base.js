// ==========================================
// BEACON BASE
// Right-clicking a beacon replaces the 3x3 of blocks directly
// beneath it with iron blocks, giving it a tier-1 pyramid base.
// ==========================================
BlockEvents.rightClicked('minecraft:beacon', event => {
    if (event.hand != 'MAIN_HAND') return

    let level = event.level
    let below = event.block.pos.below()

    for (let dx = -1; dx <= 1; dx++) {
        for (let dz = -1; dz <= 1; dz++) {
            level.setBlock(below.offset(dx, 0, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3)
        }
    }
    event.cancel()
})
