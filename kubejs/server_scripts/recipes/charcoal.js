ServerEvents.recipes(event => {
    event.campfireCooking(
        'minecraft:charcoal',   // Output
        '#minecraft:logs',      // Input
        0,                       // XP
        600                      // Cook time (ticks)
    )
})
