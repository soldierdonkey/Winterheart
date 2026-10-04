// Places structure templates once per world at fixed spots, with a fixed rotation.
// Worldgen structures (jigsaw / structurespawn) always get a random rotation in 1.20.1, so these are
// pasted with /place template instead. Templates live in kubejs/data/<namespace>/structures/.

const FIXED_STRUCTURES = [
    {
        template: 'winterheart:watchtower',
        // Centre of the footprint, in blocks from world spawn (+X east, +Z south).
        offsetX: 0,
        offsetZ: 400,
        // Template size [x, y, z]; used to centre it and clear the space it occupies.
        size: [21, 46, 21],
        // none | clockwise_90 | 180 | counterclockwise_90 (relative to how it was built)
        rotation: 'none',
        // Template layers below the surface: the watchtower's bottom two layers are its dirt/grass ground.
        groundLayers: 2,
        // Dirt is filled this deep under the footprint so it never floats on a slope.
        foundationDepth: 6
    }
]

const HeightmapTypes = Java.loadClass('net.minecraft.world.level.levelgen.Heightmap$Types')
const BlockPosClass = Java.loadClass('net.minecraft.core.BlockPos')

// Block flags for writing fixups: send to clients (2) without updating neighbour shapes (16), so the game
// doesn't recompute connected blocks again.
const FIXUP_FLAGS = 2 | 16

// Templates that failed to place this session (e.g. missing file); not retried until restart.
const fixedStructureFailed = {}

function fixedStructureKey(s) {
    return 'fixed_structure_placed:' + s.template
}

// Rotated footprint (x/z swap on quarter turns) and the world box it covers, centred on the target.
function fixedStructureBox(level, s) {
    const spawn = level.getSharedSpawnPos()
    const quarter = s.rotation === 'clockwise_90' || s.rotation === 'counterclockwise_90'
    const w = quarter ? s.size[2] : s.size[0]
    const d = quarter ? s.size[0] : s.size[2]
    const x1 = spawn.x + s.offsetX - Math.floor(w / 2)
    const z1 = spawn.z + s.offsetZ - Math.floor(d / 2)
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

function fixedStructureLoaded(level, box) {
    return [[box.x1, box.z1], [box.x2, box.z1], [box.x1, box.z2], [box.x2, box.z2]]
        .every(p => level.isLoaded(new BlockPosClass(p[0], 0, p[1])))
}

function placeFixedStructure(level, s) {
    const server = level.server
    const box = fixedStructureBox(level, s)
    const y0 = fixedStructureSurface(level, box) - s.groundLayers
    const top = y0 + s.size[1] - 1
    const run = cmd => server.runCommandSilent('execute in minecraft:overworld run ' + cmd)

    // clear trees/snow inside the volume above the template's ground, then fill gaps under it
    run(`fill ${box.x1} ${y0 + s.groundLayers} ${box.z1} ${box.x2} ${top} ${box.z2} minecraft:air`)
    run(`fill ${box.x1} ${y0 - s.foundationDepth} ${box.z1} ${box.x2} ${y0 - 1} ${box.z2} minecraft:dirt replace #minecraft:replaceable`)
    const o = fixedStructureOrigin(box, s)
    const ok = run(`place template ${s.template} ${o.x} ${y0} ${o.z} ${s.rotation} none`)
    if (!ok) {
        fixedStructureFailed[s.template] = true
        console.error(`[FixedStructures] /place template ${s.template} failed at ${o.x} ${y0} ${o.z}`)
        return
    }
    const fixed = applyFixedStructureFixups(level, s, { x: o.x, y: y0, z: o.z })
    run(`forceload remove ${box.x1} ${box.z1} ${box.x2} ${box.z2}`)
    level.persistentData.putBoolean(fixedStructureKey(s), true)
    console.log(`[FixedStructures] Placed ${s.template} at ${box.x1} ${y0} ${box.z1} .. ${box.x2} ${top} ${box.z2} (rotation ${s.rotation}, ${fixed} fixups)`)
}

// Force-load each pending site so it is placed right away, before a player can see the terrain without it.
ServerEvents.loaded(event => {
    const level = event.server.getLevel('minecraft:overworld')
    FIXED_STRUCTURES.forEach(s => {
        if (level.persistentData.getBoolean(fixedStructureKey(s))) return
        const box = fixedStructureBox(level, s)
        event.server.runCommandSilent(`execute in minecraft:overworld run forceload add ${box.x1} ${box.z1} ${box.x2} ${box.z2}`)
    })
})

LevelEvents.tick(event => {
    const level = event.level
    if (level.isClientSide() || level.dimension.toString() !== 'minecraft:overworld') return
    if (level.getTime() % 20 !== 0) return
    FIXED_STRUCTURES.forEach(s => {
        if (fixedStructureFailed[s.template] || level.persistentData.getBoolean(fixedStructureKey(s))) return
        const box = fixedStructureBox(level, s)
        if (fixedStructureLoaded(level, box)) placeFixedStructure(level, s)
    })
})
