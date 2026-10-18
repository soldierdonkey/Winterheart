// Places structure templates once per world at fixed spots, with a fixed rotation.
// Worldgen structures (jigsaw / structurespawn) always get a random rotation in 1.20.1, so these are
// pasted with /place template instead. Templates live in kubejs/data/<namespace>/structures/.
//
// Where each one goes comes from fixed_structures.json next to this script, written by the World Painter
// (tools/worldpainter, Struct tab, "Place in game" types; Save all). Positions are absolute block X/Z of the
// footprint centre. After editing it by hand, /kubejs reload server_scripts picks it up.

const FIXED_STRUCTURES_FILE = 'kubejs/server_scripts/worldgen/fixed_structures.json'

// How each template sits in the world. Templates not listed use DEFAULT_PROFILE.
//   anchor 'surface': template layer `groundLayers` goes at the ground surface; the volume above is cleared
//                     and dirt is filled `foundationDepth` deep under it.
//   anchor 'sea':     template layer `waterline` goes at the top water layer (sea level - 1). Nothing is
//                     cleared: the template leaves out air and water, so the ocean/air stays and its blocks
//                     overwrite everything else. Ice in the keel (bottom layers) is extended down to the
//                     sea floor when the floor is at most `groundDepth` blocks below it.
const DEFAULT_PROFILE = { anchor: 'surface', groundLayers: 0, foundationDepth: 4 }
const FIXED_STRUCTURE_PROFILES = {
    // the bottom two layers are its dirt/grass ground
    'winterheart:watchtower': { anchor: 'surface', groundLayers: 2, foundationDepth: 6 },
    // built by tools/schem_to_structure.py --iceberg, which prints the waterline
    'winterheart:shipwreck': { anchor: 'sea', waterline: 14, groundDepth: 10, groundBlock: 'minecraft:packed_ice' }
}

// Worlds made before positions came from the World Painter stored one flag per template.
const LEGACY_PLACED = { 'winterheart:watchtower': [0, 400] }

const HeightmapTypes = Java.loadClass('net.minecraft.world.level.levelgen.Heightmap$Types')
const BlockPosClass = Java.loadClass('net.minecraft.core.BlockPos')
const ResourceLocationClass = Java.loadClass('net.minecraft.resources.ResourceLocation')

// Block flags for writing fixups: send to clients (2) without updating neighbour shapes (16), so the game
// doesn't recompute connected blocks again.
const FIXUP_FLAGS = 2 | 16

const ROTATIONS = ['none', 'clockwise_90', '180', 'counterclockwise_90']

function loadFixedStructures() {
    const json = JsonIO.read(FIXED_STRUCTURES_FILE)
    if (!json) {
        console.warn(`[FixedStructures] ${FIXED_STRUCTURES_FILE} not found; nothing to place`)
        return []
    }
    const list = []
    json.get('structures').forEach(e => {
        const rot = e.get('rotation')
        const s = {
            template: String(e.get('template')),
            x: Number(e.get('x')),
            z: Number(e.get('z')),
            rotation: rot == null ? 'none' : String(rot)
        }
        if (ROTATIONS.indexOf(s.rotation) < 0) {
            console.error(`[FixedStructures] ${s.template}: unknown rotation ${s.rotation}`)
            return
        }
        s.profile = {}
        const own = FIXED_STRUCTURE_PROFILES[s.template] || {}
        Object.keys(DEFAULT_PROFILE).forEach(k => { s.profile[k] = DEFAULT_PROFILE[k] })
        Object.keys(own).forEach(k => { s.profile[k] = own[k] })
        list.push(s)
    })
    return list
}

const FIXED_STRUCTURES = loadFixedStructures()

// Placements that failed this session (e.g. missing template); not retried until restart.
const fixedStructureFailed = {}

function fixedStructureKey(s) {
    return `fixed_structure_placed:${s.template}@${s.x},${s.z}`
}

function fixedStructurePlaced(level, s) {
    const data = level.persistentData
    if (data.getBoolean(fixedStructureKey(s))) return true
    const legacy = LEGACY_PLACED[s.template]
    if (legacy && legacy[0] === s.x && legacy[1] === s.z && data.getBoolean('fixed_structure_placed:' + s.template)) {
        data.putBoolean(fixedStructureKey(s), true)
        return true
    }
    return false
}

// Template size [x, y, z] from the loaded template, or null if it doesn't exist.
function fixedStructureSize(server, s) {
    if (s.size) return s.size
    const t = server.getStructureManager().get(new ResourceLocationClass(s.template))
    if (!t.isPresent()) return null
    const v = t.get().getSize()
    s.size = [v.getX(), v.getY(), v.getZ()]
    return s.size
}

// Rotated footprint (x/z swap on quarter turns) and the world box it covers, centred on the target.
function fixedStructureBox(s, size) {
    const quarter = s.rotation === 'clockwise_90' || s.rotation === 'counterclockwise_90'
    const w = quarter ? size[2] : size[0]
    const d = quarter ? size[0] : size[2]
    const x1 = s.x - Math.floor(w / 2)
    const z1 = s.z - Math.floor(d / 2)
    return { x1: x1, z1: z1, x2: x1 + w - 1, z2: z1 + d - 1 }
}

// /place template puts the template's min corner at the given pos for rotation none; for other rotations
// the corner moves, so shift the pos so the rotated template still covers the box.
function fixedStructureOrigin(box, s) {
    switch (s.rotation) {
        case 'clockwise_90': return { x: box.x2, z: box.z1 }
        case '180': return { x: box.x2, z: box.z2 }
        case 'counterclockwise_90': return { x: box.x1, z: box.z2 }
        default: return { x: box.x1, z: box.z1 }
    }
}

// Surface Y (first free block above the ground, ignoring leaves): median of the centre and corners,
// so a single tree trunk or hole doesn't decide the height.
function fixedStructureSurface(level, box) {
    const cx = Math.floor((box.x1 + box.x2) / 2)
    const cz = Math.floor((box.z1 + box.z2) / 2)
    const points = [[cx, cz], [box.x1, box.z1], [box.x2, box.z1], [box.x1, box.z2], [box.x2, box.z2]]
    const ys = points.map(p => level.getHeight(HeightmapTypes.MOTION_BLOCKING_NO_LEAVES, p[0], p[1]))
    ys.sort((a, b) => a - b)
    return ys[2]
}

// Connected blocks (e.g. Macaw's bridge corners) get recomputed from their neighbours by /place template.
// tools/structure_fixups.py writes their saved states to <template>.fixups.json; this writes them back.
function applyFixedStructureFixups(level, s, origin) {
    const id = s.template.split(':')
    const file = `kubejs/data/${id[0]}/structures/${id[1]}.fixups.json`
    const json = JsonIO.read(file)
    if (!json) return 0
    if (s.rotation !== 'none') {
        console.warn(`[FixedStructures] ${file} skipped: fixups only support rotation none`)
        return 0
    }
    let count = 0
    json.get('blocks').forEach(b => {
        const pos = b.get('pos')
        const at = new BlockPosClass(origin.x + Number(pos.get(0)), origin.y + Number(pos.get(1)), origin.z + Number(pos.get(2)))
        level.setBlock(at, Utils.parseBlockState(String(b.get('state'))), FIXUP_FLAGS)
        count++
    })
    return count
}

function isLoose(id) {
    return id === 'minecraft:water' || id === 'minecraft:air' || id === 'minecraft:cave_air'
}

// Sea anchor: under the keel (ice in the template's bottom GROUND_KEEL_LAYERS layers), fill water down to the
// sea floor if the floor is close, so a berg that nearly touches bottom sits on it. Only the keel, so the
// rest of the underwater berg keeps its rounded shape instead of growing a straight-sided plinth.
const GROUND_KEEL_LAYERS = 4
function groundFixedStructure(level, box, y0, p) {
    let filled = 0
    for (let x = box.x1; x <= box.x2; x++) {
        for (let z = box.z1; z <= box.z2; z++) {
            let bottom = null
            for (let y = y0; y < y0 + GROUND_KEEL_LAYERS; y++) {
                const id = level.getBlock(x, y, z).id
                if (id.endsWith('ice') && id !== 'minecraft:frosted_ice') { bottom = y; break }
                if (!isLoose(id)) break  // sea floor already inside the template volume
            }
            if (bottom === null) continue
            let floor = null
            for (let y = bottom - 1; y >= bottom - p.groundDepth - 1; y--) {
                if (!isLoose(level.getBlock(x, y, z).id)) { floor = y; break }
            }
            if (floor === null) continue
            for (let y = floor + 1; y < bottom; y++) {
                level.getBlock(x, y, z).set(p.groundBlock)
                filled++
            }
        }
    }
    return filled
}

function fixedStructureLoaded(level, box) {
    return [[box.x1, box.z1], [box.x2, box.z1], [box.x1, box.z2], [box.x2, box.z2]]
        .every(p => level.isLoaded(new BlockPosClass(p[0], 0, p[1])))
}

function placeFixedStructure(level, s, size, box) {
    const server = level.server
    const p = s.profile
    const run = cmd => server.runCommandSilent('execute in minecraft:overworld run ' + cmd)
    let y0
    if (p.anchor === 'sea') {
        y0 = level.getSeaLevel() - 1 - p.waterline
    } else {
        y0 = fixedStructureSurface(level, box) - p.groundLayers
        // clear trees/snow inside the volume above the template's ground, then fill gaps under it
        run(`fill ${box.x1} ${y0 + p.groundLayers} ${box.z1} ${box.x2} ${y0 + size[1] - 1} ${box.z2} minecraft:air`)
        run(`fill ${box.x1} ${y0 - p.foundationDepth} ${box.z1} ${box.x2} ${y0 - 1} ${box.z2} minecraft:dirt replace #minecraft:replaceable`)
    }
    const o = fixedStructureOrigin(box, s)
    const ok = run(`place template ${s.template} ${o.x} ${y0} ${o.z} ${s.rotation} none`)
    if (!ok) {
        fixedStructureFailed[fixedStructureKey(s)] = true
        console.error(`[FixedStructures] /place template ${s.template} failed at ${o.x} ${y0} ${o.z}`)
        return
    }
    const fixed = applyFixedStructureFixups(level, s, { x: o.x, y: y0, z: o.z })
    const grounded = p.anchor === 'sea' && p.groundDepth > 0 ? groundFixedStructure(level, box, y0, p) : 0
    run(`forceload remove ${box.x1} ${box.z1} ${box.x2} ${box.z2}`)
    level.persistentData.putBoolean(fixedStructureKey(s), true)
    console.log(`[FixedStructures] Placed ${s.template} at ${box.x1} ${y0} ${box.z1} .. ${box.x2} ${y0 + size[1] - 1} ${box.z2} ` +
        `(rotation ${s.rotation}, ${fixed} fixups, ${grounded} grounding blocks)`)
}

// Force-load each pending site so it is placed right away, before a player can see the terrain without it.
ServerEvents.loaded(event => {
    const level = event.server.getLevel('minecraft:overworld')
    FIXED_STRUCTURES.forEach(s => {
        if (fixedStructurePlaced(level, s)) return
        const size = fixedStructureSize(event.server, s)
        if (!size) {
            fixedStructureFailed[fixedStructureKey(s)] = true
            console.error(`[FixedStructures] template ${s.template} not found`)
            return
        }
        const box = fixedStructureBox(s, size)
        event.server.runCommandSilent(`execute in minecraft:overworld run forceload add ${box.x1} ${box.z1} ${box.x2} ${box.z2}`)
    })
})

LevelEvents.tick(event => {
    const level = event.level
    if (level.isClientSide() || level.dimension.toString() !== 'minecraft:overworld') return
    if (level.getTime() % 20 !== 0) return
    FIXED_STRUCTURES.forEach(s => {
        if (fixedStructureFailed[fixedStructureKey(s)] || fixedStructurePlaced(level, s)) return
        const size = fixedStructureSize(level.server, s)
        if (!size) return
        const box = fixedStructureBox(s, size)
        if (fixedStructureLoaded(level, box)) placeFixedStructure(level, s, size, box)
    })
})
