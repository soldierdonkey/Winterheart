// Lists every guide book from guides.json in JEI, each with its own name, tooltip and texture.
// All books share the item kubejs:guide_book and differ only by NBT (see guides_engine.js, guideMakeBookItem),
// so JEI is told to treat the 'guidebook' tag as part of the item's identity.
//
// This runs on the client, so it reads guides.json from the local instance. On a dedicated server the clients
// would need their own copy of that file. JEI builds its list when you join a world, so after editing books,
// rejoin (or restart) to see the change.

const GUIDE_JEI_FILE = 'kubejs/server_scripts/questing/guides/guides.json'

function guideJeiReadData() {
    let attempts = [
        () => JsonIO.readString(GUIDE_JEI_FILE),
        () => JsonIO.readString(JsonIO.getPath(GUIDE_JEI_FILE))
    ]
    for (let i = 0; i < attempts.length; i++) {
        try {
            let parsed = JSON.parse(String(attempts[i]()))
            return parsed || {}
        } catch (e) {}
    }
    console.error(`[Guides] JEI: could not read ${GUIDE_JEI_FILE}; guide books will not be listed`)
    return {}
}

function guideJeiReadBooks() {
    let data = guideJeiReadData()
    return Array.isArray(data.books) ? data.books : []
}

function guideJeiReadPages() {
    let data = guideJeiReadData()
    return Array.isArray(data.pages) ? data.pages : []
}

JEIEvents.subtypes(event => {
    event.useNBTKey('kubejs:guide_book', 'guidebook')
    event.useNBTKey('kubejs:lost_page', 'lostpage')
    console.info('[Guides] JEI: registered NBT subtype keys for kubejs:guide_book and kubejs:lost_page')
})

JEIEvents.addItems(event => {
    let books = guideJeiReadBooks()
    console.info(`[Guides] JEI: adding ${books.length} guide book(s)`)
    books.forEach(book => {
        if (typeof book.id !== 'string' || typeof book.name !== 'string') return
        let display = { Name: JSON.stringify({ text: book.name, italic: false }) }
        if (book.tooltip) display.Lore = [JSON.stringify({ text: book.tooltip, italic: false, color: 'gray' })]
        let nbt = { guidebook: book.id, display: display }
        if (book.texture && typeof book.model === 'number') nbt.CustomModelData = book.model
        event.add(Item.of('kubejs:guide_book', nbt))
        console.info(`[Guides] JEI: added '${book.id}'`)
    })

    guideJeiReadPages().forEach(page => {
        if (typeof page.id !== 'string' || typeof page.name !== 'string') return
        let display = { Name: JSON.stringify({ text: page.name, italic: false }) }
        if (page.tooltip) display.Lore = [JSON.stringify({ text: page.tooltip, italic: false, color: 'gray' })]
        event.add(Item.of('kubejs:lost_page', { lostpage: page.id, display: display }))
        console.info(`[Guides] JEI: added lost page '${page.id}'`)
    })
})
