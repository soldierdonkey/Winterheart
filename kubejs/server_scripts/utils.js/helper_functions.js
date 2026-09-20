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

function get_ambient_temperature(player, server) {
    return player.nbt.ForgeCaps["legendarysurvivaloverhaul:temperature"]["targettemperature"]
}

// Priority: 950

global.ForgeCaps = {
    /**
     * Reads a value from a player's ForgeCaps NBT.
     * @param {Internal.ServerPlayer} player
     * @param {string} capId e.g. "legendarysurvivaloverhaul:temperature"
     * @param {string} [attribute] The specific key to read. If omitted, returns the whole capability map.
     * @returns {*} The attribute value, or null if missing.
     */
    get(player, capId, attribute) {
        if (!player || !player.nbt) return null
        let caps = player.nbt.ForgeCaps
        if (!caps || !caps[capId]) return null

        let capData = caps[capId]
        if (attribute === undefined) return capData
        return capData[attribute] !== undefined ? capData[attribute] : null
    },

    /**
     * Merges a value or object into a player's ForgeCaps using recursive NBT merging.
     * @param {Internal.ServerPlayer} player
     * @param {string} capId e.g. "legendarysurvivaloverhaul:temperature"
     * @param {string|object} attribute Key string, or an object of { key: value } pairs.
     * @param {*} [value] The value to set (ignored if attribute is an object).
     * @returns {boolean} True if successfully merged.
     */
    set(player, capId, attribute, value) {
        if (!player) return false

        let capPayload = {}
        if (typeof attribute === 'object' && attribute !== null) {
            capPayload = attribute
        } else {
            capPayload[attribute] = value
        }

        // player.mergeNbt deep-merges CompoundTags recursively,
        // preserving other capabilities and sibling attributes.
        let nbtUpdate = {ForgeCaps: {}}
        nbtUpdate.ForgeCaps[capId] = capPayload
        player.mergeNbt(nbtUpdate)
        return true
    }
}

// Global functional aliases
function getForgeCap(player, capId, attribute) {
    return global.ForgeCaps.get(player, capId, attribute)
}

function setForgeCap(player, capId, attribute, value) {
    return global.ForgeCaps.set(player, capId, attribute, value)
}

// Priority: 940

global.CapPersistence = {
    DEFAULT_PREFIX: 'cap_backup_',

    /**
     * Saves all data inside a specific ForgeCap into the Overworld's persistentData.
     * @param {Internal.ServerPlayer} player
     * @param {string} capId e.g. "legendarysurvivaloverhaul:temperature"
     * @param {string} [customPrefix] Optional key prefix
     * @returns {boolean} True if data was found and saved
     */
    saveCap(player, capId, customPrefix) {
        if (!player || !player.nbt) return false
        
        let caps = player.nbt.ForgeCaps
        if (!caps || !caps[capId]) return false

        let overworld = player.server.getLevel('minecraft:overworld')
        let prefix = customPrefix || this.DEFAULT_PREFIX
        let key = `${prefix}${player.uuid}_${capId}`

        // Clone the capability tag directly into persistentData
        overworld.persistentData.put(key, caps[capId])
        return true
    },

    /**
     * Loads saved ForgeCap data from level persistentData back into the player's ForgeCaps.
     * @param {Internal.ServerPlayer} player
     * @param {string} capId e.g. "legendarysurvivaloverhaul:temperature"
     * @param {string} [customPrefix] Optional key prefix
     * @returns {boolean} True if data was found and merged
     */
    loadCap(player, capId, customPrefix) {
        if (!player) return false

        let overworld = player.server.getLevel('minecraft:overworld')
        let prefix = customPrefix || this.DEFAULT_PREFIX
        let key = `${prefix}${player.uuid}_${capId}`

        if (!overworld.persistentData.contains(key)) return false

        let savedData = overworld.persistentData.get(key)

        // Deep-merge the saved capability compound back into the player's ForgeCaps
        let nbtUpdate = {ForgeCaps: {}}
        nbtUpdate.ForgeCaps[capId] = savedData
        player.mergeNbt(nbtUpdate)
        return true
    },

    /**
     * Saves ALL active ForgeCaps present on the player to level persistentData.
     * @param {Internal.ServerPlayer} player
     */
    saveAll(player, customPrefix) {
        if (!player || !player.nbt || !player.nbt.ForgeCaps) return
        let caps = player.nbt.ForgeCaps
        caps.allKeys.forEach(capId => {
            this.saveCap(player, capId, customPrefix)
        })
    },

    /**
     * Restores all capabilities stored under the prefix for this player.
     * @param {Internal.ServerPlayer} player
     */
    loadAll(player, customPrefix) {
        if (!player) return
        let overworld = player.server.getLevel('minecraft:overworld')
        let prefix = customPrefix || this.DEFAULT_PREFIX
        let playerPrefix = `${prefix}${player.uuid}_`

        overworld.persistentData.allKeys.forEach(key => {
            if (key.startsWith(playerPrefix)) {
                let capId = key.substring(playerPrefix.length)
                this.loadCap(player, capId, customPrefix)
            }
        })
    }
}

// Global helper wrappers
function savePlayerCap(player, capId, prefix) {
    return global.CapPersistence.saveCap(player, capId, prefix)
}

function loadPlayerCap(player, capId, prefix) {
    return global.CapPersistence.loadCap(player, capId, prefix)
}