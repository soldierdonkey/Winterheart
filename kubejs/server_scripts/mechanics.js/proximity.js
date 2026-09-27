// Server script: kubejs/server_scripts/block_sweep.js

console.log('====================================================');
console.log('[ProximityDebug] >>> block_sweep.js LOADED SUCCESSFULLY! <<<');
console.log('====================================================');

const PROXIMITY_DEBUG = {
    enabled: false,        // Master switch
    toChat: false,         // Print logs directly in player chat
    toConsole: false,      // Print logs to server console / kubejs/logs/server.log
    verboseHeartbeat: false // Log every 5-second pulse even if no blocks are found
};

const RULES = [
    {
        id: 'gore',
        overtaken_by: 'rotten_gore', // Suppressed if rotten_gore is present
        tag: '#winterheart:gore',
        level: true,
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                showCustomTitle(level.server, player, rollrandom([
                    `§2Ew.`,
                    `§6The sharp smell of copper and iron fills the air...`,
                    `§cYou see a swarm of flies frenzied by their next meal.`,
                    `§9Biological matter identified.`,
                    `§4The ground is stained a deep, unsettling red.`,
                    `§2A grim reminder of the wild's brutality.`,
                    `§2Thus is the cycle of life.`,
                    `§eYou remember, this will be you one day.`,
                    `§cYou still smell traces of violence.`,
                    `§2You are disgusted.`,
                    `§0You must §k########§r§0 it.`
                ]), true, true);
            });
        },
        onRepeat: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(5, _ => {set_sanity(player, level.server, get_sanity(player)-1)})()
                randomChance(2, _ => {player.potionEffects.add('minecraft:hunger', 50, 0)})()
                randomChance(2, _ => {player.potionEffects.add('minecraft:nausea', 15, 0)})()
            })
        }
    },
    {
        id: 'rotten_gore',
        tag: '#winterheart:rotten_gore',
        level: true,
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                showCustomTitle(level.server, player, rollrandom([
                    `§2You smell a disgusting odor...`,
                    `§2Something's rotting...`,
                    `§4The smell of death floats up to your nose.`,
                    `§3Your mind can't get over the sight.`,
                    `§4You're choking on the smell of death.`,
                    `§4Something died here.`
                ]), true, true);
            })
        },
        onRepeat: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(25, _ => {set_sanity(player, level.server, get_sanity(player)-1)})();
                randomChance(5, _ => {player.potionEffects.add('legendarysurvivaloverhaul:headache', 50, 0)})();
                randomChance(4, _ => {player.potionEffects.add('minecraft:nausea', 20, 0)})();
            })
        }
    },
    {
        id: 'carcass',
        overtaken_by: ['rotten_gore', 'gore'],
        tag: '#winterheart:carcass',
        level: true,
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                showCustomTitle(level.server, player, rollrandom([
                    `§2It's only natural.`,
                    `§aDon't feel bad about it.`,
                    `§fDid that just move?`,
                    `§aThe grim harvest sits still in silence.`,
                    `§cThe faint, musky scent of leather and bone hangs low.`
                ]));
            })
        },
        onRepeat: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(5, _ => {set_sanity(player, level.server, get_sanity(player)-1)})();
                randomChance(5, _ => {player.potionEffects.add('minecraft:hunger', 100, 0)})();
                randomChance(2, _ => {
                    showCustomTitle(level.server, player, `§0You swear you just saw it move.`, true, true)
                })
            })
        }
    },
    {
        id: 'cooking',
        tag: '#winterheart:cooking',
        onRepeat: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(5, _ => {set_sanity(player, level.server, get_sanity(player)+1)})();
                randomChance(5, _ => {player.potionEffects.add('minecraft:saturation', 2, 0)})();
            })
        }
    },
    {
        id: 'furniture',
        tag: '#winterheart:furniture',
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(5, _ => {set_sanity(player, level.server, get_sanity(player)+1)})();
                randomChance(5, _ => {player.potionEffects.add('farmersdelight:comfort', 200, 0)})();
            })
        }
    },
    {
        id: 'warmth',
        tag: '#winterheart:warmth',
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(50, _ => {set_temperature(player, level.server, get_temperature(player, level.server)+2)})();
                randomChance(5, _ => {set_sanity(player, level.server, get_sanity(player)+1)})();
            })
        }
    },
    {
        id: 'foliage',
        tag: '#winterheart:folieage',
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(7.5, _ => {set_sanity(player, level.server, get_temperature(player, level.server)+2)})();
            })
        }
    },
    {
        id: 'diamond_ore',
        tag: '#minecraft:diamond_ores',
        onOnce: (player, level) => {
            showCustomTitle(level.server, player, `§3💎§f You feel §o§3powerful§f.`);
        }
    },
    {
        id: 'lapis_ore',
        tag: '#minecraft:lapis_ores',
        onOnce: (player, level) => {
            showCustomTitle(level.server, player, `§1💎§f You feel at §o§1peace§f.`);
        }
    },
    {
        id: 'redstone_ore',
        tag: '#minecraft:redstone_ores',
        onOnce: (player, level) => {
            showCustomTitle(level.server, player, `§4💎§f You feel §o§4energized§f.`);
        }
    },
    {
        id: 'deepslate',
        blocks: ['minecraft:deepslate'],
        level: true,
        onOnce: (player, level) => {
            randomDelay(level.server, 0, 500, () => {
                showCustomTitle(level.server, player, `§0Something §l§oevil§r§0 emanates from these rocks...`);
            })
        },
        onRepeat: (player, level) => {
            randomDelay(level.server, 0, 50, () => {
                randomChance(50, _ => {set_sanity(player, level.server, get_sanity(player)-1)})();
                randomChance(5, _ => {player.potionEffects.add('minecraft:blindness', rollrandom([10, 40, 80, 100, 600]), 0)})();
                randomChance(4, _ => {
                    showCustomTitle(level.server, player, `§0What is that?`, true, true)
                })
                randomChance(2, _ => {level.server.runCommandSilent(`execute at ${player.username} run summon whos_there:white_eye`)})();
            })
        }
    }
];

const RADIUS = 5;
const RADIUS_SQ = RADIUS * RADIUS;
const TOTAL_RULES = RULES.length;

// NBT Keys inside player.persistentData
const DATA_KEY_LAST_SWEEP = 'sweep_last_blocks';
const DATA_KEY_TRIGGERED = 'sweep_triggered_once';

/**
 * Safely extracts a standard JavaScript Array from player.persistentData NBT.
 */
function getPersistentArray(player, key) {
    let list = player.persistentData[key];
    if (!list) return [];

    let result = [];
    for (let i = 0; i < list.length; i++) {
        let item = list[i];
        let str = (item && typeof item.getAsString === 'function') ? item.getAsString() : String(item);

        if (str.startsWith('"') && str.endsWith('"')) {
            str = str.slice(1, -1);
        }
        result.push(str);
    }
    return result;
}

/**
 * Writes a JavaScript Array into player.persistentData.
 */
function setPersistentArray(player, key, array) {
    player.persistentData[key] = array;
}

/**
 * Debug logging helper honoring PROXIMITY_DEBUG settings.
 */
function logProximityDebug(player, message) {
    if (!PROXIMITY_DEBUG.enabled) return;

    const cleanMessage = `[ProximityDebug] ${message}`;
    if (PROXIMITY_DEBUG.toConsole) {
        console.log(cleanMessage);
    }
    if (PROXIMITY_DEBUG.toChat && player) {
        try {
            player.tell(`§8[§6ProximityDebug§8] §7${message}`);
        } catch (e) {}
    }
}

/**
 * Checks whether a block satisfies a rule's blocks array or tag.
 */
function blockMatchesRule(block, rule) {
    const blockId = String(block.id);

    // 1. Tag check
    if (rule.tag) {
        let cleanTag = rule.tag.replace(/^#/, '');
        try {
            if (block.hasTag(cleanTag)) return true;
        } catch (err) {
            console.error(`[ProximityDebug] Error evaluating tag '${cleanTag}': ${err}`);
        }
    }

    // 2. Block array check
    if (rule.blocks && rule.blocks.length > 0) {
        for (let target of rule.blocks) {
            if (target.startsWith('#')) {
                let cleanTag = target.substring(1);
                try {
                    if (block.hasTag(cleanTag)) return true;
                } catch (err) {}
            } else if (blockId === target) {
                return true;
            }
        }
    }

    return false;
}

/**
 * Determines whether a rule is overtaken by another higher-priority rule
 * detected in the current sweep.
 */
function isRuleOvertaken(rule, currentSweep) {
    if (!rule.overtaken_by) return false;
    let overriders = Array.isArray(rule.overtaken_by) ? rule.overtaken_by : [rule.overtaken_by];
    return overriders.some(overriderId => currentSweep.includes(overriderId));
}

PlayerEvents.tick(event => {
    const player = event.player;
    const level = player.level;

    // Run every 100 ticks (5 seconds)
    const ticks = player.tickCount ?? (event.server ? event.server.tickCount : 0);
    if (ticks % 100 !== 0) return;

    const px = Math.floor(player.x);
    const py = Math.floor(player.y);
    const pz = Math.floor(player.z);

    if (PROXIMITY_DEBUG.verboseHeartbeat) {
        logProximityDebug(player, `Heartbeat (tick ${ticks}): Scanning radius ${RADIUS} at [${px}, ${py}, ${pz}]...`);
    }

    const currentSweep = [];

    scanLoop:
    for (let dx = -RADIUS; dx <= RADIUS; dx++) {
        let dxSq = dx * dx;
        for (let dy = -RADIUS; dy <= RADIUS; dy++) {
            let dxySq = dxSq + dy * dy;
            if (dxySq > RADIUS_SQ) continue;

            for (let dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dxySq + dz * dz > RADIUS_SQ) continue;

                let block = level.getBlock(px + dx, py + dy, pz + dz);
                let blockId = String(block.id);

                if (blockId === 'minecraft:air' || blockId === 'minecraft:cave_air' || blockId === 'minecraft:void_air') {
                    continue;
                }

                for (let rule of RULES) {
                    if (!currentSweep.includes(rule.id) && blockMatchesRule(block, rule)) {
                        if (rule.level && dy < -1) {
                            continue;
                        }
                        currentSweep.push(rule.id);
                        logProximityDebug(player, `Hit detected: Block §b${blockId}§7 matches rule §e${rule.id}§7 at [${px + dx}, ${py + dy}, ${pz + dz}]`);

                        if (currentSweep.length >= TOTAL_RULES) {
                            break scanLoop;
                        }
                    }
                }
            }
        }
    }

    // Retrieve persistent arrays from player NBT
    let lastSweep = getPersistentArray(player, DATA_KEY_LAST_SWEEP);
    let triggeredOnce = getPersistentArray(player, DATA_KEY_TRIGGERED);

    // Evaluate matches
    for (let rule of RULES) {
        let ruleId = rule.id;

        if (currentSweep.includes(ruleId)) {
            // Check if suppressed by a more extreme / overtaking rule
            if (isRuleOvertaken(rule, currentSweep)) {
                logProximityDebug(player, `Rule §e${ruleId}§7 is overtaken by an active rule. Trigger suppressed.`);
                continue;
            }

            if (lastSweep.includes(ruleId)) {
                // Confirmed in both consecutive sweeps (10s threshold reached)
                logProximityDebug(player, `Rule §e${ruleId}§7 reached 10s threshold!`);

                // 1. One-time trigger
                if (typeof rule.onOnce === 'function') {
                    if (!triggeredOnce.includes(ruleId)) {
                        logProximityDebug(player, `Executing §bonOnce§7 for §e${ruleId}§7.`);
                        rule.onOnce(player, level);
                        triggeredOnce.push(ruleId);
                    } else {
                        logProximityDebug(player, `Skipping §bonOnce§7 for §e${ruleId}§7 (already triggered).`);
                    }
                }

                // 2. Repetition trigger
                if (typeof rule.onRepeat === 'function') {
                    logProximityDebug(player, `Executing §donRepeat§7 for §e${ruleId}§7.`);
                    rule.onRepeat(player, level);
                }
            } else {
                // First sweep detection (0s - 5s warmup)
                logProximityDebug(player, `Rule §e${ruleId}§7 detected in current sweep. Primed for next check (5s warmup).`);
            }
        } else {
            // Player left range: reset one-time trigger for this rule
            if (lastSweep.includes(ruleId) || triggeredOnce.includes(ruleId)) {
                logProximityDebug(player, `Player left range of §e${ruleId}§7. Resetting trigger state.`);
                triggeredOnce = triggeredOnce.filter(id => id !== ruleId);
            }
        }
    }

    // Save updated arrays back into persistentData NBT
    setPersistentArray(player, DATA_KEY_LAST_SWEEP, currentSweep);
    setPersistentArray(player, DATA_KEY_TRIGGERED, triggeredOnce);
});