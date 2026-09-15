// Sanity range is 0-100, but the sanity mod uses 0-1, so we need to convert between the two

function get_sanity(player) {
    return 100*(1-player.nbt.ForgeCaps["sanitydim:sanity"]["sanity.sanity"])
}
function set_sanity(player, server, sanity) {
    let new_sanity = Math.max(0, Math.min(100, sanity))
    console.log("Setting sanity for player " + player.username + " to " + new_sanity)
    server.runCommandSilent(`sanity set ${player.username} ${new_sanity}`)
}

// Temperature range is 0-40 (realistic IRL), it has both setters and getters

function set_temperature(player, server, temperature) {
    let new_temperature = Math.max(0, Math.min(40, Math.round(temperature)))
    server.runCommandSilent(`execute as ${player.username} run temperature set ${new_temperature}`)
}

function get_temperature(player, server) {
    return player.nbt.ForgeCaps["legendarysurvivaloverhaul:temperature"]["temperature"]
}