ServerEvents.recipes(event => {

  /**
   * Helper to register recipes matching TACZ's internal serializer.
   *
   * @param {Object} config
   * @param {'gun'|'ammo'|'attachment'|'block'} [config.type='gun'] Output category string.
   * @param {string} config.id Target ID (e.g., 'tacz:m1911', 'tacz:9mm').
   * @param {number} [config.count] Quantity output (optional, defaults to 1 for guns/attachments).
   * @param {string} [config.group] Optional grouping identifier (e.g. 'pd_cartridges').
   * @param {Array<[string, number?]>} config.materials Array of [itemOrTag, count].
   * @param {string} [config.recipeId] Custom identifier for KubeJS.
   */
  function addGunSmithRecipe(config) {
    let outputType = config.type || 'gun'
    let recipeId = config.recipeId || `kubejs:tacz_workbench/${config.id.replace(':', '_')}`

    // 1. Build TACZ Result Object
    let resultPayload = {
      type: outputType,
      id: config.id
    }

    if (config.count && config.count > 1) {
      resultPayload.count = config.count
    }

    if (config.group) {
      resultPayload.group = config.group
    }

    // 2. Build Materials Array
    let materials = config.materials.map(([ingredient, amount]) => {
      let count = amount !== undefined ? amount : 1
      let itemObj = {}

      if (ingredient.startsWith('#')) {
        itemObj.tag = ingredient.substring(1)
      } else {
        itemObj.item = ingredient
      }

      let entry = { item: itemObj }
      if (count > 1) {
        entry.count = count
      }
      return entry
    })

    // 3. Register Custom Recipe
    event.custom({
      type: 'tacz:gun_smith_table_crafting',
      result: resultPayload,
      materials: materials
    }).id(recipeId)
  }

  // ==================================================
  // 1. AMMUNITION RECIPES
  // ==================================================

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:9mm',
    count: 60,
    materials: [
      ['winterheart:copper_plate', 3],
      ['winterheart:copper_ring', 3],
      ['minecraft:gunpowder', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:45acp',
    count: 45,
    materials: [
      ['winterheart:copper_plate', 3],
      ['winterheart:iron_bolt', 3],
      ['minecraft:gunpowder', 5]
    ]
  })

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:12g',
    count: 32,
    materials: [
      ['winterheart:copper_ring', 4],
      ['winterheart:iron_bolt', 4],
      ['minecraft:paper', 4],
      ['minecraft:gunpowder', 6]
    ]
  })

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:762x39',
    count: 40,
    materials: [
      ['winterheart:iron_plate', 4],
      ['winterheart:copper_ring', 4],
      ['minecraft:gunpowder', 8]
    ]
  })

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:308',
    count: 30,
    materials: [
      ['winterheart:iron_plate', 5],
      ['winterheart:copper_rod', 3],
      ['minecraft:gunpowder', 10]
    ]
  })

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:792x57',
    count: 25,
    materials: [
      ['winterheart:iron_plate', 4],
      ['winterheart:iron_rod', 3],
      ['minecraft:gunpowder', 10]
    ]
  })

  addGunSmithRecipe({
    type: 'ammo',
    id: 'tacz:45_70',
    count: 24,
    materials: [
      ['winterheart:copper_plate', 4],
      ['winterheart:iron_bolt', 4],
      ['minecraft:gunpowder', 8]
    ]
  })

  // ==================================================
  // 2. ATTACHMENTS & AMMO MODS
  // ==================================================

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:ammo_mod_hp',
    materials: [
      ['winterheart:copper_screw', 6],
      ['winterheart:iron_bolt', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:ammo_mod_fmj',
    materials: [
      ['winterheart:iron_plate', 4],
      ['winterheart:copper_plate', 4],
      ['winterheart:diamond_screw', 1]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:ammo_mod_slug',
    materials: [
      ['winterheart:iron_bolt', 8],
      ['winterheart:copper_ring', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:ammo_mod_he',
    materials: [
      ['winterheart:iron_plate', 4],
      ['minecraft:tnt', 2],
      ['minecraft:blaze_powder', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:ammo_mod_i',
    materials: [
      ['winterheart:copper_ring', 4],
      ['minecraft:fire_charge', 2],
      ['minecraft:blaze_powder', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:muzzle_choke_sg',
    materials: [
      ['winterheart:iron_ring', 4],
      ['winterheart:iron_screw', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:bayonet_6h3',
    materials: [
      ['winterheart:iron_plate', 4],
      ['winterheart:iron_rod', 2],
      ['winterheart:iron_screw', 2]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:scope_retro_2x',
    materials: [
      ['winterheart:iron_ring', 2],
      ['winterheart:iron_screw', 4],
      ['minecraft:spyglass', 1]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:scope_98k',
    materials: [
      ['winterheart:iron_plate', 2],
      ['winterheart:iron_ring', 3],
      ['winterheart:iron_screw', 4],
      ['minecraft:spyglass', 1]
    ]
  })

  addGunSmithRecipe({
    type: 'attachment',
    id: 'tacz:scope_1873_6x',
    materials: [
      ['winterheart:copper_ring', 4],
      ['winterheart:copper_screw', 4],
      ['winterheart:diamond_ring', 1],
      ['minecraft:spyglass', 1]
    ]
  })

  // ==================================================
  // 3. FIREARMS
  // ==================================================

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:m1911',
    materials: [
      ['winterheart:iron_plate', 4],
      ['winterheart:iron_rod', 2],
      ['winterheart:iron_screw', 3],
      ['winterheart:iron_gear', 1],
      ['minecraft:redstone', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:uzi',
    materials: [
      ['winterheart:iron_plate', 6],
      ['winterheart:iron_bolt', 6],
      ['winterheart:iron_gear', 6],
      ['winterheart:copper_ring', 4],
      ['minecraft:redstone', 64]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:db_short',
    materials: [
      ['winterheart:iron_rod', 4],
      ['winterheart:iron_plate', 2],
      ['winterheart:iron_bolt', 4],
      ['#minecraft:planks', 2],
      ['minecraft:redstone', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:db_long',
    materials: [
      ['winterheart:iron_rod', 6],
      ['winterheart:iron_plate', 4],
      ['winterheart:iron_bolt', 4],
      ['#minecraft:planks', 4],
      ['minecraft:redstone', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:m870',
    materials: [
      ['winterheart:iron_plate', 6],
      ['winterheart:iron_rod', 4],
      ['winterheart:iron_gear', 2],
      ['winterheart:iron_screw', 6],
      ['#minecraft:planks', 2],
      ['minecraft:redstone', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:springfield1873',
    materials: [
      ['winterheart:iron_rod', 5],
      ['winterheart:copper_ring', 4],
      ['winterheart:iron_screw', 4],
      ['winterheart:iron_gear', 1],
      ['#minecraft:planks', 4],
      ['minecraft:redstone', 1]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:kar98',
    materials: [
      ['winterheart:iron_rod', 6],
      ['winterheart:iron_plate', 4],
      ['winterheart:iron_bolt', 4],
      ['winterheart:iron_screw', 6],
      ['#minecraft:planks', 4],
      ['minecraft:redstone', 4]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:ak47',
    materials: [
      ['winterheart:iron_plate', 8],
      ['winterheart:iron_rod', 4],
      ['winterheart:iron_gear', 3],
      ['winterheart:iron_screw', 8],
      ['#minecraft:planks', 3],
      ['minecraft:redstone', 18]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:rpk',
    materials: [
      ['winterheart:iron_plate', 12],
      ['winterheart:iron_rod', 6],
      ['winterheart:iron_gear', 4],
      ['winterheart:iron_bolt', 8],
      ['#minecraft:planks', 4],
      ['minecraft:redstone', 64]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:fn_fal',
    materials: [
      ['winterheart:iron_plate', 8],
      ['winterheart:iron_rod', 4],
      ['winterheart:iron_gear', 3],
      ['winterheart:diamond_screw', 2],
      ['winterheart:copper_ring', 4],
      ['minecraft:redstone', 12]
    ]
  })

  addGunSmithRecipe({
    type: 'gun',
    id: 'tacz:hk_g3',
    materials: [
      ['winterheart:iron_plate', 10],
      ['winterheart:iron_rod', 4],
      ['winterheart:iron_gear', 3],
      ['winterheart:diamond_bolt', 2],
      ['winterheart:copper_ring', 4],
      ['minecraft:redstone', 12]
    ]
  })

  // ==================================================
  // 4. WORKBENCH BLOCKS
  // ==================================================

  addGunSmithRecipe({
    type: 'block',
    id: 'tacz:ammo_workbench',
    materials: [
      ['winterheart:copper_plate', 6],
      ['winterheart:copper_gear', 2],
      ['winterheart:iron_screw', 4],
      ['minecraft:crafting_table', 1]
    ]
  })

  addGunSmithRecipe({
    type: 'block',
    id: 'tacz:attachment_workbench',
    materials: [
      ['winterheart:iron_plate', 6],
      ['winterheart:iron_gear', 2],
      ['winterheart:diamond_ring', 1],
      ['winterheart:iron_screw', 6],
      ['minecraft:crafting_table', 1]
    ]
  })
  event.remove({ output: "tacz:gun_smith_table" })
  event.shaped(
    'tacz:gun_smith_table',
    [
        "GAG",
        "SFS",
        "S S"
    ],
    {
        'A': 'minecraft:anvil',
        'G': 'winterheart:iron_gear',
        'F': 'winterheart:iron_plate',
        'S': 'winterheart:iron_rod',
    }
  )
})