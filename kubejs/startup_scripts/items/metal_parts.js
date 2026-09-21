// kubejs/startup_scripts/metal_parts.js

StartupEvents.registry('item', event => {
  const metals = ['copper', 'iron', 'gold', 'diamond'];
  const parts = ['plate', 'rod', 'ring', 'gear', 'bolt', 'screw'];

  metals.forEach(metal => {
    parts.forEach(part => {
      const id = `${metal}_${part}`;
      
      // Formats 'copper_gear' -> 'Copper Gear'
      const displayName = id
        .split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');

      event.create(`winterheart:${id}`)
        .texture(`winterheart:item/${id}`)
        .displayName(displayName);
    });
  });
});