function get_sanity(player) {
    return 100*(1-player.nbt.ForgeCaps["sanitydim:sanity"]["sanity.sanity"])
}
function set_sanity(player, server, sanity) {
    let new_sanity = Math.max(0, Math.min(100, sanity))
    server.runCommandSilent(`sanity set ${player.username} ${new_sanity}`)
}