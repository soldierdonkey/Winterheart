ItemEvents.rightClicked(event => {
    const player = event.player
    const server = event.server
    const level = event.level
    let data = level.persistentData

    switch (event.item.id) {
        case 'minecraft:nether_star':
            player.tell('Nether Star right-clicked! Running diagnostics!')
            player.tell(`Current sanity: ${get_sanity(player)}`)
            set_sanity(player, server, get_sanity(player) + 10)
            player.tell(`New sanity: ${get_sanity(player)}`)
            break
        case 'minecraft:netherite_ingot':
            player.tell('Netherite Ingot right-clicked! Running diagnostics!')
            player.tell(`Current sanity: ${get_sanity(player)}`)
            set_sanity(player, server, get_sanity(player) - 10)
            player.tell(`New sanity: ${get_sanity(player)}`)
            break
        case 'minecraft:end_portal_frame':
            player.tell('End Portal Frame right-clicked! Running diagnostics!')
            player.tell(`Current sanity: ${get_sanity(player)}`)
            player.tell(`Previous sanity: ${data.getDouble("previous_sanity_" + player.uuid)}`)
            break
    }
})