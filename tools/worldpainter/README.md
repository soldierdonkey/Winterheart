# Winterheart World Painter

Macro-scale planner for the escape-south route: paint biomes/terrain by hand, place structures, and see the
resulting biomes and heights. There is no generated noise: unpainted ground is a flat canvas (World tab).

    cd tools/worldpainter
    cargo run --release            # GUI
    cargo run --release -- --png out.png [--height]   # headless render
    cargo test

Axes follow Minecraft: +X east, +Z south. Default region is 2048 x 2048 blocks at 8 blocks per cell.
Spawn is (0, 0); a new project's escape path runs 400 blocks south to a target ring of radius 100.

- **Paint**: Biome (sets all five climate channels to a point that yields that biome), Channel, Erase, Naturalize.
  Line mode drags a capsule corridor. `[` `]` resize, Cmd+Z undo, Cmd+S save. Arrow keys pan (Shift = faster),
  `+` `-` zoom (also right-drag / Space+drag and the wheel).
  - **Channel** works on one channel (continentalness, erosion, temperature, humidity, weirdness, height):
    **Set** approaches a value (for Height: a bias in blocks, or with **Absolute Y** an exact Y above y=0, not
    sea level); **Raise/Lower** changes it by a fixed amount; **Noise** adds fbm noise (amplitude, wavelength,
    octaves, seed) to that channel only. Raise/Lower and Noise apply once per stroke at the strongest brush
    weight a cell saw, so lingering doesn't pile up; stroke again for more. On Height they move the bias and any
    absolute-Y paint together.
  - **Detail** (a Channel option) scales the export-only terrain detail (World tab) where painted: 1 = as set,
    0 = smooth ground, up to 2. Use Set 0 around spawn or other building sites. The info box shows "in game ~Y",
    the exported height under the cursor including that detail, which the map colours don't show.
  - **Naturalize** (brush, or **Naturalize whole map**) blurs values toward their neighbourhood, warps shapes
    (edge roughness) and adds noise, on painted and unpainted ground alike, for the channels toggled on. Also once
    per stroke; the whole-map button uses the Strength slider.
- **World**: the canvas climate for unpainted ground, sea level, edge ocean width (0 = off), border padding, the
  winter palette, and the export-only terrain detail (including whether it applies on absolute-Y paint; off by
  default, so painted Y is exact).
- **Struct**: structure types (name, resource id, footprint) and instances (click to place, drag to move).
  Types with **Place in game** on (Watchtower, Shipwreck) are real templates: **Save all** writes their instances
  (footprint centre, rotation) to `kubejs/server_scripts/worldgen/fixed_structures.json`, which
  `fixed_structures.js` pastes once per world (`/kubejs reload server_scripts` picks up changes). A spot that was
  already pasted in a world stays; moving it pastes a new copy at the new spot.
- **Path**: the escape route from spawn. Click on the line to add a bend, click elsewhere to extend the path to a
  new target, drag points to move them, Del removes the selected one. Sliders set the target ring radius, corridor
  half-width, edge hardness, spawn/target clearing radii, and the terrain the corridor is painted with
  (continentalness, erosion, weirdness, lift in blocks). The orange band previews what **Paint corridor** covers.
  To reshape a painted corridor: **Erase corridor**, move points, paint again (Cmd+Z undoes either).
  **Spawn Y** (default 66) is where the player and the Starter Structure are placed: Save all writes it to
  `config/starterstructure.json5` as both the spawn coordinates and a fixed structure position. The **flat spawn
  pad** (radius, blend) levels the exported ground to Spawn Y - 1 around spawn and gives it the pack's own
  `winterheart:spawn_clearing` biome (a copy of Snowy Plains in `kubejs/data/winterheart/worldgen/biome`), which
  Streams Reflowing is told not to carve, so no stream runs through spawn.
  The path is saved in the project; `structures.json` gets the target, radius, waypoints and length under `guide`.
- **View**: biome / height / each noise channel, hillshade, tree pixels, grid, painted-area highlight.
- **File**: **Save all** (Cmd+S) writes `worldpainter_project.json`, `export/biome_map.png`, `height_map.png`, `structures.json`,
  syncs the spawn into `config/starterstructure.json5` (spawn coords, Y = surface + 1) and writes the in-game
  structure spots. Load reloads the project.

## NovoAtlas export (File > Export NovoAtlas datapack)

Writes `export/winterheart_novoatlas/` (a datapack): a 1 px = 1 block heightmap and colour-coded biome map
centred on (0, 0), `novoatlas/map_info/world.json`, and `data/minecraft/dimension/overworld.json` pointing the
overworld at NovoAtlas's `image_map` generator. Needs the NovoAtlas mod (Forge 1.20.1 backport). It replaces the
overworld, so it conflicts with `kubejs/data/minecraft/dimension/overworld.json`; use only one. New worlds only.
**Border padding** (World tab, 0-1024, default 512) makes the image that many blocks larger on every side and
fills the extra ring with the nearest border pixel's climate, so the border biomes (and their terrain) carry on
outward. The map view shows it dimmed, with an outline. Outside the image the world is void; the terrain ramps to
deep ocean over "Edge ocean width" blocks before the outside edge of the padding (set the width to 0 to skip it).
`--export-novoatlas <dir>` does the same headless. Map size options are under File (2048-6144 blocks);
each image costs roughly 4 bytes per block of area in memory in game.

**File > Export to modpack (install)** exports and copies the datapack to `<instance>/config/paxi/datapacks/winterheart_novoatlas`
(the instance is found by walking up from the working directory), and by default moves
`kubejs/data/minecraft/dimension/overworld.json` into `tools/worldpainter/backups/`. **Uninstall from modpack**
removes the pack and restores the newest backup. Only new worlds pick it up.

Limits: the biome picker is a simplified version of vanilla's `OverworldBiomeBuilder`. Biomes come from climate,
not height, so painting an absolute Y below sea level on land climate gives a flooded land biome. With the NovoAtlas export the planner's heights and biomes are what gets generated (plus the export-only detail bumps). Not yet tested in game.
