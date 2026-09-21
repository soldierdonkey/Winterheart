ServerEvents.recipes(event => {
    // Remove the default blaze rod recipe
    event.remove({ output: 'minecraft:brewing_stand' })

    // Alternative early-game recipe using Copper and Iron
    event.shaped('minecraft:brewing_stand', [
        ' I ',
        'RRR',
        'SSS'
    ], {
        I: 'winterheart:iron_rod',
        C: 'minecraft:copper_ingot',
        R: 'winterheart:gold_ring',
        S: 'minecraft:cobblestone'
    })
})
ServerEvents.tags("item", (event) => {
    event.remove("minecraft:brewing_stand_fuel", "minecraft:blaze_rod");
    event.add("minecraft:brewing_stand_fuel", "minecraft:charcoal");
    event.add("minecraft:brewing_stand_fuel", "minecraft:coal");
})