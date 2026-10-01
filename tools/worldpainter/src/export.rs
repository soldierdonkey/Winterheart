use crate::biomes;
use crate::model::*;
use crate::render::{render_scaled, ViewMode};
use crate::sim::World;
use macroquad::texture::Image;
use serde_json::json;
use std::fs;
use std::path::Path;

pub const PROJECT_FILE: &str = "worldpainter_project.json";
pub const EXPORT_DIR: &str = "export";

pub fn save_project(p: &Project, path: &str) -> Result<(), String> {
    let s = serde_json::to_string(p).map_err(|e| e.to_string())?;
    fs::write(path, s).map_err(|e| e.to_string())
}

pub fn load_project(path: &str) -> Result<Project, String> {
    let s = fs::read_to_string(path).map_err(|e| e.to_string())?;
    serde_json::from_str(&s).map_err(|e| e.to_string())
}

pub fn save_png(path: &Path, bytes: Vec<u8>, w: usize, h: usize) {
    Image { bytes, width: w as u16, height: h as u16 }.export_png(&path.to_string_lossy());
}

/// Writes preview PNGs and a structure manifest. Returns a one-line summary.
pub fn export_all(p: &Project, w: &World, dir: &str) -> Result<String, String> {
    let dir = Path::new(dir);
    fs::create_dir_all(dir).map_err(|e| e.to_string())?;

    for (name, mode) in [("biome_map.png", ViewMode::Biome), ("height_map.png", ViewMode::Height)] {
        let (bytes, wd, ht) = render_scaled(p, w, mode, 4, mode == ViewMode::Biome, true);
        save_png(&dir.join(name), bytes, wd, ht);
    }

    let [sx, sz] = p.spawn;
    let structures: Vec<_> = p
        .structures
        .iter()
        .map(|s| {
            let t = &p.types[s.ty.min(p.types.len() - 1)];
            let cell = w.at_block(p, s.x as f32, s.z as f32);
            let (fw, fd) = s.footprint(&p.types);
            json!({
                "name": t.name,
                "id": t.id,
                "x": s.x,
                "z": s.z,
                "rotation_degrees": s.rot as i32 * 90,
                "footprint": { "x": fw, "z": fd },
                "offset_from_spawn": { "dx": s.x - sx, "dz": s.z - sz },
                "estimated_surface_y": cell.map(|c| c.height.round() as i32),
                "biome": cell.map(|c| biomes::biome(c.biome).id),
            })
        })
        .collect();
    let manifest = json!({
        "note": "Planner output. Axes follow Minecraft: +X east, +Z south. Heights are estimates from the planner's terrain model, not the game's.",
        "spawn": { "x": sx, "z": sz },
        "guide": { "offset": p.guide_offset, "radius": p.guide_radius },
        "structures": structures,
    });
    fs::write(dir.join("structures.json"), serde_json::to_string_pretty(&manifest).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    save_project(p, &dir.join("project.json").to_string_lossy())?;
    Ok(format!("Exported biome_map.png, height_map.png, structures.json, project.json to {}/", dir.display()))
}
