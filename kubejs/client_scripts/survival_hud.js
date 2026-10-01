// Receives the survival HUD data from server_scripts/mechanics/custom_sidebars.js ('winterheart_hud' packet)
// and stores it in global.winterheartHud, which startup_scripts/survival_hud_render.js draws.

global.winterheartHud = { title: '', weather: 'clear', lines: [] }

NetworkEvents.dataReceived('winterheart_hud', event => {
    let data = event.data
    let list = data.get('lines')
    let parsed = []
    for (let i = 0; i < list.size(); i++) {
        let raw = String(list.getString(i))
        let split = raw.indexOf('|')
        parsed.push({ style: raw.substring(0, split), text: raw.substring(split + 1) })
    }
    global.winterheartHud = { title: String(data.getString('title')), weather: String(data.getString('weather')), lines: parsed }
})
