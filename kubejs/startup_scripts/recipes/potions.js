MoreJSEvents.registerPotionBrewing(event => {
    event.removeByPotion(null, null, null);
    event.removeByCustom(null, null, null);
    event.removeContainer("minecraft:splash_potion");
    event.removeContainer("minecraft:lingering_potion");
})