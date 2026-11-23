// Runs startup_scripts/survival_hud_render.js against a mock GuiGraphics and prints every draw call as JSON.
// Used by preview.py; node record_hud.js <preview.json> <render script>
const fs = require('fs')
const [dataFile, scriptFile] = process.argv.slice(2)
const data = JSON.parse(fs.readFileSync(dataFile, 'utf8'))
const src = fs.readFileSync(scriptFile, 'utf8')
const SIGNATURE = 'drawString(net.minecraft.client.gui.Font,java.lang.String,float,float,int,boolean)'
const SCREEN_W = 200

const result = {}
for (const weather of ['clear', 'snow', 'blizzard']) {
    const ops = []
    const g = {
        fill: (x0, y0, x1, y1, c) => ops.push(['fill', x0, y0, x1, y1, c >>> 0]),
        blit: (tex, x, y, u, v, w, h, tw, th) => ops.push(['blit', String(tex), x, y, u, v, w, h, tw, th]),
        [SIGNATURE]: (font, text, x, y, c) => ops.push(['text', text, x, y, c >>> 0])
    }
    const font = { 'width(java.lang.String)': t => t.length * 6 }
    const global = { winterheartHud: { title: data.title, weather, lines: data.lines.map(l => { const i = l.indexOf('|'); return { style: l.slice(0, i), text: l.slice(i + 1) } }) } }
    const noop = () => {}
    const build = new Function('Platform', 'ForgeEvents', 'global', 'Java', 'console', src + '\nreturn { hudRender, HUD_VARIANTS }')
    const m = build({ isClientEnvironment: () => false }, { onEvent: noop }, global, { loadClass: noop }, console)
    m.hudRender(g, SCREEN_W, font)
    result[weather] = ops
    result.variants = m.HUD_VARIANTS
}
console.log(JSON.stringify(result))
