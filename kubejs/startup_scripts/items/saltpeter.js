StartupEvents.registry('item', event => {
    // Final product: pure potassium nitrate crystals
    event.create('saltpeter')
        .displayName('Saltpeter Crystals')
        .tooltip('§7Potassium Nitrate (KNO₃). Vital for gunpowder and fertilizers.')
        .texture('winterheart:item/saltpeter');

    // Leached solution (calcium & potassium nitrate)
    event.create('crude_nitrate_bottle')
        .displayName('Crude Nitrate Solution')
        .maxStackSize(16)
        .tooltip('§7Unrefined leachate extracted from nitrate-rich earth.')
        .texture('winterheart:item/crude_nitrate_bottle');

    // Converted solution (treated with wood ash)
    event.create('potassium_nitrate_bottle')
        .displayName('Potassium Nitrate Solution')
        .maxStackSize(16)
        .tooltip('§7Filtered with wood ash to convert calcium nitrate into potassium nitrate.')
        .texture('winterheart:item/potassium_nitrate_bottle');
});

// --- STEP 2: BREWING STAND CONVERSION (MOREJS) ---
MoreJSEvents.registerPotionBrewing(event => {
    // Brew charred planks (potassium-rich wood ash) into crude nitrate bottles
    event.addCustomBrewing(
        'immersive_weathering:charred_planks',
        'kubejs:crude_nitrate_bottle',
        'kubejs:potassium_nitrate_bottle'
    );
});