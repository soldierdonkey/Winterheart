// Dummy item for lost journal pages. Every page shares this id and differs only by NBT (lostpage: "<id>"),
// defined in server_scripts/questing/guides/guides.json. Guide triggers match it with { "page": "<id>" }.
StartupEvents.registry('item', event => {
    event.create('lost_page')
        .displayName('Lost Page')
        .texture('minecraft:item/map')
})
