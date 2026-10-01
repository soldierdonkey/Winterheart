// Opens the guide book (see server_scripts/questing/guides/guides_engine.js for the right-click handler)
StartupEvents.registry('item', event => {
    event.create('guide_book')
        .displayName('Empty Guide Book')
        .maxStackSize(1)
        .texture('minecraft:item/writable_book')
})
