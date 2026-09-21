// kubejs/server_scripts/metal_parts_recipes.js

// 1. Tag Normalization (ensures compatibility with modded hammers)
ServerEvents.tags('item', event => {
  event.add('forge:hammer', [
    '#forge:hammers',
    '#forge:tools/hammers',
    '#c:tools/hammers',
    '#c:hammers',
    '#c:hammer',
  ]);
});

// 2. Crafting Progression
ServerEvents.recipes(event => {
  const materials = [
    { name: 'iron',    base: '#forge:ingots/iron' },
    { name: 'gold',    base: '#forge:ingots/gold' },
    { name: 'copper',  base: '#forge:ingots/copper' },
    { name: 'diamond', base: '#forge:gems/diamond' }
  ];

  materials.forEach(mat => {
    const plate = `winterheart:${mat.name}_plate`;
    const rod   = `winterheart:${mat.name}_rod`;
    const ring  = `winterheart:${mat.name}_ring`;
    const bolt  = `winterheart:${mat.name}_bolt`;
    const screw = `winterheart:${mat.name}_screw`;
    const gear  = `winterheart:${mat.name}_gear`;

    // Ingot/Gem -> Plate
    event.shapeless(plate, [mat.base, '#forge:hammer'])
      .damageIngredient('#forge:hammer')
      .id(`winterheart:crafting/${mat.name}_plate_from_hammer`);

    // Plate -> 2 Rods
    event.shapeless(Item.of(rod, 2), [plate, '#forge:hammer'])
      .damageIngredient('#forge:hammer')
      .id(`winterheart:crafting/${mat.name}_rods_from_plate`);

    // Rod -> Ring
    event.shapeless(ring, [rod, '#forge:hammer'])
      .damageIngredient('#forge:hammer')
      .id(`winterheart:crafting/${mat.name}_ring_from_rod`);

    // Rod -> 2 Bolts
    event.shapeless(Item.of(bolt, 2), [rod, '#forge:hammer'])
      .damageIngredient('#forge:hammer')
      .id(`winterheart:crafting/${mat.name}_bolts_from_rod`);

    // Bolt -> Screw
    event.shapeless(screw, [bolt, '#forge:hammer'])
      .damageIngredient('#forge:hammer')
      .id(`winterheart:crafting/${mat.name}_screw_from_bolt`);

    // 4 Plates + 1 Ring + Hammer -> Gear
    event.shaped(gear, [
      'BPB',
      'PRP',
      'BHB'
    ], {
      P: plate,
      R: ring,
      B: bolt,
      H: '#forge:hammer'
    })
    .damageIngredient('#forge:hammer')
    .id(`winterheart:crafting/${mat.name}_gear_from_plates_and_ring`);
  });
});