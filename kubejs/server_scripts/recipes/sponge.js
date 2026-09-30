ServerEvents.recipes(event => {
    // Remove all existing smelting and blasting recipes where a moss clump is the input
    // event.remove({ type: 'minecraft:smelting', ingredient: 'immersive_weathering:moss_clump' })
    event.remove({ id: 'immersive_weathering:green_dye_from_moss_clump' })

    // Add the new smelting recipe: moss clump -> dry sponge
    // Syntax: event.smelting(output, input).xp(experience).cookingTime(ticks)
    event.smelting('minecraft:sponge', 'immersive_weathering:moss_clump')
        .xp(0.1)
        .cookingTime(200) // 200 ticks = 10 seconds (standard furnace speed)
})
