# Winterheart World Painter

Macro-scale planner for the escape-south route: tune climate noise, paint biomes/terrain, place structures,
and see the resulting biomes and heights.

    cd tools/worldpainter
    cargo run --release            # GUI
    cargo run --release -- --png out.png [--height]   # headless render
    cargo test

Axes follow Minecraft: +X east, +Z south. Default region is 2048 x 2048 blocks at 8 blocks per cell.
Spawn is (0, 0); the target ring is 400 blocks south, radius 100.

- **Paint**: Biome (sets all five climate channels to a point that yields that biome), Channel (set one
  channel, incl. height bias), Erase. Line mode drags a capsule corridor. `[` `]` resize, Cmd+Z undo, Cmd+S save.
- **Noise**: seed, sea level, domain warp, and per-channel scale/octaves/persistence/lacunarity/contrast/
  amplitude/bias for continentalness, erosion, temperature, humidity, weirdness.
- **Struct**: structure types (name, resource id, footprint) and instances (click to place, drag to move).
- **View**: biome / height / each noise channel, hillshade, tree pixels, grid, painted-area highlight.
- **File**: save/load `worldpainter_project.json`; export `export/biome_map.png`, `height_map.png`,
  `structures.json`, `project.json`.

## NovoAtlas export (File > Export NovoAtlas datapack)

Writes `export/winterheart_novoatlas/` (a datapack): a 1 px = 1 block heightmap and colour-coded biome map
centred on (0, 0), `novoatlas/map_info/world.json`, and `data/minecraft/dimension/overworld.json` pointing the
overworld at NovoAtlas's `image_map` generator. Needs the NovoAtlas mod (Forge 1.20.1 backport). It replaces the
overworld, so it conflicts with `kubejs/data/minecraft/dimension/overworld.json`; use only one. New worlds only.
Outside the image the world is void; the terrain ramps to deep ocean over "Edge ocean width" blocks first.
`--export-novoatlas <dir>` does the same headless. Map size options are under File (2048-6144 blocks);
each image costs roughly 4 bytes per block of area in memory in game.

**File > Export to modpack (install)** exports and copies the datapack to `<instance>/config/paxi/datapacks/winterheart_novoatlas`
(the instance is found by walking up from the working directory), and by default moves
`kubejs/data/minecraft/dimension/overworld.json` into `tools/worldpainter/backups/`. **Uninstall from modpack**
removes the pack and restores the newest backup. Only new worlds pick it up.

Limits: the biome picker is a simplified version of vanilla's `OverworldBiomeBuilder`, and the noise is the
planner's own Perlin, not vanilla's seeded noise. With the NovoAtlas export the planner's heights and biomes are what gets generated (plus the export-only detail bumps). Not yet tested in game.
