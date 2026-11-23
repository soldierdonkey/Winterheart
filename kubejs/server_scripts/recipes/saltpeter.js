// --- STEP 1: LEACHING & CRAFTING RECIPES ---
ServerEvents.recipes(event => {
    // Bottle scale leaching: 1 Rich Soil + Water Bucket + 3 Bottles -> 3 Crude Bottles + Dirt + Bucket
    event.shapeless(
        Item.of('kubejs:crude_nitrate_bottle', 4),
        [
            'farmersdelight:rich_soil',
            'legendarysurvivaloverhaul:purified_water_bottle',
            'legendarysurvivaloverhaul:purified_water_bottle',
            'legendarysurvivaloverhaul:purified_water_bottle',
            'legendarysurvivaloverhaul:purified_water_bottle'
        ]
    ).replaceIngredient('farmersdelight:rich_soil', 'minecraft:dirt');

    // STEP 3 (smelting to saltpeter) lives in config/paxi/datapacks/winterheart_recipes
    // (python/recipes/gen_recipes.py). This recipe stays here until the core mod adds a
    // recipe type that can replace an ingredient with a remainder (docs/todo/general-kubejs-removal.md).
});

// --- CONTAINER POP-OUT SYSTEM ---
ItemEvents.smelted(event => {
    const { player, item, level } = event;
    
    let returnItem = null;
    let count = item.count;

    console.log(item)

    // Distinguish bottle vs bucket yields
    if (item.id === 'kubejs:saltpeter') {
        returnItem = 'minecraft:glass_bottle';
    }

    if (!returnItem) return;

    // Locate the furnace block from the player's open container
    let pos = null;
    if (player.containerMenu && player.containerMenu.container && player.containerMenu.container.blockPos) {
        pos = player.containerMenu.container.blockPos;
    } else {
        // Fallback: Check the block the player is directly facing
        let ray = player.rayTrace(6);
        if (ray && ray.block && ray.block.hasTag('minecraft:furnaces')) {
            pos = ray.block.pos;
        }
    }

    if (pos) {
        // Summon the item entity directly inside the furnace center
        let itemEntity = level.createEntity('item');
        itemEntity.x = pos.x + 0.5;
        itemEntity.y = pos.y + 0.5;
        itemEntity.z = pos.z + 0.5;
        itemEntity.item = Item.of(returnItem, count);

        // Add a slight upward velocity so it ejects clearly from the block
        itemEntity.motionX = (Math.random() - 0.5) * 0.1;
        itemEntity.motionY = 0.25;
        itemEntity.motionZ = (Math.random() - 0.5) * 0.1;

        itemEntity.spawn();
    } else {
        // Inventory fallback if the furnace position cannot be resolved
        player.give(Item.of(returnItem, count));
    }
});