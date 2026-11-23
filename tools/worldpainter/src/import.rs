//! Rebuild an editable project from an installed NovoAtlas datapack (biome map + heightmap).
//!
//! Lossy by nature: every cell becomes fully painted with the climate point of its biome and an exact absolute Y
//! (the mean height of the cell's pixels). Structures, spawn and path are kept from the project already on disk.
use crate::biomes;
use crate::model::*;
use crate::novoatlas::{biome_color, MAP_NAME, NAMESPACE};
use crate::sim::World;
use macroquad::prelude::ImageFormat;
use macroquad::texture::Image;
use std::path::Path;

fn load_png(path: &Path) -> Result<Image, String> {
    let bytes = std::fs::read(path).map_err(|e| format!("{}: {e}", path.display()))?;
    Image::from_file_with_format(&bytes, Some(ImageFormat::Png)).map_err(|e| format!("{}: {e}", path.display()))
}

/// Replace the paint (and padding) of `p` with what the datapack in `pack` encodes. Returns a summary.
pub fn import_pack(p: &mut Project, pack: &Path) -> Result<String, String> {
    let nova = pack.join("data").join(NAMESPACE).join("novoatlas");
    let bio = load_png(&nova.join("biome_map").join(format!("{MAP_NAME}.png")))?;
    let hgt = load_png(&nova.join("heightmap").join(format!("{MAP_NAME}.png")))?;
    if (bio.width, bio.height) != (hgt.width, hgt.height) {
        return Err("biome map and heightmap sizes differ".into());
    }
    let (w, h) = (bio.width as usize, bio.height as usize);
    let r = p.region.clone();
    let cell = r.cell as usize;
    let (gw, gh) = (r.nx * cell, r.nz * cell);
    if w < gw || h < gh || (w - gw) % 2 != 0 || (h - gh) % 2 != 0 || (w - gw) / 2 != (h - gh) / 2 {
        return Err(format!("image {w}x{h} doesn't fit region {gw}x{gh} plus even padding"));
    }
    let pad = (w - gw) / 2;

    let mut by_color = std::collections::HashMap::new();
    for i in 0..biomes::BIOMES.len() as u8 {
        by_color.insert(biome_color(i), i);
    }
    let mut reps: Vec<Option<[f32; 5]>> = (0..biomes::BIOMES.len() as u8).map(biomes::representative).collect();
    let fallback = biomes::representative(biomes::b::SNOWY_PLAINS);
    for r in reps.iter_mut() {
        if r.is_none() {
            *r = fallback;
        }
    }

    let mut paint = Paint::new(r.len());
    let (mut unknown, mut unpaintable) = (0usize, 0usize);
    for j in 0..r.nz {
        for i in 0..r.nx {
            let idx = j * r.nx + i;
            let mut votes = [0u16; 64];
            let mut hsum = 0u32;
            for dz in 0..cell {
                for dx in 0..cell {
                    let o = ((pad + j * cell + dz) * w + pad + i * cell + dx) * 4;
                    let c = [bio.bytes[o], bio.bytes[o + 1], bio.bytes[o + 2]];
                    match by_color.get(&c) {
                        Some(&b) => votes[b as usize] += 1,
                        None => unknown += 1,
                    }
                    hsum += hgt.bytes[o] as u32;
                }
            }
            let b = (0..biomes::BIOMES.len()).max_by_key(|&b| votes[b]).unwrap();
            // representative() gives [t, h, c, e, w]
            let Some([t, hu, c, e, wei]) = reps[b] else {
                unpaintable += 1;
                continue;
            };
            for (ch, v) in [(CH_C, c), (CH_E, e), (CH_T, t), (CH_H, hu), (CH_W, wei), (CH_AY, hsum as f32 / (cell * cell) as f32)] {
                paint.mask[ch][idx] = 1.0;
                paint.value[ch][idx] = v;
            }
        }
    }
    p.paint = paint;
    p.padding = pad as i32;

    // how well does the rebuilt project reproduce the installed pack?
    let world = World::new(p);
    let maps = crate::novoatlas::build_maps(p, &world);
    let (mut same_b, mut near_h) = (0usize, 0usize);
    for k in 0..w * h {
        let o = k * 4;
        if by_color.get(&[bio.bytes[o], bio.bytes[o + 1], bio.bytes[o + 2]]) == Some(&maps.biomes[k]) {
            same_b += 1;
        }
        if (hgt.bytes[o] as i32 - maps.heights[k] as i32).abs() <= 3 {
            near_h += 1;
        }
    }
    let n = (w * h) as f64;
    Ok(format!(
        "imported {w}x{h} pack (padding {pad}); rebuilt export matches {:.1}% of biome pixels and {:.1}% of heights within 3 blocks ({unknown} unknown-colour px, {unpaintable} unpaintable cells)",
        same_b as f64 / n * 100.0,
        near_h as f64 / n * 100.0
    ))
}
