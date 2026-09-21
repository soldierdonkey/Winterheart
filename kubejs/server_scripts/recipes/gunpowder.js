ServerEvents.recipes(event => {
    event.remove({ output: 'minecraft:gunpowder' })
    event.shapeless(
        '16x minecraft:gunpowder',      // Output
        [
            '6x kubejs:saltpeter',      // 6 Saltpeter 
            'butchery:sulfur',          // 1 Sulfur 
            '2x #minecraft:coals'       // 2 Charcoal
        ]
    )
})
