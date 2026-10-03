// priority: 600
//
// Guide system engine
// --------------------
// Guides are defined in guides.json (edited with the guide_editor app in the instance root) and loaded into
// global.GUIDES. The file is re-read every few seconds, so saving in the editor updates the running game.
// Which guides are unlocked is stored world-wide in the overworld's persistentData. A guide unlocks when one of its triggers fires for ANY player:
//   - sweeps: every SWEEP_INTERVAL_TICKS, looks at what each player stands on / looks at / carries
//   - events: hooks on breaking, placing, crafting, damage, kills, ...
//
// On unlock, every player gets a written book containing only that page, and the server is paused with
// /pause (Multiplayer Server Pause) until every player has closed the book. The close is reported by
// client_scripts/guide_book_watcher.js over KubeJS data packets.
//
// The book is opened by briefly swapping a written book into the player's hand, because the vanilla
// open-book packet only works on the item held in that hand. The original stack is put back in the same tick.
//
// Debug commands: guides_commands.js (/guides ...).

const GUIDE_CONFIG = {
    SWEEP_INTERVAL_TICKS: 10,       // 10 ticks = twice a second
    LOOK_DISTANCE: 16,              // max ray distance for looking_at_* sweeps
    PAUSE_ON_UNLOCK: true,
    POPUP_GAP_TICKS: 10,            // delay between queued popups
    BOOK_TITLE: 'Winterheart Guide',
    BOOK_AUTHOR: 'Winterheart',
    STORE_KEY: 'winterheart_guides_unlocked',
    CHANNEL_OPEN: 'winterheart_guide_open',       // server -> client: start watching for the book to close
    CHANNEL_CLOSED: 'winterheart_guide_closed'    // client -> server: the book was closed
}

const GUIDE_SWEEP_KINDS = ['standing_on', 'standing_in', 'looking_at_block', 'looking_at_entity', 'inventory', 'holding', 'time', 'custom']
const GUIDE_EVENT_KINDS = [
    'block_broken', 'block_placed', 'block_used', 'item_used', 'item_crafted', 'item_smelted',
    'item_picked_up', 'item_eaten', 'entity_killed', 'entity_hurt', 'player_hurt', 'player_died',
    'advancement', 'time_phase', 'proximity', 'custom'
]

const GuideCompoundTag = Java.loadClass('net.minecraft.nbt.CompoundTag')
const GuideInteractionHand = Java.loadClass('net.minecraft.world.InteractionHand')

// Survives /kjs reload so a reload can't forget an active popup or pause.
global.GuideRuntime = global.GuideRuntime || {
    viewers: {},        // uuid -> true, players who have the popup book open
    popupActive: false,
    queue: [],          // guide ids waiting to be shown
    ownsPause: false,   // true if WE issued /pause and must undo it
    trace: {},          // uuid -> true, debug output enabled
    lastTrace: {},      // uuid -> last logged sweep snapshot key, so the log only records changes
    warned: {}          // guide id -> true, broken-trigger errors already logged
}
const guideRT = global.GuideRuntime

let guideIndex = null    // trigger index of locked guides, see section 4

// ==========================================
// 1. DATA ACCESS + PERSISTENT STORE
// ==========================================

function guideList() {
    return Array.isArray(global.GUIDES) ? global.GUIDES : []
}

function guideBooks() {
    return Array.isArray(global.GUIDE_BOOKS) && global.GUIDE_BOOKS.length > 0 ? global.GUIDE_BOOKS : [{ id: 'guide', name: 'Guide Book' }]
}

// Lost pages: dummy items (kubejs:lost_page) tagged with `lostpage: "<id>"`, defined by the "pages" list in guides.json.
function guideLostPages() {
    return Array.isArray(global.GUIDE_LOST_PAGES) ? global.GUIDE_LOST_PAGES : []
}

function guideFindLostPage(id) {
    return guideLostPages().find(p => p.id === id) || null
}

function guideFindBook(id) {
    let books = guideBooks()
    for (let i = 0; i < books.length; i++) {
        if (books[i].id === id) return books[i]
    }
    return null
}

function guideFind(id) {
    let list = guideList()
    for (let i = 0; i < list.length; i++) {
        if (list[i].id === id) return list[i]
    }
    return null
}

function guideToArray(v) {
    if (v === undefined || v === null) return []
    return Array.isArray(v) ? v : [v]
}

function guideStore(server) {
    let data = server.getLevel('minecraft:overworld').persistentData
    if (!data.contains(GUIDE_CONFIG.STORE_KEY)) data.put(GUIDE_CONFIG.STORE_KEY, new GuideCompoundTag())
    return data.getCompound(GUIDE_CONFIG.STORE_KEY)
}

function guideIsUnlocked(server, id) {
    return guideStore(server).contains(id)
}

const GUIDE_COLOR_NAMES = ['black', 'dark_blue', 'dark_green', 'dark_aqua', 'dark_red', 'dark_purple', 'gold', 'gray',
    'dark_gray', 'blue', 'green', 'aqua', 'red', 'light_purple', 'yellow', 'white']

function guideValidColor(c) {
    return typeof c === 'string' && (GUIDE_COLOR_NAMES.indexOf(c) >= 0 || /^#[0-9a-fA-F]{6}$/.test(c))
}

// Returns problem strings; ones starting with 'warning: ' do not stop a load.
function guideValidate(list, books, pages) {
    list = list || guideList()
    books = books || guideBooks()
    pages = pages || guideLostPages()
    let problems = []
    let seenPages = {}
    pages.forEach((p, i) => {
        if (!p || typeof p.id !== 'string' || !/^[a-z0-9_]+$/.test(p.id)) {
            problems.push(`pages[${i}]: id must be a string of a-z, 0-9 and _`)
            return
        }
        if (seenPages[p.id]) problems.push(`lost page '${p.id}': duplicate id`)
        seenPages[p.id] = true
        if (typeof p.name !== 'string' || p.name.length === 0) problems.push(`lost page '${p.id}': missing name`)
    })
    let seen = {}
    let seenBooks = {}
    books.forEach((b, i) => {
        if (!b || typeof b.id !== 'string' || !/^[a-z0-9_]+$/.test(b.id)) {
            problems.push(`books[${i}]: id must be a string of a-z, 0-9 and _`)
            return
        }
        if (seenBooks[b.id]) problems.push(`book '${b.id}': duplicate id`)
        seenBooks[b.id] = true
        if (typeof b.name !== 'string' || b.name.length === 0) problems.push(`book '${b.id}': missing name`)
        if (b.titleColor !== undefined && !guideValidColor(b.titleColor)) problems.push(`book '${b.id}': titleColor must be a color name or #rrggbb`)
    })
    list.forEach((g, i) => {
        let where = `GUIDES[${i}]`
        if (!g || typeof g.id !== 'string' || !/^[a-z0-9_]+$/.test(g.id)) {
            problems.push(`${where}: id must be a string of a-z, 0-9 and _`)
            return
        }
        where = `guide '${g.id}'`
        if (seen[g.id]) problems.push(`${where}: duplicate id`)
        seen[g.id] = true
        if (typeof g.title !== 'string') problems.push(`${where}: missing title`)
        if (!seenBooks[g.book]) problems.push(`${where}: book '${g.book}' does not exist`)
        if (g.content === undefined) problems.push(`${where}: missing content`)
        let triggers = guideToArray(g.unlock)
        if (triggers.length === 0) problems.push(`warning: ${where}: no unlock trigger (only unlockable from code/commands)`)
        triggers.forEach(t => {
            if (t && t.sweep) {
                if (GUIDE_SWEEP_KINDS.indexOf(t.sweep) < 0) problems.push(`${where}: unknown sweep '${t.sweep}'`)
                if (t.sweep === 'custom' && typeof t.test !== 'function') problems.push(`${where}: custom sweep needs test`)
                if (t.sweep === 'inventory' && t.item === undefined) problems.push(`${where}: inventory sweep needs item`)
                if (t.sweep === 'holding' && t.item === undefined) problems.push(`${where}: holding sweep needs item`)
            } else if (t && t.event) {
                if (GUIDE_EVENT_KINDS.indexOf(t.event) < 0) problems.push(`${where}: unknown event '${t.event}'`)
            } else {
                problems.push(`${where}: trigger needs a 'sweep' or 'event' key`)
            }
            if (t && t.hand !== undefined && ['any', 'main', 'off'].indexOf(t.hand) < 0) problems.push(`${where}: hand must be any, main or off`)
        })
    })
    return problems
}

// ---- guides.json loading ----
// JSON can't hold regexes or functions, so: { "regex": "zomb" } becomes a RegExp, and "test" / "where" are the
// NAMES of functions you register yourself:  global.GuideFunctions.my_test = ctx => ...

const GUIDE_DATA_FILE = 'kubejs/server_scripts/questing/guides/guides.json'
const GUIDE_RELOAD_CHECK_TICKS = 40     // how often the file is checked for changes (2 seconds)

global.GuideFunctions = global.GuideFunctions || {}

function guideNormMatch(spec) {
    if (Array.isArray(spec)) return spec.map(guideNormMatch)
    if (spec && typeof spec === 'object' && typeof spec.regex === 'string') return new RegExp(spec.regex)
    return spec
}

function guideNormalize(raw) {
    let src = Array.isArray(raw) ? raw : (raw && Array.isArray(raw.guides) ? raw.guides : null)
    if (!src) return { guides: [], books: [], pages: [], problems: ['guides.json needs a "guides" array'] }
    let problems = []
    let books = raw && Array.isArray(raw.books) && raw.books.length > 0 ? raw.books.map(b => Object.assign({}, b)) : [{ id: 'guide', name: 'Guide Book' }]
    let guides = src.map(g => {
        let out = Object.assign({}, g)
        if (typeof out.book !== 'string' || out.book.length === 0) out.book = books[0].id
        out.unlock = guideToArray(g.unlock).map(t => {
            let n = Object.assign({}, t)
            try {
                ;['match', 'item', 'by', 'holding'].forEach(k => { if (k in n) n[k] = guideNormMatch(n[k]) })
            } catch (e) {
                problems.push(`guide '${g.id}': bad matcher: ${e}`)
            }
            ;['test', 'where'].forEach(k => {
                if (typeof n[k] !== 'string') return
                let name = n[k]
                if (typeof global.GuideFunctions[name] !== 'function') {
                    problems.push(`guide '${g.id}': no function '${name}' in global.GuideFunctions`)
                }
                n[k] = ctx => global.GuideFunctions[name](ctx)
            })
            return n
        })
        return out
    })
    let pages = raw && Array.isArray(raw.pages) ? raw.pages.map(p => Object.assign({}, p)) : []
    return { guides: guides, books: books, pages: pages, problems: problems }
}

// Tries a few ways to read the file, since the path coercion differs between KubeJS builds.
// Returns the raw text, or null (logging every failure) if none worked.
function guideReadDataFile() {
    let errors = []
    let attempts = [
        ['JsonIO.readString(path string)', () => JsonIO.readString(GUIDE_DATA_FILE)],
        ['JsonIO.readString(JsonIO.getPath)', () => JsonIO.readString(JsonIO.getPath(GUIDE_DATA_FILE))],
        ['java.nio Files.readString', () => {
            const Paths = Java.loadClass('java.nio.file.Paths')
            const Files = Java.loadClass('java.nio.file.Files')
            return Files.readString(Paths.get(GUIDE_DATA_FILE))
        }]
    ]
    for (let i = 0; i < attempts.length; i++) {
        try {
            let text = attempts[i][1]()
            if (text !== null && text !== undefined && String(text).length > 0) {
                guideRT.readFailed = false
                return String(text)
            }
            errors.push(`${attempts[i][0]}: returned empty`)
        } catch (e) {
            errors.push(`${attempts[i][0]}: ${e}`)
        }
    }
    if (guideRT.readFailed) return null     // already reported; don't repeat every check
    guideRT.readFailed = true
    console.error(`[Guides] could not read ${GUIDE_DATA_FILE}:\n  ${errors.join('\n  ')}`)
    return null
}

// Replaces global.GUIDES if the text is valid; otherwise keeps the old list and logs why.
function guideApplyData(text) {
    let parsed
    try {
        parsed = JSON.parse(text)
    } catch (e) {
        guideRT.dataProblems = [`guides.json is not valid JSON: ${e}`]
        console.error(`[Guides] ${guideRT.dataProblems[0]}; keeping the previous guides`)
        return false
    }
    let result = guideNormalize(parsed)
    let problems = result.problems.concat(guideValidate(result.guides, result.books, result.pages))
    guideRT.dataProblems = problems
    let errors = problems.filter(p => p.indexOf('warning: ') !== 0)
    problems.forEach(p => {
        if (p.indexOf('warning: ') === 0) console.warn(`[Guides] ${p}`)
        else console.error(`[Guides] ${p}`)
    })
    if (errors.length > 0) {
        console.error(`[Guides] guides.json has ${errors.length} error(s); keeping the previous guides`)
        return false
    }
    global.GUIDES = result.guides
    global.GUIDE_BOOKS = result.books
    global.GUIDE_LOST_PAGES = result.pages
    guideInvalidate()
    console.info(`[Guides] Loaded ${result.guides.length} guide(s) in ${result.books.length} book(s) from guides.json`)
    return true
}

// force = re-apply even if the file did not change. Returns true if the guides were (re)loaded.
function guideCheckDataFile(server, force) {
    let text = guideReadDataFile()
    if (text === null) {
        return false
    }
    if (!force && text === guideRT.dataText) return false
    let first = guideRT.dataText === undefined
    guideRT.dataText = text     // remembered even when rejected, so a broken file is reported once, not every check
    let ok = guideApplyData(text)
    if (server && !first) {
        let msg = ok ? Text.gray('[guides] guides.json reloaded') : Text.red('[guides] guides.json rejected, see the server log')
        server.getPlayerList().getPlayers().forEach(p => { if (p.hasPermissions(2)) p.tell(msg) })
    }
    return ok
}

guideCheckDataFile(null, true)

// ==========================================
// 2. MATCHING
// ==========================================

// spec: undefined/'*' (anything), 'ns:id', '#ns:tag', regex, function(id), or an array of those
function guideMatch(spec, id, hasTag, stack) {
    if (spec === undefined || spec === null || spec === '*') return true
    if (Array.isArray(spec)) return spec.some(s => guideMatch(s, id, hasTag, stack))
    // { page: 'id' } matches a kubejs:lost_page stack carrying that page id in its NBT
    if (typeof spec === 'object' && !(spec instanceof RegExp) && typeof spec.page === 'string') return guideIsLostPage(stack, spec.page)
    if (typeof spec === 'function') return !!spec(id)
    if (spec instanceof RegExp) return spec.test(id)
    let s = String(spec)
    if (s.charAt(0) === '#') {
        try { return hasTag ? !!hasTag(s.substring(1)) : false } catch (e) { return false }
    }
    return s === id
}

function guideIsLostPage(stack, pageId) {
    try {
        if (!stack || String(stack.id) !== 'kubejs:lost_page' || !stack.nbt) return false
        return String(stack.nbt.getString('lostpage')) === pageId
    } catch (e) {
        return false
    }
}

function guideEntityHasTag(entity, tag) {
    try {
        const TagKey = Java.loadClass('net.minecraft.tags.TagKey')
        const Registries = Java.loadClass('net.minecraft.core.registries.Registries')
        const ResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation')
        return !!entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(tag)))
    } catch (e) {
        return false
    }
}

function guideMatchBlock(spec, block) {
    return guideMatch(spec, String(block.id), t => block.hasTag(t))
}

function guideMatchItem(spec, stack) {
    return guideMatch(spec, String(stack.id), t => stack.hasTag(t), stack)
}

function guideMatchEntity(spec, entity) {
    return guideMatch(spec, String(entity.type), t => guideEntityHasTag(entity, t))
}

// ==========================================
// 3. SWEEPS
// ==========================================

function guideMakeSweepCtx(player) {
    let memo = {}
    function once(key, fn) {
        if (!(key in memo)) {
            try {
                memo[key] = fn()
            } catch (e) {
                memo[key] = null
                console.error(`[Guides] sweep lookup '${key}' failed for ${player.username}: ${e}`)
            }
        }
        return memo[key]
    }
    return {
        player: player,
        server: player.server,
        level: player.level,
        // block the player is standing on (null while airborne)
        standingOn: () => once('on', () => {
            if (!player.onGround) return null
            let x = Math.floor(player.x), z = Math.floor(player.z)
            let y = Math.floor(player.y - 0.2)
            let b = player.level.getBlock(x, y, z)
            // tall collision shapes (fences) put the player in the air block above them
            if (String(b.id) === 'minecraft:air') b = player.level.getBlock(x, y - 1, z)
            return b
        }),
        standingIn: () => once('in', () => player.block),
        // { phase, day } from the custom time system (mechanics/time_system.js keeps them in the overworld's data)
        time: () => once('time', () => guideTimeState(player.server)),
        // RayTraceResultJS ({ block, entity, hit, type }) for what the crosshair touches.
        // NOTE: its `distance` field is the max distance we asked for, not how far the hit was; use lookDistance().
        look: () => once('look', () => player.rayTrace(GUIDE_CONFIG.LOOK_DISTANCE)),
        // real eye-to-hit distance in blocks (Infinity if nothing was hit)
        lookDistance: () => once('lookDistance', () => {
            let r = player.rayTrace(GUIDE_CONFIG.LOOK_DISTANCE)
            if (!r || !r.hit || !(r.block || r.entity)) return Infinity
            return player.getEyePosition().distanceTo(r.hit)
        }),
        stacks: () => once('stacks', () => {
            let out = []
            let inv = player.inventory
            let n = inv.getContainerSize()
            for (let i = 0; i < n; i++) {
                let s = inv.getItem(i)
                if (!s.isEmpty()) out.push(s)
            }
            return out
        })
    }
}

function guideTimeState(server) {
    let data = server.getLevel('minecraft:overworld').persistentData
    return { phase: String(data.getString('current_phase') || 'DAWN'), day: data.getInt('custom_day') || 1 }
}

function guideDayInRange(t, day) {
    return (t.minDay === undefined || day >= t.minDay) && (t.maxDay === undefined || day <= t.maxDay)
}

// hand: 'main', 'off' or anything else (= either). An empty hand never matches.
function guideHolding(player, spec, hand) {
    let stacks = []
    if (hand !== 'off') stacks.push(player.mainHandItem)
    if (hand !== 'main') stacks.push(player.offHandItem)
    return stacks.some(s => s && !s.isEmpty() && guideMatchItem(spec, s))
}

// The optional `holding` requirement any trigger may carry. World-level events have no player, so any online
// player holding the item satisfies it.
function guideRequirementOk(t, player, server) {
    if (t.holding === undefined) return true
    if (player) return guideHolding(player, t.holding, t.hand)
    let ok = false
    server.getPlayerList().getPlayers().forEach(p => { if (guideHolding(p, t.holding, t.hand)) ok = true })
    return ok
}

function guideCountItems(ctx, spec) {
    let total = 0
    ;(ctx.stacks() || []).forEach(s => {
        if (guideMatchItem(spec, s)) total += s.count
    })
    return total
}

function guideEvalSweep(t, ctx) {
    let result = false
    switch (t.sweep) {
        case 'standing_on': {
            let b = ctx.standingOn()
            result = !!b && guideMatchBlock(t.match, b)
            break
        }
        case 'standing_in': {
            let b = ctx.standingIn()
            result = !!b && guideMatchBlock(t.match, b)
            break
        }
        case 'looking_at_block': {
            let r = ctx.look()
            result = !!r && !!r.block && (t.distance === undefined || ctx.lookDistance() <= t.distance) &&
                guideMatchBlock(t.match, r.block)
            break
        }
        case 'looking_at_entity': {
            let r = ctx.look()
            result = !!r && !!r.entity && (t.distance === undefined || ctx.lookDistance() <= t.distance) &&
                guideMatchEntity(t.match, r.entity)
            break
        }
        case 'holding':
            result = guideHolding(ctx.player, t.item, t.hand)
            break
        case 'time': {
            let tm = ctx.time()
            result = !!tm && guideMatch(t.match, tm.phase) && guideDayInRange(t, tm.day)
            break
        }
        case 'inventory':
            result = guideCountItems(ctx, t.item) >= (t.count === undefined ? 1 : t.count)
            break
        case 'custom':
            result = !!t.test(ctx)
            break
        default:
            return false
    }
    return result && guideRequirementOk(t, ctx.player, ctx.server) && (!t.where || !!t.where(ctx))
}

function guideSweepSnapshot(ctx) {
    let on = ctx.standingOn(), inn = ctx.standingIn(), r = ctx.look()
    return {
        standingOn: on ? String(on.id) : '-',
        standingIn: inn ? String(inn.id) : '-',
        block: r && r.block ? String(r.block.id) : '-',
        entity: r && r.entity ? String(r.entity.type) : '-',
        time: (() => { let tm = ctx.time(); return tm ? `${tm.phase} day ${tm.day}` : '-' })(),
        distance: r && (r.block || r.entity) ? Math.round(ctx.lookDistance() * 10) / 10 : 0,
        rayType: r ? String(r.type) : 'null (rayTrace failed)',
        rayHit: r && r.hit ? `${r.hit.x.toFixed(2)}, ${r.hit.y.toFixed(2)}, ${r.hit.z.toFixed(2)}` : '-',
        rayReportedDistance: r ? r.distance : '-'
    }
}

function guideRunSweep(server) {
    let sweeps = guideGetIndex(server).sweeps
    let tracing = false
    for (let k in guideRT.trace) { tracing = true; break }
    if (sweeps.length === 0 && !tracing) return

    server.getPlayerList().getPlayers().forEach(player => {
        if (!player.isAlive()) return
        let ctx = guideMakeSweepCtx(player)

        if (guideRT.trace[String(player.uuid)]) {
            let s = guideSweepSnapshot(ctx)
            let line = `on ${s.standingOn} | in ${s.standingIn} | looking at ${s.block} / ${s.entity} (${s.distance}m) | ${s.time}`
            server.runCommandSilent(`title ${player.username} actionbar ${JSON.stringify({ text: line, color: 'gray' })}`)

            let key = `${s.standingOn}|${s.standingIn}|${s.block}|${s.entity}|${s.rayType}`
            if (guideRT.lastTrace[String(player.uuid)] !== key) {
                guideRT.lastTrace[String(player.uuid)] = key
                console.info(`[Guides] sweep ${player.username}: ${line} | ray type=${s.rayType} hit=(${s.rayHit}) kjs-distance=${s.rayReportedDistance}`)
            }
        }

        sweeps.slice().forEach(entry => {
            if (guideIsUnlocked(server, entry.guide.id)) return
            try {
                if (guideEvalSweep(entry.trigger, ctx)) guideUnlock(server, entry.guide.id, { player: player })
            } catch (e) {
                guideWarnOnce(entry.guide.id, `sweep '${entry.trigger.sweep}' threw: ${e}`)
            }
        })
    })
}

ServerEvents.tick(event => {
    let tick = event.server.tickCount
    if (tick % GUIDE_RELOAD_CHECK_TICKS === 0) guideCheckDataFile(event.server, false)
    if (tick % GUIDE_CONFIG.SWEEP_INTERVAL_TICKS !== 0) return
    guideRunSweep(event.server)
})

function guideWarnOnce(id, msg) {
    if (guideRT.warned[id]) return
    guideRT.warned[id] = true
    console.error(`[Guides] guide '${id}': ${msg}`)
}

// ==========================================
// 4. TRIGGER INDEX (locked guides only, rebuilt whenever unlock state changes)
// ==========================================

function guideInvalidate() {
    guideIndex = null
}

function guideGetIndex(server) {
    if (guideIndex) return guideIndex
    let sweeps = []
    let events = {}
    guideList().forEach(g => {
        if (!g || typeof g.id !== 'string' || guideIsUnlocked(server, g.id)) return
        guideToArray(g.unlock).forEach(t => {
            if (!t) return
            if (t.sweep) sweeps.push({ guide: g, trigger: t })
            else if (t.event) (events[t.event] = events[t.event] || []).push({ guide: g, trigger: t })
        })
    })
    guideIndex = { sweeps: sweeps, events: events }
    return guideIndex
}

// ==========================================
// 5. EVENTS
// ==========================================

// makePayload() -> { id, hasTag?, attacker?, damage?, day? }; only built if someone is listening or tracing
function guideDispatch(kind, player, makePayload) {
    if (!player || !player.server) return
    guideDispatchCore(kind, player, player.server, makePayload)
}

// player is null for world-level events (time phases); `ctx.player` is then undefined in `where` functions.
function guideDispatchCore(kind, player, server, makePayload) {
    let tracing = false
    let tracers = []
    if (player) {
        tracing = !!guideRT.trace[String(player.uuid)]
        if (tracing) tracers = [player]
    } else {
        server.getPlayerList().getPlayers().forEach(p => { if (guideRT.trace[String(p.uuid)]) tracers.push(p) })
        tracing = tracers.length > 0
    }
    let list = guideGetIndex(server).events[kind]
    if (!list && !tracing) return

    let p = makePayload()
    if (tracing) {
        let extra = p.damage !== undefined ? ` (${Math.round(p.damage * 10) / 10} dmg)` : (p.day !== undefined ? ` (day ${p.day})` : '')
        let shown = p.ids ? p.ids.join(' / ') : p.id
        tracers.forEach(tp => tp.tell(Text.gray('[guides] ').append(Text.aqua(kind)).append(Text.white(' ' + shown + extra))))
    }
    if (!list) return

    list.slice().forEach(entry => {
        let t = entry.trigger
        if (guideIsUnlocked(server, entry.guide.id)) return
        try {
            if (!(p.ids || [p.id]).some(id => guideMatch(t.match, id, p.hasTag, p.stack))) return
            if (t.minDamage !== undefined && !(p.damage >= t.minDamage)) return
            if (p.day !== undefined && !guideDayInRange(t, p.day)) return
            if (t.by !== undefined && !(p.attacker && guideMatchEntity(t.by, p.attacker))) return
            if (!guideRequirementOk(t, player, server)) return
            let ctx = { player: player, server: server, level: server.getLevel('minecraft:overworld'), event: kind, id: p.id, payload: p }
            if (t.where && !t.where(ctx)) return
            guideUnlock(server, entry.guide.id, { player: player })
        } catch (e) {
            guideWarnOnce(entry.guide.id, `event '${kind}' threw: ${e}`)
        }
    })
}

function guideBlockPayload(block) {
    return { id: String(block.id), hasTag: t => block.hasTag(t) }
}

function guideItemPayload(stack) {
    return { id: String(stack.id), hasTag: t => stack.hasTag(t), stack: stack }
}

function guideEntityPayload(entity) {
    return { id: String(entity.type), hasTag: t => guideEntityHasTag(entity, t) }
}

// A damage source has a message id ('inFire') and a registry id ('minecraft:in_fire'); accept either.
function guideDamageIds(source) {
    let ids = []
    let push = v => { v = String(v); if (ids.indexOf(v) < 0) ids.push(v) }
    try {
        let t = source.type
        if (typeof t === 'string') push(t)
    } catch (e) {}
    try { push(source.getMsgId()) } catch (e) {}
    try {
        let key = String(source.typeHolder().unwrapKey().get().location())
        push(key)
        if (key.indexOf(':') >= 0) push(key.split(':')[1])
    } catch (e) {}
    ids.slice().forEach(i => { if (i.indexOf(':') < 0) push('minecraft:' + i) })
    return ids.length > 0 ? ids : ['unknown']
}

BlockEvents.broken(event => {
    guideDispatch('block_broken', event.player, () => guideBlockPayload(event.block))
})

BlockEvents.placed(event => {
    let p = event.entity
    if (!p || !p.isPlayer()) return
    guideDispatch('block_placed', p, () => guideBlockPayload(event.block))
})

BlockEvents.rightClicked(event => {
    guideDispatch('block_used', event.player, () => guideBlockPayload(event.block))
})

ItemEvents.rightClicked(event => {
    guideDispatch('item_used', event.player, () => guideItemPayload(event.item))
})

ItemEvents.crafted(event => {
    guideDispatch('item_crafted', event.player, () => guideItemPayload(event.item))
})

ItemEvents.smelted(event => {
    guideDispatch('item_smelted', event.player, () => guideItemPayload(event.item))
})

ItemEvents.pickedUp(event => {
    guideDispatch('item_picked_up', event.player, () => guideItemPayload(event.item))
})

ItemEvents.foodEaten(event => {
    let p = event.player
    if (!p) return
    guideDispatch('item_eaten', p, () => guideItemPayload(event.item))
})

PlayerEvents.advancement(event => {
    guideDispatch('advancement', event.player, () => ({ id: String(event.advancement.id) }))
})

EntityEvents.hurt(event => {
    let entity = event.entity
    let source = event.source
    if (!entity) return

    if (entity.isPlayer()) {
        guideDispatch('player_hurt', entity, () => ({
            id: guideDamageIds(source)[0],
            ids: guideDamageIds(source),
            attacker: source.actual,
            damage: event.damage
        }))
        return
    }

    let attacker = source.player
    if (attacker) {
        guideDispatch('entity_hurt', attacker, () => {
            let p = guideEntityPayload(entity)
            p.damage = event.damage
            return p
        })
    }
})

EntityEvents.death(event => {
    let entity = event.entity
    let source = event.source
    if (!entity) return

    if (entity.isPlayer()) {
        guideDispatch('player_died', entity, () => {
            let ids = guideDamageIds(source)
            return { id: ids[0], ids: ids, attacker: source.actual }
        })
        return
    }

    let killer = source.player
    if (killer) guideDispatch('entity_killed', killer, () => guideEntityPayload(entity))
})

// ==========================================
// 6. BOOKS
// ==========================================

function guideRenderPage(g, locked) {
    let parts = ['']
    if (locked) parts.push({ text: '[locked] ', color: 'gray' })
    let book = guideFindBook(g.book)
    let color = book && book.titleColor ? book.titleColor : 'dark_red'   // a color name or #rrggbb, set per book
    parts.push({ text: g.title, bold: true, color: color })
    parts.push({ text: '\n\n' })
    guideToArray(g.content).forEach(c => {
        parts.push(typeof c === 'string' ? { text: c, color: 'black' } : c)
    })
    return JSON.stringify(parts)
}

function guideUnlockedGuides(server) {
    return guideList().filter(g => g && guideIsUnlocked(server, g.id))
}

function guideSyncInventory(player) {
    player.containerMenu.broadcastChanges()
    if (player.inventoryMenu !== player.containerMenu) player.inventoryMenu.broadcastChanges()
}

// pages: array of page JSON strings. Opens the vanilla book screen for one player.
function guideOpenPages(player, pages, hand, book) {
    hand = hand || GuideInteractionHand.MAIN_HAND
    let original = player.getHeldItem(hand)
    let stack = Item.of('minecraft:written_book', {
        // vanilla rejects written-book titles over 32 characters ("Invalid book tag")
        title: String(book ? book.name : GUIDE_CONFIG.BOOK_TITLE).substring(0, 32),
        author: book && book.author ? book.author : GUIDE_CONFIG.BOOK_AUTHOR,
        resolved: true,
        pages: pages
    })
    player.setHeldItem(hand, stack)
    guideSyncInventory(player)
    player.openItemGui(stack, hand)
    player.setHeldItem(hand, original)
    guideSyncInventory(player)
}

// A book's pages: its guides that are unlocked, in file order (includeLocked adds the rest, marked, for testing).
// bookId may be omitted/unknown, which falls back to the first book.
function guideOpenFullBook(player, hand, includeLocked, bookId) {
    let server = player.server
    let book = guideFindBook(bookId) || guideBooks()[0]
    let pages = []
    guideList().forEach(g => {
        if (!g || g.book !== book.id) return
        let unlocked = guideIsUnlocked(server, g.id)
        if (unlocked || includeLocked) pages.push(guideRenderPage(g, !unlocked))
    })
    if (pages.length === 0) {
        pages.push(JSON.stringify(['', { text: 'Nothing recorded yet.', italic: true, color: 'gray' }]))
    }
    guideOpenPages(player, pages, hand, book)
}

// The guide book item for a book: one shared item id, tagged with the book id, named from the JSON.
function guideMakeBookItem(book) {
    let display = { Name: JSON.stringify({ text: book.name, italic: false }) }
    if (book.tooltip) display.Lore = [JSON.stringify({ text: book.tooltip, italic: false, color: 'gray' })]
    let nbt = { guidebook: book.id, display: display }
    // a textured book selects its item model (written by the guide editor) via CustomModelData
    if (book.texture && typeof book.model === 'number') nbt.CustomModelData = book.model
    return Item.of('kubejs:guide_book', nbt)
}

// A lost page item: one shared item id, tagged with the page id, named from the JSON.
function guideMakeLostPageItem(page) {
    let display = { Name: JSON.stringify({ text: page.name, italic: false }) }
    if (page.tooltip) display.Lore = [JSON.stringify({ text: page.tooltip, italic: false, color: 'gray' })]
    return Item.of('kubejs:lost_page', { lostpage: page.id, display: display })
}

// ==========================================
// 7. PAUSE (Multiplayer Server Pause)
// ==========================================
// /pause only TOGGLES, and fails if every client is already on a pause screen (a book screen is one),
// so it must be issued before the clients report in: pause in the same tick the books are opened.

function guideReadForcePause() {
    try {
        const ServerPause = Java.loadClass('me.ichun.mods.serverpause.common.ServerPause')
        return !!ServerPause.eventHandlerServer.forcePause
    } catch (e) {
        return null     // unknown: mod missing or class not reachable
    }
}

function guidePause(server) {
    if (!GUIDE_CONFIG.PAUSE_ON_UNLOCK || guideRT.ownsPause) return
    if (guideReadForcePause() === true) return      // an op paused manually; leave it alone
    server.runCommandSilent('pause')
    guideRT.ownsPause = guideReadForcePause() !== false
}

function guideResume(server) {
    if (!guideRT.ownsPause) return
    guideRT.ownsPause = false
    if (guideReadForcePause() !== false) server.runCommandSilent('pause')
}

// ==========================================
// 8. UNLOCK + POPUP FLOW
// ==========================================

function guideViewerCount() {
    let n = 0
    for (let k in guideRT.viewers) n++
    return n
}

function guideShowNextPopup(server) {
    if (guideRT.popupActive) return
    while (guideRT.queue.length > 0) {
        let g = guideFind(guideRT.queue.shift())
        if (!g) continue
        let players = server.getPlayerList().getPlayers()
        if (players.size() === 0) continue

        let pages = [guideRenderPage(g, false)]
        guideRT.popupActive = true
        players.forEach(p => {
            guideOpenPages(p, pages, GuideInteractionHand.MAIN_HAND, guideFindBook(g.book))
            guideRT.viewers[String(p.uuid)] = true
            p.sendData(GUIDE_CONFIG.CHANNEL_OPEN)
        })
        guidePause(server)
        return
    }
}

function guideFinishPopup(server) {
    guideRT.viewers = {}
    guideRT.popupActive = false
    guideResume(server)
    if (guideRT.queue.length > 0) {
        server.scheduleInTicks(GUIDE_CONFIG.POPUP_GAP_TICKS, () => guideShowNextPopup(server))
    }
}

// Shows a guide to everyone (book + pause) without touching unlock state.
function guidePopup(server, id) {
    if (!guideFind(id)) return false
    guideRT.queue.push(id)
    guideShowNextPopup(server)
    return true
}

// opts: { silent: skip the popup, player: who triggered it }. Returns true if it was newly unlocked.
function guideUnlock(server, id, opts) {
    opts = opts || {}
    if (!guideFind(id) || guideIsUnlocked(server, id)) return false
    guideStore(server).putBoolean(id, true)
    guideInvalidate()
    console.info(`[Guides] Unlocked '${id}'${opts.player ? ' (triggered by ' + opts.player.username + ')' : ''}`)
    if (!opts.silent) guidePopup(server, id)
    return true
}

function guideLock(server, id) {
    if (!guideIsUnlocked(server, id)) return false
    guideStore(server).remove(id)
    guideInvalidate()
    return true
}

// Emergency release: forget the popup, release the pause we hold.
function guideReset(server) {
    guideRT.queue = []
    guideFinishPopup(server)
}

NetworkEvents.dataReceived(GUIDE_CONFIG.CHANNEL_CLOSED, event => {
    let player = event.player
    if (!player || !guideRT.popupActive) return
    delete guideRT.viewers[String(player.uuid)]
    if (guideViewerCount() === 0) guideFinishPopup(player.server)
})

PlayerEvents.loggedOut(event => {
    let key = String(event.player.uuid)
    if (!guideRT.viewers[key]) return
    delete guideRT.viewers[key]
    if (guideRT.popupActive && guideViewerCount() === 0) guideFinishPopup(event.server)
})

ItemEvents.rightClicked('kubejs:guide_book', event => {
    let tag = event.item.nbt
    let bookId = tag && tag.contains('guidebook') ? String(tag.getString('guidebook')) : null
    guideOpenFullBook(event.player, event.hand, false, bookId)
})

// ==========================================
// 9. PUBLIC API
// ==========================================

global.Guides = {
    config: GUIDE_CONFIG,
    runtime: guideRT,
    list: guideList,
    books: guideBooks,
    findBook: guideFindBook,
    bookIds: () => guideBooks().map(b => b.id),
    makeBookItem: guideMakeBookItem,
    lostPages: guideLostPages,
    findLostPage: guideFindLostPage,
    makeLostPageItem: guideMakeLostPageItem,
    find: guideFind,
    ids: () => guideList().map(g => g.id),
    validate: guideValidate,
    reload: server => guideCheckDataFile(server, true),
    isUnlocked: guideIsUnlocked,
    unlockedIds: server => guideUnlockedGuides(server).map(g => g.id),
    unlock: guideUnlock,
    lock: guideLock,
    popup: guidePopup,
    reset: guideReset,
    renderPage: guideRenderPage,
    openPages: guideOpenPages,
    openFullBook: guideOpenFullBook,
    makeSweepCtx: guideMakeSweepCtx,
    evalSweep: guideEvalSweep,
    sweepSnapshot: guideSweepSnapshot,
    readForcePause: guideReadForcePause,
    viewerCount: guideViewerCount,
    // Fire a custom event: global.Guides.fire(player, 'name', { any: 'data' }); matches { event: 'custom', match: 'name' }
    // Called by mechanics/time_system.js when a phase begins.
    firePhase: (server, day, phase) => guideDispatchCore('time_phase', null, server, () => ({ id: String(phase), day: day })),
    // Called by mechanics/proximity.js once a proximity rule is confirmed for a player.
    fireProximity: (player, ruleId) => guideDispatch('proximity', player, () => ({ id: String(ruleId) })),
    fire: (player, name, data) => guideDispatch('custom', player, () => ({ id: String(name), data: data }))
}
