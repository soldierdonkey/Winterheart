//! Pixel rendering shared by the GUI and the PNG exporter (no window needed).
use crate::biomes::{self, TreeKind};
use crate::model::*;
use crate::sim::{Cell, World};

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum ViewMode {
    Biome,
    Height,
    Continentalness,
    Erosion,
    PeaksValleys,
    Temperature,
    Humidity,
}

pub const VIEW_MODES: [(ViewMode, &str); 7] = [
    (ViewMode::Biome, "Biome"),
    (ViewMode::Height, "Height"),
    (ViewMode::Continentalness, "Contin."),
    (ViewMode::Erosion, "Erosion"),
    (ViewMode::PeaksValleys, "PV"),
    (ViewMode::Temperature, "Temp"),
    (ViewMode::Humidity, "Humid"),
];

fn mul(c: [u8; 3], k: f32) -> [u8; 3] {
    [(c[0] as f32 * k).clamp(0.0, 255.0) as u8, (c[1] as f32 * k).clamp(0.0, 255.0) as u8, (c[2] as f32 * k).clamp(0.0, 255.0) as u8]
}

fn lerp3(a: [u8; 3], b: [u8; 3], t: f32) -> [u8; 3] {
    let t = t.clamp(0.0, 1.0);
    [
        (a[0] as f32 + (b[0] as f32 - a[0] as f32) * t) as u8,
        (a[1] as f32 + (b[1] as f32 - a[1] as f32) * t) as u8,
        (a[2] as f32 + (b[2] as f32 - a[2] as f32) * t) as u8,
    ]
}

fn height_color(h: f32, sea: f32) -> [u8; 3] {
    if h < sea {
        let t = ((sea - h) / 45.0).clamp(0.0, 1.0);
        return lerp3([70, 130, 200], [12, 30, 90], t);
    }
    let a = h - sea;
    let stops: [(f32, [u8; 3]); 5] = [(0.0, [190, 200, 130]), (12.0, [90, 150, 70]), (45.0, [150, 130, 80]), (85.0, [140, 130, 125]), (130.0, [250, 250, 250])];
    for w in stops.windows(2) {
        if a <= w[1].0 {
            return lerp3(w[0].1, w[1].1, (a - w[0].0) / (w[1].0 - w[0].0));
        }
    }
    stops[4].1
}

/// Diverging blue (-1) / dark (0) / red (+1).
fn diverge(v: f32) -> [u8; 3] {
    let v = v.clamp(-1.0, 1.0);
    if v < 0.0 {
        lerp3([30, 30, 40], [60, 120, 255], -v)
    } else {
        lerp3([30, 30, 40], [255, 110, 60], v)
    }
}

pub fn cell_color(cell: &Cell, mode: ViewMode, sea: f32, shade: bool) -> [u8; 3] {
    let k = if shade { cell.shade } else { 1.0 };
    match mode {
        ViewMode::Biome => {
            let b = biomes::biome(cell.biome);
            if biomes::is_ocean(cell.biome) || biomes::is_river(cell.biome) {
                mul(b.color, 0.85 + 0.3 * (cell.height / sea).clamp(0.3, 1.0))
            } else {
                mul(b.color, k)
            }
        }
        ViewMode::Height => mul(height_color(cell.height, sea), if cell.height < sea { 1.0 } else { k }),
        ViewMode::Continentalness => diverge(cell.p[CH_C]),
        ViewMode::Erosion => diverge(cell.p[CH_E]),
        ViewMode::PeaksValleys => diverge(cell.pv),
        ViewMode::Temperature => diverge(cell.p[CH_T]),
        ViewMode::Humidity => diverge(cell.p[CH_H]),
    }
}

/// Fill an RGBA buffer (nx * nz pixels, one per cell).
pub fn render_pixels(out: &mut [u8], p: &Project, w: &World, mode: ViewMode, shade: bool, paint_overlay: bool) {
    for (idx, cell) in w.cells.iter().enumerate() {
        let mut c = cell_color(cell, mode, p.sea_level, shade);
        if paint_overlay && p.paint.any_at(idx) {
            c = lerp3(c, [255, 0, 200], 0.28);
        }
        out[idx * 4..idx * 4 + 4].copy_from_slice(&[c[0], c[1], c[2], 255]);
    }
}

fn hash(i: usize, j: usize, k: u32) -> u32 {
    let mut h = (i as u32).wrapping_mul(0x9E37_79B1) ^ (j as u32).wrapping_mul(0x85EB_CA77) ^ k.wrapping_mul(0xC2B2_AE3D);
    h ^= h >> 15;
    h = h.wrapping_mul(0x2C1B_3C6D);
    h ^= h >> 12;
    h = h.wrapping_mul(0x297A_2D39);
    h ^ (h >> 15)
}

pub struct TreeDot {
    /// Offset inside the cell in 0..1.
    pub fx: f32,
    pub fy: f32,
    pub kind: TreeKind,
}

/// Deterministic tree "pixels" for a cell; up to two per cell, chance scaled by the biome's density.
pub fn tree_dots(i: usize, j: usize, cell: &Cell, sea: f32) -> [Option<TreeDot>; 2] {
    let mut out = [None, None];
    if cell.height < sea {
        return out;
    }
    if let Some((kind, density)) = biomes::biome(cell.biome).tree {
        for (k, slot) in out.iter_mut().enumerate() {
            let h = hash(i, j, k as u32);
            // two slots, so each carries half the density
            if (h & 0xFFFF) as f32 / 65535.0 < density * 0.62 {
                *slot = Some(TreeDot { fx: ((h >> 16) & 0xFF) as f32 / 255.0, fy: ((h >> 24) & 0xFF) as f32 / 255.0, kind });
            }
        }
    }
    out
}

pub fn tree_color(kind: TreeKind) -> [u8; 3] {
    match kind {
        TreeKind::Oak => [28, 82, 30],
        TreeKind::Conifer => [18, 62, 48],
        TreeKind::Birch => [92, 130, 52],
        TreeKind::DarkOak => [16, 48, 22],
        TreeKind::Jungle => [14, 100, 24],
        TreeKind::Acacia => [120, 120, 40],
        TreeKind::Cherry => [225, 120, 165],
        TreeKind::Mangrove => [48, 74, 40],
        TreeKind::Bamboo => [110, 175, 60],
    }
}

/// Render a scaled RGBA image (`scale` px per cell) with optional tree pixels, for exports and tests.
pub fn render_scaled(p: &Project, w: &World, mode: ViewMode, scale: usize, trees: bool, shade: bool) -> (Vec<u8>, usize, usize) {
    let (nx, nz) = (p.region.nx, p.region.nz);
    let (width, height) = (nx * scale, nz * scale);
    let mut base = vec![0u8; nx * nz * 4];
    render_pixels(&mut base, p, w, mode, shade, false);
    let mut out = vec![255u8; width * height * 4];
    for y in 0..height {
        for x in 0..width {
            let s = ((y / scale) * nx + x / scale) * 4;
            out[(y * width + x) * 4..(y * width + x) * 4 + 4].copy_from_slice(&base[s..s + 4]);
        }
    }
    if trees && scale >= 2 {
        for j in 0..nz {
            for i in 0..nx {
                for dot in tree_dots(i, j, &w.cells[j * nx + i], p.sea_level).into_iter().flatten() {
                    let px = (i * scale + (dot.fx * (scale as f32 - 1.0)) as usize).min(width - 1);
                    let py = (j * scale + (dot.fy * (scale as f32 - 1.0)) as usize).min(height - 1);
                    let c = tree_color(dot.kind);
                    out[(py * width + px) * 4..(py * width + px) * 4 + 4].copy_from_slice(&[c[0], c[1], c[2], 255]);
                }
            }
        }
    }
    (out, width, height)
}
