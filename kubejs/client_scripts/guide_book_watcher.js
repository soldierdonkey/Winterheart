// Tells the server when the guide popup book has been closed, so it can undo /pause.
// The server sends 'winterheart_guide_open' when it opens the book; we then wait for the book screen to
// appear and disappear and answer with 'winterheart_guide_closed' (see server_scripts/questing/guides/guides_engine.js).

const GUIDE_WATCH_TIMEOUT_TICKS = 100   // give up if the book never shows up (5s) so the server can't stay paused

let guideWatching = false
let guideSawBook = false
let guideWaited = 0

NetworkEvents.dataReceived('winterheart_guide_open', event => {
    guideWatching = true
    guideSawBook = false
    guideWaited = 0
})

ClientEvents.tick(event => {
    if (!guideWatching) return
    if (!Client.player) return

    let screen = Client.currentScreen
    if (screen && String(screen).indexOf('BookViewScreen') >= 0) {
        guideSawBook = true
        return
    }

    guideWaited++
    if (guideSawBook || guideWaited > GUIDE_WATCH_TIMEOUT_TICKS) {
        guideWatching = false
        Client.player.sendData('winterheart_guide_closed')
    }
})
