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

function rollrandom(arr) {
  // Return undefined or handle empty arrays
  if (!arr || arr.length === 0) return undefined; 
  
  // Calculate a random index based on the array length
  const randomIndex = Math.floor(Math.random() * arr.length);
  
  // Return the item at that index
  return arr[randomIndex];
}

/**
 * Executes a callback if a random chance is met.
 * @param {number} percentage - The percent chance of success (0 to 100).
 * @param {Function} onSuccess - The function to execute if successful.
 * @param {Function} [onFailure] - Optional function to execute if unsuccessful (defaults to an empty function).
 * @returns {*} The return value of the executed function.
 */
function randomChance(percentage, onSuccess, onFailure) {
  const roll = Math.random() * 100;
  
  if (roll < percentage) {
    return onSuccess;
  }
  
  return onFailure != undefined ? onFailure : () => {};
}

/**
 * Runs an anonymous function after a random delay.
 * @param {Internal.ServerJS} server - The Minecraft server instance.
 * @param {number} min - Minimum delay in ticks.
 * @param {number} max - Maximum delay in ticks.
 * @param {function} callback - The anonymous function to run.
 */
function randomDelay(server, min, max, callback) {
    // Generate a random number of ticks between min and max (inclusive)
    let delay = Math.floor(Math.random() * (max - min + 1)) + min;
    
    server.scheduleInTicks(delay, callback);
}

/**
 * Displays a highly visible, customizable title message to a specific player.
 * 
 * @param {Internal.MinecraftServer} server - The current Minecraft server instance.
 * @param {Internal.ServerPlayer} player - The specific player who will see the title.
 * @param {string} msg - The text message content to display.
 * @param {boolean} bold - Whether the text should be bold.
 * @param {boolean} italic - Whether the text should be italicized.
 */
function showCustomTitle(server, player, msg, bold, italic) {
    // Safety check to ensure a valid player was passed
    if (!player) return;

    // Get the player's username to target them safely in commands
    let target = player.username;

    // Set display times for the specific player (10 ticks fade-in, 60 stay, 20 fade-out)
    server.runCommandSilent(`title ${target} times 10 60 20`);
    
    // Construct the customized JSON text component
    let titleJson = JSON.stringify({
        text: msg,
        color: "dark_red",
        bold: bold === true,
        italic: italic === true
    });
    
    // Send the formatted title to the specific player
    server.runCommandSilent(`title ${target} actionbar ${titleJson}`);
}