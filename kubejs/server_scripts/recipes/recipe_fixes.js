ServerEvents.recipes(event => {
    event.campfireCooking(
        'minecraft:charcoal',   // Output
        '#minecraft:logs',      // Input
        0,                       // XP
        600                      // Cook time (ticks)
    )
})
ServerEvents.tags('item', event => {
    event.add('notreepunching:weak_saws', 
        "minecraft:wooden_axe",
        "minecraft:stone_axe",
    )
    event.add('minecraft:animal_fat', 
        "butchery:animal_fat"
    )
})