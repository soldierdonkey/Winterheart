// Registers the status effect used to indicate that one or more limbs are
// actively decaying/healing under the limb poising system.
// Runtime logic lives in kubejs/server_scripts/mechanics.js/limb_poisoning.js

StartupEvents.registry('mob_effect', event => {
    event.create('winterheart:limb_poising')
        .displayName('Limb Poising')
        .harmful()
        .color(0x4caf50)
    // Icon auto-loaded from kubejs/assets/winterheart/textures/mob_effect/limb_poising.png
})
