ServerEvents.recipes(event => {
    // Remove the default blaze rod recipe
    event.remove({ output: 'minecraft:brewing_stand' })

    // Alternative early-game recipe using Copper and Iron
    event.shaped('minecraft:brewing_stand', [
        ' I ',
        ' C ',
        'SSS'
    ], {
        I: 'minecraft:iron_ingot',
        C: 'minecraft:copper_ingot',
        S: 'minecraft:cobblestone'
    })
})
ServerEvents.tags("item", (event) => {
    event.remove("minecraft:brewing_stand_fuel", "minecraft:blaze_rod");
    event.add("minecraft:brewing_stand_fuel", "minecraft:charcoal");
    event.add("minecraft:brewing_stand_fuel", "minecraft:coal");
})