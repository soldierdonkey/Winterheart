ServerEvents.tags('block', event => {

    // Fill-in tags

    event.add('butchery:skeletons', 
        ['butchery:pig_skeleton', 'butchery:cow_skeleton', 'butchery:sheep_skeleton', 'butchery:chicken_skeleton', 'butchery:bat_skeleton', 'butchery:dolphin_skeleton', 'butchery:goat_skeleton', 'butchery:mooshroom_skeleton', 'butchery:camel_skeleton', 'butchery:creeper_skeleton', 'butchery:donkey_skeleton', 'butchery:mule_skeleton', 'butchery:fox_skeleton', 'butchery:hoglin_skeleton', 'butchery:panda_skeleton', 'butchery:piglin_skeleton', 'butchery:polarbear_skeleton', 'butchery:skeleton_corpse', 'butchery:llama_skeleton', 'butchery:axolotl_skeleton', 'butchery:phantom_skeleton', 'butchery:cat_skeleton', 'butchery:wolf_skeleton', 'butchery:skeleton_horse_carcass', 'butchery:ocelot_skeleton', 'butchery:skeleton', 'butchery:wither_skeleton_corpse', 'butchery:skeleton_horse_head', 'butchery:fish_skeleton']
    )

    // Proximity tags

    event.add('winterheart:rotten_gore', 
        ['butchery:wither_skeleton_corpse', 'butchery:stray_corpse', 'butchery:skeleton_corpse', 'butchery:villager_corpse', 'butchery:drowned_corpse', 'butchery:drained_drowned_corpse', 'butchery:zombie_corpse', 'butchery:drained_zombie_corpse', 'butchery:zombie_villager_corpse', 'butchery:playercorpse', 'butchery:drainedplayercorpse', 'antlers:sheep_corpse', 'antlers:bloody_cow', 'antlers:bloody_fox', 'antlers:bloody_pig', 'minecraft:wither_skeleton_skull']
    )
    event.add('winterheart:gore', 
        ['#butchery:carcass', '#butchery:heads', 'butchery:blood', 'butchery:blood_splatter', 'butchery:blood_puddle']
    )
    event.add('winterheart:carcass', 
        ['#butchery:skeletons', '#butchery:drained_carcass', '#butchery:skulls', 'butchery:bone_barrel']
    )
    event.add('winterheart:cooking', 
        ['farmersdelight:skillet', 'farmersdelight:cooking_pot', 'farmersdelight:stove', 'hardcore_torches:stove']
    )
    event.add('winterheart:furniture', 
        ['#minecraft:anvil', 'minecraft:cartography_table', 'minecraft:stonecutter', 'butchery:taxidermy_table', 'tacz:workbench_a', 'minecraft:lectern', 'tacz:workbench_c', 'tacz:gun_smith_table', 'legendarysurvivaloverhaul:heater', 'minecraft:jukebox', 'hardcore_torches:unlit_lantern']
    )
    event.add('winterheart:warmth',
        ['#minecraft:wool_carpets', '#minecraft:wool', '#minecraft:beds', 'minecraft:campfire', 'minecraft:jack_o_lantern', 'hardcore_torches:lit_lantern']
    )
    event.add('winterheart:foliage',
        ['#dynamictrees:branches', '#dynamictrees:foliage', '#minecraft:crops', '#minecraft:bee_growables', '#minecraft:flower_pots', '#minecraft:flowers']
    )
})