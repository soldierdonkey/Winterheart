// Priority: 800

ServerEvents.recipes(event => {
    const STRING = '#notreepunching:string'
    const LEATHER = 'minecraft:leather'
    const WOOD = '#minecraft:planks'
    const STICK = 'minecraft:stick'
    const CHAIN = 'minecraft:chain'
    const IRON_NUGGET = 'minecraft:iron_nugget'

    const STONE_MATERIALS = '#minecraft:stone_tool_materials'

    const HIGH_TIERS = [
        { name: 'iron', item: 'minecraft:iron_ingot' },
        { name: 'golden', item: 'minecraft:gold_ingot' },
        { name: 'diamond', item: 'minecraft:diamond' }
    ]

    // =======================================================
    // 1. SCAFFOLDING HELPERS
    // =======================================================

    /**
     * Registers armor recipes requiring internal lining/strapping
     */
    function scaffoldArmor(tierName, materialItem, liningItem) {
        const helmet = `minecraft:${tierName}_helmet`
        const chestplate = `minecraft:${tierName}_chestplate`
        const leggings = `minecraft:${tierName}_leggings`
        const boots = `minecraft:${tierName}_boots`

        event.remove({ output: [helmet, chestplate, leggings, boots] })

        const keys = {
            M: materialItem,
            L: liningItem
        }

        // Helmet (1 Lining item)
        event.shaped(helmet, [
            'MMM',
            'MLM'
        ], keys)

        // Chestplate (2 Lining items)
        event.shaped(chestplate, [
            'M M',
            'MLM',
            'MLM'
        ], keys)

        // Leggings (2 Lining items)
        event.shaped(leggings, [
            'MMM',
            'M M',
            'L L'
        ], keys)

        // Boots (2 Lining items)
        event.shaped(boots, [
            'M M',
            'M M',
            'L L'
        ], keys)
    }

    /**
     * Registers tool recipes requiring grip/binding wrapping the handle
     */
    function scaffoldTools(tierName, materialItem, bindingItem) {
        const sword = `minecraft:${tierName}_sword`
        const pickaxe = `minecraft:${tierName}_pickaxe`
        const axe = `minecraft:${tierName}_axe`
        const shovel = `minecraft:${tierName}_shovel`
        const hoe = `minecraft:${tierName}_hoe`

        event.remove({ output: [sword, pickaxe, axe, shovel, hoe] })

        const baseKeys = {
            M: materialItem,
            S: STICK,
            B: bindingItem
        }

        // Sword
        event.shaped(sword, [
            ' M ',
            ' M ',
            'BS '
        ], baseKeys)

        // Pickaxe
        event.shaped(pickaxe, [
            'MMM',
            'BSB',
            ' S '
        ], baseKeys)

        // Axe
        event.shaped(axe, [
            'MM ',
            'MSB',
            'BS '
        ], baseKeys)

        // Shovel
        event.shaped(shovel, [
            ' M ',
            'BSB',
            ' S '
        ], baseKeys)

        // Hoe
        event.shaped(hoe, [
            'MM ',
            'BSB',
            ' S '
        ], baseKeys)
    }

    // =======================================================
    // 2. LEATHER ARMOR (REQUIRES STRING THREADING)
    // =======================================================
    scaffoldArmor('leather', LEATHER, STRING)
    scaffoldTools('wooden', WOOD, STRING)

    // =======================================================
    // 3. CHAINMAIL ARMOR (IRON NUGGETS, CHAINS & STRING)
    // =======================================================
    event.remove({ output: [
        'minecraft:chainmail_helmet',
        'minecraft:chainmail_chestplate',
        'minecraft:chainmail_leggings',
        'minecraft:chainmail_boots'
    ] })

    const chainKeys = {
        N: IRON_NUGGET,
        C: CHAIN,
        S: STRING
    }

    // Chainmail Helmet
    event.shaped('minecraft:chainmail_helmet', [
        'NNN',
        'CSC'
    ], chainKeys)

    // Chainmail Chestplate
    event.shaped('minecraft:chainmail_chestplate', [
        'N N',
        'CSC',
        'NNN'
    ], chainKeys)

    // Chainmail Leggings
    event.shaped('minecraft:chainmail_leggings', [
        'NNN',
        'CSC',
        'N N'
    ], chainKeys)

    // Chainmail Boots
    event.shaped('minecraft:chainmail_boots', [
        'N N',
        'CSC'
    ], chainKeys)

    // =======================================================
    // 4. HIGH-TIER ARMORS (IRON, GOLD, DIAMOND REQUIRE LEATHER)
    // =======================================================
    HIGH_TIERS.forEach(tier => {
        scaffoldArmor(tier.name, tier.item, LEATHER)
    })

    // =======================================================
    // 5. STONE TOOLS (REQUIRES STRING BINDINGS)
    // =======================================================
    scaffoldTools('stone', STONE_MATERIALS, STRING)

    // =======================================================
    // 6. HIGH-TIER TOOLS (IRON, GOLD, DIAMOND REQUIRE LEATHER)
    // =======================================================
    HIGH_TIERS.forEach(tier => {
        scaffoldTools(tier.name, tier.item, LEATHER)
    })
})