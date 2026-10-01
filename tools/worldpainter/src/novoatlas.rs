//! Export the plan as a NovoAtlas datapack (heightmap + colour-coded biome map).
//!
//! Format notes (read from the NovoAtlas 1.1.1 Forge 1.20.1 jar, not yet exercised in game):
//! - Images are centred on world (0, 0), 1 pixel = 1 block, +X right, +Z down.
//! - Height = floor(vertical_scale * red_channel + starting_y).
//! - Outside the image the terrain is void and the biome is `default_biome`.
use crate::biomes;
use crate::model::*;
use crate::noise::Perlin;
use crate::sim::{terrain_height, World};
use serde_json::json;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

pub const NAMESPACE: &str = "winterheart";
pub const MAP_NAME: &str = "world";
pub const STARTING_Y: i32 = 0;

/// Unique, well separated RGB colour for a biome index.
pub fn biome_color(idx: u8) -> [u8; 3] {
    [20 + idx * 4, ((idx as u32 * 53) % 256) as u8, 255 - idx * 3]
}

fn hex(c: [u8; 3]) -> String {
    format!("#{:02X}{:02X}{:02X}", c[0], c[1], c[2])
}

pub struct Maps {
    pub width: usize,
    pub height: usize,
    /// One byte per pixel: terrain height above STARTING_Y.
    pub heights: Vec<u8>,
    /// One biome index per pixel.
    pub biomes: Vec<u8>,
}

/// Evaluate the final terrain height and biome at a block position (what the exported pixel will hold).
pub fn pixel_fields(p: &Project, w: &World, detail: &Perlin, x: f32, z: f32) -> (f32, u8) {
    let (v, bias) = w.sample_smooth(&p.region, x, z);
    let [c, e, t, h, wei] = v;
    let pv = biomes::peaks_valleys(wei);
    let mut y = terrain_height(c, e, pv, bias, p.sea_level);
    if p.detail_amp > 0.0 {
        let land = ((c + 0.1) / 0.2).clamp(0.0, 1.0);
        let s = p.detail_scale.max(1.0) as f64;
        y += detail.fbm(x as f64 / s, z as f64 / s, 3, 0.5, 2.0) as f32 * 2.0 * p.detail_amp * land;
    }
    (y, p.pick_biome(t, h, c, e, wei))
}

pub fn build_maps(p: &Project, w: &World) -> Maps {
    let r = &p.region;
    let (width, height) = (r.nx * r.cell as usize, r.nz * r.cell as usize);
    let detail = Perlin::new(p.seed as u64 ^ 0xDE7A_11ED);
    let mut heights = vec![0u8; width * height];
    let mut bio = vec![0u8; width * height];
    for pz in 0..height {
        for px in 0..width {
            let (x, z) = (r.min_x as f32 + px as f32 + 0.5, r.min_z as f32 + pz as f32 + 0.5);
            let (y, biome) = pixel_fields(p, w, &detail, x, z);
            heights[pz * width + px] = (y - STARTING_Y as f32).round().clamp(0.0, 255.0) as u8;
            bio[pz * width + px] = biome;
        }
    }
    Maps { width, height, heights, biomes: bio }
}

fn write_png(path: &Path, rgba: Vec<u8>, w: usize, h: usize) -> Result<(), String> {
    if w > u16::MAX as usize || h > u16::MAX as usize {
        return Err("image too large".into());
    }
    if let Some(dir) = path.parent() {
        fs::create_dir_all(dir).map_err(|e| e.to_string())?;
    }
    macroquad::texture::Image { bytes: rgba, width: w as u16, height: h as u16 }.export_png(&path.to_string_lossy());
    Ok(())
}

pub fn export_pack(p: &Project, w: &World, dir: &Path) -> Result<String, String> {
    if !p.region.is_centered() {
        return Err("map isn't centred on (0,0); pick a size under File > Map size first".into());
    }
    let maps = build_maps(p, w);
    let (wd, ht) = (maps.width, maps.height);

    let used: BTreeSet<u8> = maps.biomes.iter().copied().collect();
    let mut height_rgba = Vec::with_capacity(wd * ht * 4);
    for &v in &maps.heights {
        height_rgba.extend_from_slice(&[v, v, v, 255]);
    }
    let mut biome_rgba = Vec::with_capacity(wd * ht * 4);
    for &i in &maps.biomes {
        let c = biome_color(i);
        biome_rgba.extend_from_slice(&[c[0], c[1], c[2], 255]);
    }

    let root = dir.join("winterheart_novoatlas");
    let data = root.join("data");
    let nova = data.join(NAMESPACE).join("novoatlas");
    write_png(&nova.join("heightmap").join(format!("{MAP_NAME}.png")), height_rgba, wd, ht)?;
    write_png(&nova.join("biome_map").join(format!("{MAP_NAME}.png")), biome_rgba, wd, ht)?;

    let key = format!("{NAMESPACE}:{MAP_NAME}");
    let biome_entries: Vec<_> = used.iter().map(|&i| json!({ "biome": biomes::biome(i).id, "color": hex(biome_color(i)) })).collect();
    let map_info = json!({
        "height_map": key,
        "starting_y": STARTING_Y,
        "surface_biomes": { "map": key, "biomes": biome_entries },
    });
    let dimension = json!({
        "type": "minecraft:overworld",
        "generator": {
            "type": "novoatlas:image_map",
            "map_info": key,
            "settings": "minecraft:overworld",
            "underground_density_function": "novoatlas:caves",
            "biome_source": { "type": "novoatlas:color_map", "map_info": key, "default_biome": "minecraft:the_void" },
        },
    });
    let put = |path: std::path::PathBuf, v: &serde_json::Value| -> Result<(), String> {
        fs::create_dir_all(path.parent().unwrap()).map_err(|e| e.to_string())?;
        fs::write(path, serde_json::to_string_pretty(v).map_err(|e| e.to_string())?).map_err(|e| e.to_string())
    };
    put(nova.join("map_info").join(format!("{MAP_NAME}.json")), &map_info)?;
    put(data.join("minecraft").join("dimension").join("overworld.json"), &dimension)?;
    put(root.join("pack.mcmeta"), &json!({ "pack": { "description": "Winterheart painted world (NovoAtlas)", "pack_format": 15 } }))?;

    Ok(format!("NovoAtlas pack: {}x{} blocks, {} biomes -> {}", wd, ht, used.len(), root.display()))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn small_project() -> Project {
        let mut p = Project::new();
        p.resize(512);
        p
    }

    #[test]
    fn biome_colors_are_unique() {
        let mut seen = std::collections::HashSet::new();
        for i in 0..biomes::BIOMES.len() as u8 {
            assert!(seen.insert(biome_color(i)), "duplicate colour for biome {i}");
        }
    }

    #[test]
    fn exported_corridor_is_land() {
        let mut p = Project::new();
        p.resize(1536);
        let w = World::new(&p);
        let detail = Perlin::new(p.seed as u64 ^ 0xDE7A_11ED);
        for z in (0..=520).step_by(4) {
            for x in (-40..=40).step_by(4) {
                let (y, biome) = pixel_fields(&p, &w, &detail, x as f32, z as f32);
                assert!(!biomes::is_ocean(biome), "ocean biome at {x},{z}");
                assert!(y > p.sea_level + 0.5, "below sea at {x},{z}: {y}");
            }
        }
    }

    #[test]
    fn edges_are_ocean() {
        let p = small_project();
        let w = World::new(&p);
        let detail = Perlin::new(1);
        let (y, biome) = pixel_fields(&p, &w, &detail, p.region.min_x as f32 + 1.0, 0.0);
        assert!(biomes::is_ocean(biome) && y < p.sea_level, "edge not ocean: {y}");
    }

    #[test]
    fn pack_files_are_written_and_decodable() {
        let mut p = small_project();
        p.edge_width = 64.0;
        let w = World::new(&p);
        let dir = std::env::current_dir().unwrap().join("target").join("test_out");
        let _ = fs::remove_dir_all(&dir);
        let msg = export_pack(&p, &w, &dir).unwrap();
        println!("{msg}");
        let root = dir.join("winterheart_novoatlas");
        let info: serde_json::Value = serde_json::from_str(&fs::read_to_string(root.join("data/winterheart/novoatlas/map_info/world.json")).unwrap()).unwrap();
        let entries = info["surface_biomes"]["biomes"].as_array().unwrap();
        assert!(!entries.is_empty());
        for f in ["data/winterheart/novoatlas/heightmap/world.png", "data/winterheart/novoatlas/biome_map/world.png"] {
            let bytes = fs::read(root.join(f)).unwrap();
            let img = macroquad::texture::Image::from_file_with_format(&bytes, Some(macroquad::prelude::ImageFormat::Png)).unwrap();
            assert_eq!((img.width(), img.height()), (512, 512));
        }
        // every colour in the biome map must be declared in map_info
        let bytes = fs::read(root.join("data/winterheart/novoatlas/biome_map/world.png")).unwrap();
        let img = macroquad::texture::Image::from_file_with_format(&bytes, Some(macroquad::prelude::ImageFormat::Png)).unwrap();
        let declared: std::collections::HashSet<String> = entries.iter().map(|e| e["color"].as_str().unwrap().to_string()).collect();
        for px in img.bytes.chunks(4) {
            assert!(declared.contains(&hex([px[0], px[1], px[2]])));
        }
    }
}
