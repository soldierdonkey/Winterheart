// Right-clicking permafrost with an axe chips out flint at a heavy durability cost
const PERMAFROST_BLOCKS = [
    'immersive_weathering:permafrost',
    'immersive_weathering:grassy_permafrost'
]
const PERMAFROST_AXE_DURABILITY = 20

BlockEvents.rightClicked(event => {
    if (event.hand !== 'main_hand') return
    if (!PERMAFROST_BLOCKS.includes(event.block.id)) return

    let item = event.item
    if (!item.hasTag('minecraft:axes')) return

    item.hurtAndBreak(PERMAFROST_AXE_DURABILITY, event.player, () => {
        event.player.broadcastBreakEvent('main_hand')
    })
    event.block.popItemFromFace('minecraft:flint', event.facing)
    event.level.playSound(null, event.block.pos, 'minecraft:block.gravel.break', 'blocks', 1, 1)
    event.player.swing()
    event.cancel()
})
