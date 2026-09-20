// Priority: 850

const TWO_PI = 6.283185307179586
const DEG_TO_RAD = 0.017453292519943295

// ==========================================
// 1. HITBOX & MOB DEFINITIONS
// ==========================================
const MOB_TEMPLATES = {
    'enderman':    { id: 'minecraft:enderman', name: 'Enderman',    width: 1, height: 3 },
    'spider':      { id: 'minecraft:spider',   name: 'Spider',      width: 2, height: 1 },
    'stray':       { id: 'minecraft:stray',    name: 'Stray',       width: 1, height: 2 },
    'skeleton':    { id: 'minecraft:skeleton', name: 'Skeleton',    width: 1, height: 2 },
    'zombie':      { id: 'minecraft:zombie',   name: 'Zombie',      width: 1, height: 2 },
    'creeper':     { id: 'minecraft:creeper',  name: 'Creeper',     width: 1, height: 2 },
    'baby_zombie': { id: 'minecraft:zombie',   name: 'Baby Zombie', width: 1, height: 1, isBaby: true }
}

const LIVESTOCK_TEMPLATES = {
    'cow':     { id: 'minecraft:cow',     name: 'Cow',     width: 1, height: 2 },
    'sheep':   { id: 'minecraft:sheep',   name: 'Sheep',   width: 1, height: 2 },
    'pig':     { id: 'minecraft:pig',     name: 'Pig',     width: 1, height: 2 },
    'chicken': { id: 'minecraft:chicken', name: 'Chicken', width: 1, height: 1 },
    'rabbit':  { id: 'minecraft:rabbit',  name: 'Rabbit',  width: 1, height: 1 }
}

const SPAWNER_CONFIG = {
    DEBUG_VERBOSE: true,
    SURFACE_Y: 60,
    DESPAWN_RADIUS: 80,
    
    HOSTILE_CAPS: {
        DAYS_1_10: 0,
        DAYS_11_20: 6,
        DAYS_21_30: 12,
        DAYS_31_40: 20,
        DAYS_41_50: 32
    },

    LIVESTOCK_QUOTAS: {
        DAYS_1_10: 80,
        DAYS_11_20: 20,
        DAYS_21_30: 10,
        DAYS_31_PLUS: 0
    },

    HOSTILE_POOL_SURFACE: [
        'enderman',
        'spider',
        'stray',
        'stray',
        'zombie',
        'skeleton',
        'baby_zombie'
    ],

    HOSTILE_POOL_CAVE: [
        'enderman',
        'spider',
        'stray',
        'zombie',
        'creeper',
        'baby_zombie'
    ],

    LIVESTOCK_POOL: [
        'cow',
        'sheep',
        'pig',
        'chicken',
        'rabbit'
    ]
}

function logTrace(stage, player, message) {
    if (!SPAWNER_CONFIG.DEBUG_VERBOSE) return
    let target = player ? ` [${player.username}]` : ''
    console.log(`[Spawner:${stage}]${target} ${message}`)
}

function shuffleArray(arr) {
    let array = arr.slice()
    for (let i = array.length - 1; i > 0; i--) {
        let j = Math.floor(Math.random() * (i + 1))
        let temp = array[i]
        array[i] = array[j]
        array[j] = temp
    }
    return array
}

// ==========================================
// 2. POSITION & BLOCK RESOLUTION
// ==========================================
function toInt(val) {
    let num = Math.floor(Number(val))
    return (!isFinite(num) || isNaN(num)) ? null : num
}

function getBlock(level, x, y, z) {
    let ix = toInt(x)
    let iy = toInt(y)
    let iz = toInt(z)

    if (ix === null || iy === null || iz === null) return null
    if (iy < -64 || iy > 320) return null
    return level.getBlock(ix, iy, iz)
}

function getPlayerCoords(player) {
    let rawX = 0, rawY = 64, rawZ = 0, rawYaw = 0

    try {
        rawX = typeof player.getX === 'function' ? player.getX() : player.x
        rawY = typeof player.getY === 'function' ? player.getY() : player.y
        rawZ = typeof player.getZ === 'function' ? player.getZ() : player.z
        rawYaw = typeof player.getYRot === 'function' ? player.getYRot() : (player.yRot || player.yaw || 0)
    } catch (e) {}

    return {
        x: Math.floor(Number(rawX)) || 0,
        y: Math.floor(Number(rawY)) || 64,
        z: Math.floor(Number(rawZ)) || 0,
        yaw: Number(rawYaw) || 0
    }
}

function isPassable(block) {
    if (!block) return true
    let id = String(block.id)
    
    if (id.endsWith('air')) return true
    if (id === 'minecraft:snow' || 
        id === 'minecraft:grass' || 
        id === 'minecraft:tall_grass' || 
        id === 'minecraft:fern' || 
        id === 'minecraft:large_fern' || 
        id.endsWith('_flower') || 
        id.includes('sapling') || 
        id.includes('mushroom') || 
        id.includes('bush')) {
        return true
    }
    return false
}

function isSolidFloor(block) {
    if (!block) return false
    let id = String(block.id)
    
    if (id.endsWith('air')) return false
    if (isPassable(block)) return false
    if (id.includes('leaves')) return false
    if (id.includes('water') || id.includes('lava') || id.includes('powder_snow')) return false
    
    return true
}

// ==========================================
// 3. HITBOX & ADAPTIVE FIT VALIDATION
// ==========================================
function testMobFit(level, x, y, z, template) {
    let ix = toInt(x)
    let iy = toInt(y)
    let iz = toInt(z)

    if (ix === null || iy === null || iz === null) return { valid: false, reason: 'Invalid coordinate' }
    if (iy < -60 || iy + template.height > 318) return { valid: false, reason: `Out of bounds Y=${iy}` }

    let width = template.width || 1
    let height = template.height || 2

    // Footprint offsets (1x1 or 2x2)
    let offsets = (width === 2) ? [
        [0, 0], [1, 0], [0, 1], [1, 1]
    ] : [
        [0, 0]
    ]

    // 1. Floor integrity check
    for (let i = 0; i < offsets.length; i++) {
        let ox = offsets[i][0]
        let oz = offsets[i][1]
        let ground = getBlock(level, ix + ox, iy - 1, iz + oz)
        if (!ground) return { valid: false, reason: 'Unloaded chunk floor' }
        if (!isSolidFloor(ground)) return { valid: false, reason: `Unstable floor at [${ox}, ${oz}]: ${ground.id}` }
    }

    // 2. Headroom and spatial clearance check
    for (let i = 0; i < offsets.length; i++) {
        let ox = offsets[i][0]
        let oz = offsets[i][1]
        for (let dy = 0; dy < height; dy++) {
            let checkBlock = getBlock(level, ix + ox, iy + dy, iz + oz)
            if (!checkBlock) return { valid: false, reason: 'Unloaded chunk space' }
            if (!isPassable(checkBlock)) {
                return { valid: false, reason: `Obstructed at [${ox}, +${dy}, ${oz}]: ${checkBlock.id}` }
            }
        }
    }

    let centerOffset = (width === 2) ? 1.0 : 0.5
    return {
        valid: true,
        pos: { x: ix + centerOffset, y: iy, z: iz + centerOffset },
        reason: 'Clear'
    }
}

function getSurfaceY(level, x, z, playerY) {
    let ix = toInt(x)
    let iz = toInt(z)
    let py = toInt(playerY) || 64

    if (ix === null || iz === null) return -64

    let startY = Math.min(319, Math.max(py + 35, 100))
    let minY = Math.max(-60, SPAWNER_CONFIG.SURFACE_Y - 8)

    for (let y = startY; y >= minY; y--) {
        let block = getBlock(level, ix, y, iz)
        if (!block) continue

        if (isPassable(block) || block.id.includes('leaves')) continue

        if (isSolidFloor(block)) {
            return y + 1
        }

        if (block.id.includes('water') || block.id.includes('lava') || block.id.includes('powder_snow')) {
            return -64
        }
    }
    return -64
}

// ==========================================
// 4. CANDIDATE SELECTION WITH FALLBACK
// ==========================================
function selectFittingMobAt(level, targetX, checkY, targetZ, poolKeyList, templateDict) {
    // Shuffle candidates to randomize initial pick
    let candidates = shuffleArray(poolKeyList)

    for (let i = 0; i < candidates.length; i++) {
        let templateKey = candidates[i]
        let template = templateDict[templateKey]
        if (!template) continue

        let check = testMobFit(level, targetX, checkY, targetZ, template)
        if (check.valid) {
            return { pos: check.pos, template: template, reason: 'OK' }
        }
    }
    return null
}

function findCaveSpawnPos(level, pCoords, minR, maxR, pool) {
    let px = pCoords.x
    let py = pCoords.y
    let pz = pCoords.z
    let yaw = pCoords.yaw
    let lastReason = 'No footing found'

    for (let attempt = 1; attempt <= 8; attempt++) {
        let r = minR + Math.random() * (maxR - minR)
        let angleRad = (yaw + 180 + (Math.random() * 50 - 25)) * DEG_TO_RAD

        let targetX = toInt(px - Math.sin(angleRad) * r)
        let targetZ = toInt(pz + Math.cos(angleRad) * r)
        if (targetX === null || targetZ === null) continue

        let distSq = (targetX - px) * (targetX - px) + (targetZ - pz) * (targetZ - pz)
        if (distSq < minR * minR) continue

        for (let dy = -4; dy <= 4; dy++) {
            let checkY = py + dy
            let match = selectFittingMobAt(level, targetX, checkY, targetZ, pool, MOB_TEMPLATES)
            if (match) {
                return match
            }
        }
    }
    return { pos: null, template: null, reason: lastReason }
}

function findSurfaceSpawnPos(level, pCoords, minR, maxR, pool, templateDict) {
    let px = pCoords.x
    let py = pCoords.y
    let pz = pCoords.z
    let lastReason = 'No candidate found'

    for (let attempt = 1; attempt <= 12; attempt++) {
        let r = minR + Math.random() * (maxR - minR)
        let angle = Math.random() * TWO_PI

        let targetX = toInt(px + Math.cos(angle) * r)
        let targetZ = toInt(pz + Math.sin(angle) * r)
        if (targetX === null || targetZ === null) continue

        let distSq = (targetX - px) * (targetX - px) + (targetZ - pz) * (targetZ - pz)
        if (distSq < minR * minR) continue

        let topY = getSurfaceY(level, targetX, targetZ, py)
        if (topY < SPAWNER_CONFIG.SURFACE_Y - 5) {
            lastReason = `Surface Y=${topY} below threshold (${SPAWNER_CONFIG.SURFACE_Y - 5})`
            continue
        }

        let match = selectFittingMobAt(level, targetX, topY, targetZ, pool, templateDict)
        if (match) {
            return match
        }

        lastReason = 'No candidate mob fits terrain opening'
    }

    return { pos: null, template: null, reason: lastReason }
}

// ==========================================
// 5. SYNTHETIC HOSTILE CONTROLLER (1-SEC)
// ==========================================

LevelEvents.tick(event => {
    let level = event.level
    if (level.dimension.toString() !== 'minecraft:overworld' || level.isClientSide()) return

    if (global.currentTick % 20 !== 0) return

    let data = level.persistentData
    let day = data.getInt('custom_day') || 1
    let phase = data.getString('current_phase') || 'DAWN'

    let hostileCap = SPAWNER_CONFIG.HOSTILE_CAPS.DAYS_1_10
    if (day >= 41) hostileCap = SPAWNER_CONFIG.HOSTILE_CAPS.DAYS_41_50
    else if (day >= 31) hostileCap = SPAWNER_CONFIG.HOSTILE_CAPS.DAYS_31_40
    else if (day >= 21) hostileCap = SPAWNER_CONFIG.HOSTILE_CAPS.DAYS_21_30
    else if (day >= 11) hostileCap = SPAWNER_CONFIG.HOSTILE_CAPS.DAYS_11_20

    level.players.forEach(player => {
        let pCoords = getPlayerCoords(player)
        let isCave = pCoords.y < SPAWNER_CONFIG.SURFACE_Y
        let isNight = (phase === 'NIGHT' || phase === 'MIDNIGHT' || phase === 'ETERNAL_NIGHT' || phase === 'DUSK')

        let nearbySynthetic = level.getEntities().filter(e => {
            return e.isAlive() &&
                   e.tags.contains('synthetic_hostile') &&
                   e.distanceToEntity(player) <= SPAWNER_CONFIG.DESPAWN_RADIUS
        })

        // Reaper: Cull non-interacted hostiles
        if (nearbySynthetic.length >= hostileCap) {
            let nonInteracted = nearbySynthetic.filter(e => e.isAlive() && !e.tags.contains('interacted'))
            if (nonInteracted.length > 0) {
                nonInteracted.sort((a, b) => {
                    let distA = a.distanceToEntity(player)
                    let distB = b.distanceToEntity(player)
                    if (isNaN(distA) || isNaN(distB)) return 0
                    if (distB > distA) return 1
                    if (distB < distA) return -1
                    return 0
                })
                let culled = nonInteracted[0]
                culled.discard()
                logTrace('Hostile:Reaper', player, `Reaped distant '${culled.type}' at (${culled.x.toFixed(1)}, ${culled.y.toFixed(1)}, ${culled.z.toFixed(1)})`)
            }
            return
        }

        let canSpawn = (day >= 21) || isCave || (day >= 11 && isNight)
        if (!canSpawn) return

        let mobPool = isCave ? SPAWNER_CONFIG.HOSTILE_POOL_CAVE : SPAWNER_CONFIG.HOSTILE_POOL_SURFACE
        let result = isCave ? 
            findCaveSpawnPos(level, pCoords, 8, 16, mobPool) : 
            findSurfaceSpawnPos(level, pCoords, 24, 60, mobPool, MOB_TEMPLATES)
        
        if (!result.pos || !result.template) {
            logTrace('Hostile:Spatial', player, `Spawning failed: ${result.reason}`)
            return
        }

        let spawnPos = result.pos
        let template = result.template

        let entity = level.createEntity(template.id)
        if (!entity) return

        entity.setPos(spawnPos.x, spawnPos.y, spawnPos.z)
        entity.tags.add('synthetic_hostile')

        if (template.isBaby) {
            if (typeof entity.setBaby === 'function') {
                entity.setBaby(true)
            } else {
                entity.mergeNbt({ IsBaby: true })
            }
        }

        entity.spawn()

        let dist = Math.sqrt(Math.pow(spawnPos.x - pCoords.x, 2) + Math.pow(spawnPos.z - pCoords.z, 2)).toFixed(1)
        logTrace('Hostile:Success', player, `SUCCESS: Spawned '${template.name}' (dim: ${template.width}x${template.height}) at (${spawnPos.x.toFixed(1)}, ${spawnPos.y.toFixed(1)}, ${spawnPos.z.toFixed(1)}) [${dist}m away | Yaw: ${pCoords.yaw.toFixed(1)}°]`)
    })
})

// ==========================================
// 6. SYNTHETIC LIVESTOCK CONTROLLER (10-SEC)
// ==========================================
LevelEvents.tick(event => {
    let level = event.level
    if (level.dimension.toString() !== 'minecraft:overworld' || level.isClientSide()) return

    if (global.currentTick % 80 !== 0) return

    let data = level.persistentData
    let day = data.getInt('custom_day') || 1
    let phase = data.getString('current_phase') || 'DAWN'

    if (day > 30 || phase === 'MIDNIGHT' || phase === 'ETERNAL_NIGHT') return

    let livestockQuota = SPAWNER_CONFIG.LIVESTOCK_QUOTAS.DAYS_1_10
    if (day >= 21) livestockQuota = SPAWNER_CONFIG.LIVESTOCK_QUOTAS.DAYS_21_30
    else if (day >= 11) livestockQuota = SPAWNER_CONFIG.LIVESTOCK_QUOTAS.DAYS_11_20

    level.players.forEach(player => {
        let pCoords = getPlayerCoords(player)
        if (pCoords.y < SPAWNER_CONFIG.SURFACE_Y - 5) return

        let nearbyAnimals = level.getEntities().filter(e => {
            return e.isAlive() && e.isAnimal() && e.distanceToEntity(player) <= 60
        })

        if (nearbyAnimals.length >= livestockQuota) return

        let centerResult = findSurfaceSpawnPos(level, pCoords, 24, 60, SPAWNER_CONFIG.LIVESTOCK_POOL, LIVESTOCK_TEMPLATES)
        if (!centerResult.pos || !centerResult.template) {
            logTrace('Livestock:Spatial', player, `Flock anchor rejected: ${centerResult.reason}`)
            return
        }

        let centerPos = centerResult.pos
        let template = centerResult.template
        let groupSize = day >= 21 ? 2 : (2 + Math.floor(Math.random() * 3))
        let spawnedCount = 0

        for (let i = 0; i < groupSize; i++) {
            let offsetX = toInt(centerPos.x + (Math.random() * 4 - 2))
            let offsetZ = toInt(centerPos.z + (Math.random() * 4 - 2))
            if (offsetX === null || offsetZ === null) continue

            let groundY = getSurfaceY(level, offsetX, offsetZ, pCoords.y)
            if (groundY >= SPAWNER_CONFIG.SURFACE_Y - 5) {
                let check = testMobFit(level, offsetX, groundY, offsetZ, template)
                if (check.valid) {
                    let animal = level.createEntity(template.id)
                    if (animal) {
                        animal.setPos(check.pos.x, check.pos.y, check.pos.z)
                        animal.tags.add('synthetic_livestock')
                        animal.spawn()
                        spawnedCount++
                    }
                }
            }
        }

        logTrace('Livestock:Success', player, `Flock spawned: ${spawnedCount}/${groupSize} '${template.name}' near (${centerPos.x.toFixed(1)}, ${centerPos.y.toFixed(1)}, ${centerPos.z.toFixed(1)})`)
    })
})

// ==========================================
// 7. INTERACTION & AUDIT LOGGER
// ==========================================
EntityEvents.spawned(event => {
    let entity = event.entity
    if (entity.level.isClientSide() || !entity.isLiving() || entity.isPlayer()) return
    if (entity.tags.contains('synthetic_hostile') || entity.tags.contains('synthetic_livestock')) return

    let pos = `(${entity.x.toFixed(1)}, ${entity.y.toFixed(1)}, ${entity.z.toFixed(1)})`
    let isBaby = (typeof entity.isBaby === 'function' && entity.isBaby()) ? ' [Baby/Breeding]' : ''
    console.log(`[External Mob Spawn] '${entity.type}' spawned at ${pos}${isBaby}`)
})

EntityEvents.hurt(event => {
    let entity = event.entity
    let source = event.source.entity

    if (entity && entity.tags && entity.tags.contains('synthetic_hostile')) {
        entity.tags.add('interacted')
    }
    if (source && source.tags && source.tags.contains('synthetic_hostile')) {
        source.tags.add('interacted')
    }
})