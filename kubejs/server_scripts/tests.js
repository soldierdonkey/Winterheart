ItemEvents.rightClicked(event => {
    const player = event.player
    const server = event.server
    const level = event.level
    let overworld = server.getLevel('minecraft:overworld')
    let data = overworld.persistentData

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
        case 'minecraft:quartz':
            player.tell('Quartz right-clicked! Running diagnostics!')
            player.tell(`Current temperature: ${get_temperature(player, server)}`)
            set_temperature(player, server, get_temperature(player, server) + 5)
            player.tell(`New temperature: ${get_temperature(player, server)}`)
            break
        case 'minecraft:nether_brick':
            player.tell('Nether Brick right-clicked! Running diagnostics!')
            break
    }
})