// Draws the survival HUD panel (top right) with a tiled snowy spruce frame whose snow level follows the weather.
// ForgeEvents only exists in startup scripts, so the drawing lives here (restart the game after editing it).
// The data is stored by client_scripts/survival_hud.js in global.winterheartHud = { title, weather, lines: [{ style, text }] }.
// Border tiles live in assets/winterheart/textures/gui/wood_border.png, a 64x120 sheet of 8x8 tiles. It holds three
// 64x40 blocks, one per snow level (0 light = clear, 1 medium = snow, 2 heavy = blizzard), each laid out as:
//   rows 0-3: top, bottom, left, right edges, 8 variants each (one per column)
//   row 4:    top-left, top-right, bottom-left, bottom-right corners
// Each edge tile is picked from the variants by a hash of its position, so the pattern looks random but is stable.
// Overflow: planks poking out past the frame come from assets/winterheart/textures/gui/wood_stubs.png (32x32, 8x8
// sprites: row 0 left, 1 right, 2 top, 3 bottom stubs, 4 variants each). The snow piles on the top and bottom bars and
// the snow clumps on the sides are drawn in code, so the pile is one smooth curve across the whole width.
// Errors are logged once as '[SurvivalHUD]' in logs/kubejs/startup.log.

const HUD_TEXTURE = 'winterheart:textures/gui/wood_border.png'
const HUD_SHEET_W = 64
const HUD_SHEET_H = 120
const HUD_BLOCK_H = 40
const HUD_VARIANTS = 8
// Background planks: assets/winterheart/textures/gui/plank_bg.png, a 16x8 tile repeated across the panel
const HUD_BG_TEXTURE = 'winterheart:textures/gui/plank_bg.png'
const HUD_BG_TILE_W = 16
const HUD_BG_TILE_H = 8
const HUD_SNOW_LEVELS = { clear: 0, snow: 1, blizzard: 2 }
const HUD_STUB_TEXTURE = 'winterheart:textures/gui/wood_stubs.png'
const HUD_STUB_SHEET = 32
const HUD_STUB_CHANCE = [0.10, 0.14, 0.18]     // chance per border tile of a plank poking out, per snow level
const HUD_CLUMP_CHANCE = [0.0, 0.18, 0.4]      // chance per side tile of a snow clump clinging to the outside
const HUD_PILE_BASE = [2.6, 4.8, 7.2]          // average pile height in px per snow level
const HUD_PILE_AMP = [1.6, 2.6, 3.8]           // how much the height rolls up and down
const HUD_PILE_STEP = 1                        // pile heights snap to multiples of this many px, so the pile climbs in steps
const HUD_PILE_CUTOFF = 1.5                    // columns whose height is below this (px) are not drawn at all
const HUD_SNOW_BODY = hudArgb(255, 234, 245, 252)
const HUD_SNOW_HIGHLIGHT = hudArgb(255, 252, 254, 255)
const HUD_SNOW_SHADE = hudArgb(255, 176, 202, 226)

const HUD_TILE = 8
const HUD_WIDTH = 128          // panel interior width, multiple of 8 so the border tiles fit exactly
const HUD_MARGIN = 10          // distance from the screen edge
const HUD_PAD = 8              // inner padding, keeps text clear of the icicles
const HUD_LINE_HEIGHT = 10
const HUD_SPACER_HEIGHT = 5

function hudArgb(a, r, g, b) {
    return (a << 24) | (r << 16) | (g << 8) | b
}

const HUD_BG_SHADE = hudArgb(90, 0, 0, 0)   // extra darkening over the planks so the text stays readable
const HUD_TITLE_COLOR = hudArgb(255, 200, 232, 250)
const HUD_STYLE_COLORS = {
    section: hudArgb(255, 150, 205, 240),
    normal: hudArgb(255, 226, 232, 238),
    accent: hudArgb(255, 160, 215, 235),
    warning: hudArgb(255, 235, 175, 70),
    critical: hudArgb(255, 225, 60, 60),
    muted: hudArgb(255, 130, 140, 150)
}


// GuiGraphics.drawString is ambiguous from JS when called by plain name: Forge adds a (String, float, float) overload
// next to vanilla's (Component, int, int). Naming the signature picks one overload (tested against Rhino).
const HUD_DRAW_STRING = 'drawString(net.minecraft.client.gui.Font,java.lang.String,float,float,int,boolean)'

function hudDrawText(g, font, text, x, y, color) {
    g[HUD_DRAW_STRING](font, text, x, y, color, true)
}

function hudBlit(g, level, u, v, x, y) {
    g.blit(HUD_TEXTURE, x, y, u, level * HUD_BLOCK_H + v, HUD_TILE, HUD_TILE, HUD_SHEET_W, HUD_SHEET_H)
}

// Fills (x, y, w, h) with the plank tile. w must be a multiple of 16 and h a multiple of 8.
function hudDrawBackground(g, x, y, w, h) {
    for (let dy = 0; dy < h; dy += HUD_BG_TILE_H) {
        for (let dx = 0; dx < w; dx += HUD_BG_TILE_W) {
            g.blit(HUD_BG_TEXTURE, x + dx, y + dy, 0, 0, HUD_BG_TILE_W, HUD_BG_TILE_H, HUD_BG_TILE_W, HUD_BG_TILE_H)
        }
    }
    g.fill(x, y, x + w, y + h, HUD_BG_SHADE)
}

// Text width for centring. Font.width is overloaded (String / FormattedText / FormattedCharSequence), so try the
// explicit signature first, then the plain call, then a rough estimate. The first one that works is remembered.
const HUD_WIDTH_SIGNATURE = 'width(java.lang.String)'
let hudWidthMode = 0
function hudTextWidth(font, text) {
    while (hudWidthMode < 2) {
        try {
            return hudWidthMode === 0 ? font[HUD_WIDTH_SIGNATURE](text) : font.width(text)
        } catch (e) {
            hudWidthMode++
        }
    }
    return text.length * 6
}

// Stable pseudo-random variant (0..HUD_VARIANTS-1) for the index-th tile along a side
function hudVariant(index, side) {
    let n = Math.sin((index + 1) * 12.9898 + (side + 1) * 78.233) * 43758.5453
    return Math.floor((n - Math.floor(n)) * HUD_VARIANTS) % HUD_VARIANTS
}

// Border drawn around the rectangle (x, y, w, h): half of it outside, half inside. w and h must be multiples of 8.
function hudDrawBorder(g, level, x, y, w, h) {
    let x0 = x - HUD_TILE / 2
    let y0 = y - HUD_TILE / 2
    let outerW = w + HUD_TILE
    let outerH = h + HUD_TILE
    let x1 = x0 + outerW - HUD_TILE
    let y1 = y0 + outerH - HUD_TILE

    for (let dx = HUD_TILE; dx < outerW - HUD_TILE; dx += HUD_TILE) {
        let i = dx / HUD_TILE
        hudBlit(g, level, hudVariant(i, 0) * HUD_TILE, 0, x0 + dx, y0)
        hudBlit(g, level, hudVariant(i, 1) * HUD_TILE, 8, x0 + dx, y1)
    }
    for (let dy = HUD_TILE; dy < outerH - HUD_TILE; dy += HUD_TILE) {
        let i = dy / HUD_TILE
        hudBlit(g, level, hudVariant(i, 2) * HUD_TILE, 16, x0, y0 + dy)
        hudBlit(g, level, hudVariant(i, 3) * HUD_TILE, 24, x1, y0 + dy)
    }
    hudBlit(g, level, 0, 32, x0, y0)
    hudBlit(g, level, 8, 32, x1, y0)
    hudBlit(g, level, 16, 32, x0, y1)
    hudBlit(g, level, 24, 32, x1, y1)
}

// Stable pseudo-random number in [0, 1) for (index, side, salt)
function hudRand(index, side, salt) {
    let n = Math.sin((index + 1) * 12.9898 + (side + 1) * 78.233 + salt * 37.719) * 43758.5453
    return n - Math.floor(n)
}

// Height (px, fractional) of the snow pile for each column of a bar that is len px wide. Rolling hills from a few
// sines, a couple of extra humps in heavy snow, and the ends rounded off so the pile slopes down past the corners.
const hudProfileCache = {}
function hudPileProfile(len, level, seed) {
    let key = len + ':' + level + ':' + seed
    if (hudProfileCache[key]) return hudProfileCache[key]
    let base = HUD_PILE_BASE[level]
    let amp = HUD_PILE_AMP[level]
    let hump1 = len * (0.2 + 0.2 * hudRand(seed, 0, 1))
    let hump2 = len * (0.6 + 0.25 * hudRand(seed, 0, 2))
    let out = []
    for (let i = 0; i < len; i++) {
        let v = base + amp * (0.55 * Math.sin(i * 0.16 + seed) + 0.3 * Math.sin(i * 0.07 + seed * 1.7 + 1) + 0.15 * Math.sin(i * 0.33 + seed * 2.3))
        if (level === 2) {
            v += 2.2 * Math.exp(-Math.pow((i - hump1) / 9, 2)) + 1.6 * Math.exp(-Math.pow((i - hump2) / 7, 2))
        }
        let edge = Math.min(i, len - 1 - i)
        let round = edge >= 8 ? 1 : Math.sqrt(1 - Math.pow(1 - edge / 8, 2))
        out.push(Math.max(0, v * round))
    }
    hudProfileCache[key] = out
    return out
}

// Snow pile sitting on a surface. Columns run from x0 to x0 + len; surfaceY is the top row of the wood. The pile
// grows upward (or is scaled down for the bottom bar) and sinks one pixel into the wood to hide its outline.
// Heights are snapped to HUD_PILE_STEP and everything below HUD_PILE_CUTOFF is skipped, which keeps the edges sharp.
function hudDrawPile(g, x0, len, surfaceY, level, seed, scale) {
    let profile = hudPileProfile(len, level, seed)
    let baseY = surfaceY + 1
    for (let i = 0; i < len; i++) {
        let raw = profile[i] * scale
        if (raw < HUD_PILE_CUTOFF) continue
        let full = Math.max(HUD_PILE_STEP, Math.floor(raw / HUD_PILE_STEP) * HUD_PILE_STEP)
        let top = baseY - full
        let x = x0 + i
        // ragged underside: stretches of 4 columns slump a pixel over the plank
        let bottom = (full >= 3 && hudRand(Math.floor(i / 4), seed, 4) < 0.4) ? baseY + 1 : baseY
        g.fill(x, top, x + 1, bottom, HUD_SNOW_BODY)
        g.fill(x, top, x + 1, top + 1, HUD_SNOW_HIGHLIGHT)
        g.fill(x, bottom - 1, x + 1, bottom, HUD_SNOW_SHADE)
    }
}

// Round snow clump clinging to the outside of a side bar. dir is -1 for the left side, +1 for the right.
function hudDrawClump(g, faceX, cy, radius, dir) {
    for (let dy = -radius; dy <= radius; dy++) {
        let reach = Math.round(Math.sqrt(radius * radius - dy * dy))
        if (reach < 1) continue
        let xa = dir < 0 ? faceX - reach + 1 : faceX
        let xb = dir < 0 ? faceX + 1 : faceX + reach
        g.fill(xa, cy + dy, xb, cy + dy + 1, dy === -radius || dy === -radius + 1 ? HUD_SNOW_HIGHLIGHT : (dy === radius ? HUD_SNOW_SHADE : HUD_SNOW_BODY))
    }
}

function hudDrawStub(g, side, variant, x, y) {
    g.blit(HUD_STUB_TEXTURE, x, y, variant * HUD_TILE, side * HUD_TILE, HUD_TILE, HUD_TILE, HUD_STUB_SHEET, HUD_STUB_SHEET)
}

// Everything that leaks outside the plain frame: planks poking out, snow clumps on the sides and the two piles.
// The frame occupies (x0, y0) .. (x0 + outerW, y0 + outerH); wood rows/cols inside each border tile are noted below.
function hudDrawOverflow(g, level, x, y, w, h) {
    let x0 = x - HUD_TILE / 2
    let y0 = y - HUD_TILE / 2
    let outerW = w + HUD_TILE
    let outerH = h + HUD_TILE
    let x1 = x0 + outerW - HUD_TILE
    let y1 = y0 + outerH - HUD_TILE
    let tilesX = outerW / HUD_TILE
    let tilesY = outerH / HUD_TILE

    // planks poking out; each gets a random sub-tile offset along the bar
    for (let i = 1; i < tilesX - 1; i++) {
        let jitter = Math.floor(hudRand(i, 8, 1) * 5) - 2
        if (hudRand(i, 4, 0) < HUD_STUB_CHANCE[level]) hudDrawStub(g, 2, Math.floor(hudRand(i, 4, 1) * 4), x0 + i * HUD_TILE + jitter, y0 + 3 - 7)
        if (hudRand(i, 5, 0) < HUD_STUB_CHANCE[level]) hudDrawStub(g, 3, Math.floor(hudRand(i, 5, 1) * 4), x0 + i * HUD_TILE + jitter, y1 + 7)
    }
    for (let i = 1; i < tilesY - 1; i++) {
        let jitter = Math.floor(hudRand(i, 9, 1) * 5) - 2
        if (hudRand(i, 6, 0) < HUD_STUB_CHANCE[level]) hudDrawStub(g, 0, Math.floor(hudRand(i, 6, 1) * 4), x0 - 6, y0 + i * HUD_TILE + jitter)
        if (hudRand(i, 7, 0) < HUD_STUB_CHANCE[level]) hudDrawStub(g, 1, Math.floor(hudRand(i, 7, 1) * 4), x1 + 5, y0 + i * HUD_TILE + jitter)
    }

    // snow clumps clinging to the outer faces of the side bars (outer face: column 1 on the left, 5 on the right)
    for (let i = 1; i < tilesY - 1; i++) {
        if (hudRand(i, 10, 0) < HUD_CLUMP_CHANCE[level]) hudDrawClump(g, x0 + 1, y0 + i * HUD_TILE + 1 + Math.floor(hudRand(i, 10, 1) * 6), 2 + Math.floor(hudRand(i, 10, 2) * 2), -1)
        if (hudRand(i, 11, 0) < HUD_CLUMP_CHANCE[level]) hudDrawClump(g, x1 + 5, y0 + i * HUD_TILE + 1 + Math.floor(hudRand(i, 11, 1) * 6), 2 + Math.floor(hudRand(i, 11, 2) * 2), 1)
    }

    // piles: the top one starts 2px past each corner so it spills over the sides; the bottom one sits on the inside
    hudDrawPile(g, x0 - 1, outerW + 2, y0 + 3, level, 3, 1)
    hudDrawPile(g, x0 + 2, outerW - 4, y1 + 3, level, 7, 0.55)
}

// x for a line of the given width: left-aligned inside the padding, or centred across the panel interior
function hudLineX(font, text, panelX, centered) {
    if (!centered) return panelX + HUD_PAD
    return panelX + Math.floor((HUD_WIDTH - hudTextWidth(font, text)) / 2)
}

function hudRender(g, screenW, font) {
    let hud = global.winterheartHud
    let level = HUD_SNOW_LEVELS[hud.weather] || 0
    let contentH = HUD_LINE_HEIGHT + 4
    hud.lines.forEach(line => {
        contentH += line.style === 'spacer' ? HUD_SPACER_HEIGHT : HUD_LINE_HEIGHT
    })
    let h = Math.ceil((contentH + HUD_PAD * 2) / HUD_TILE) * HUD_TILE
    let x = screenW - HUD_MARGIN - HUD_WIDTH
    let y = HUD_MARGIN

    hudDrawBackground(g, x, y, HUD_WIDTH, h)
    hudDrawBorder(g, level, x, y, HUD_WIDTH, h)
    hudDrawOverflow(g, level, x, y, HUD_WIDTH, h)

    let ty = y + HUD_PAD
    hudDrawText(g, font, hud.title, hudLineX(font, hud.title, x, true), ty, HUD_TITLE_COLOR)
    ty += HUD_LINE_HEIGHT + 4
    hud.lines.forEach(line => {
        if (line.style === 'spacer') {
            ty += HUD_SPACER_HEIGHT
            return
        }
        // 'c:' prefix centres any style; 'section' headers are always centred
        let centered = line.style.indexOf('c:') === 0
        let style = centered ? line.style.substring(2) : line.style
        centered = centered || style === 'section'
        let color = HUD_STYLE_COLORS[style] || HUD_STYLE_COLORS.normal
        hudDrawText(g, font, line.text, hudLineX(font, line.text, x, centered), ty, color)
        ty += HUD_LINE_HEIGHT
    })
}

let hudErrorLogged = false

// Only on the client: the event class does not exist on a dedicated server
if (Platform.isClientEnvironment()) {
    ForgeEvents.onEvent('net.minecraftforge.client.event.RenderGuiOverlayEvent$Post', event => {
        // Post fires for every overlay; draw once per frame, after the hotbar
        if (String(event.getOverlay().id()) !== 'minecraft:hotbar') return
        let hud = global.winterheartHud
        let mc = Java.loadClass('net.minecraft.client.Minecraft').getInstance()
        if (!mc.player || !hud || hud.lines.length === 0) return
        try {
            hudRender(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), mc.font)
        } catch (e) {
            if (!hudErrorLogged) {
                hudErrorLogged = true
                console.error('[SurvivalHUD] render failed: ' + e)
            }
        }
    })
}
