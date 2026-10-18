use crate::biomes::peaks_valleys;
use crate::model::*;

#[derive(Clone, Copy, Default)]
pub struct Cell {
    /// Climate after paint, before the edge-ocean ramp.
    pub clim: [f32; 5],
    /// Final climate: continentalness, erosion, temperature, humidity, weirdness.
    pub p: [f32; 5],
    pub pv: f32,
    pub biome: u8,
    pub height: f32,
    /// Painted height bias in blocks.
    pub bias: f32,
    /// Absolute-height paint: strength, and strength times Y.
    pub abs_m: f32,
    pub abs_mv: f32,
    /// Multiplier on the export's terrain detail (1 = as set in the World tab, 0 = smooth).
    pub detail: f32,
    /// Hillshade multiplier around 1.0.
    pub shade: f32,
}

pub struct World {
    pub cells: Vec<Cell>,
}

/// Bilinear blend of a cell's painted inputs at a block position.
pub struct Sample {
    pub clim: [f32; 5],
    pub bias: f32,
    pub abs_m: f32,
    pub abs_mv: f32,
    pub detail: f32,
}

/// Continentalness the map edge ramps toward (deep ocean).
pub const EDGE_C: f32 = -0.85;

/// 0 in the interior, rising smoothly to 1 at the outside of the padding (`pad` blocks past the region) over
/// `width` blocks. A width of 0 turns the ramp off.
pub fn edge_factor(r: &Region, pad: f32, x: f32, z: f32, width: f32) -> f32 {
    if width <= 0.0 {
        return 0.0;
    }
    let d = (x - (r.min_x as f32 - pad)).min(r.max_x() as f32 + pad - x).min(z - (r.min_z as f32 - pad)).min(r.max_z() as f32 + pad - z);
    let t = (1.0 - d / width).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

/// Blend the climate-driven height toward painted absolute heights.
pub fn with_absolute(terrain: f32, abs_m: f32, abs_mv: f32) -> f32 {
    terrain * (1.0 - abs_m) + abs_mv
}

/// Flatten the ground around spawn to `spawn_y - 1`, fading back to `h` over the pad's blend width.
pub fn spawn_pad(p: &Project, x: f32, z: f32, h: f32) -> f32 {
    if p.spawn_pad <= 0.0 {
        return h;
    }
    let d = ((x - p.spawn[0] as f32).powi(2) + (z - p.spawn[1] as f32).powi(2)).sqrt();
    let t = 1.0 - ((d - p.spawn_pad) / p.spawn_pad_blend.max(1.0)).clamp(0.0, 1.0);
    let t = t * t * (3.0 - 2.0 * t);
    h + ((p.spawn_y - 1) as f32 - h) * t
}

/// Whether a block position is on the spawn pad (flat core plus blend), which gets the stream-free
/// `winterheart:spawn_clearing` biome.
pub fn on_spawn_pad(p: &Project, x: f32, z: f32) -> bool {
    p.spawn_pad > 0.0 && (x - p.spawn[0] as f32).powi(2) + (z - p.spawn[1] as f32).powi(2) <= (p.spawn_pad + p.spawn_pad_blend.max(0.0)).powi(2)
}

/// Terrain height from climate: continentalness sets the base, erosion damps relief, and peaks-and-valleys
/// adds ridges and cuts valleys, mirroring how vanilla's terrain splines are driven.
pub fn terrain_height(c: f32, e: f32, pv: f32, bias: f32, sea: f32) -> f32 {
    const BASE: [(f32, f32); 8] = [
        (-1.2, 18.0),
        (-1.05, 24.0),
        (-0.455, 40.0),
        (-0.19, 56.0),
        (-0.11, 63.0),
        (0.03, 68.0),
        (0.3, 76.0),
        (1.0, 90.0),
    ];
    let mut base = BASE[BASE.len() - 1].1;
    for w in BASE.windows(2) {
        if c <= w[1].0 {
            let t = ((c - w[0].0) / (w[1].0 - w[0].0)).clamp(0.0, 1.0);
            base = w[0].1 + t * (w[1].1 - w[0].1);
            break;
        }
    }
    let relief = 1.0 - 0.88 * ((e + 1.0) * 0.5).clamp(0.0, 1.0);
    let land = ((c + 0.2) / 0.3).clamp(0.0, 1.0);
    let ridge = if pv >= 0.0 { pv * 62.0 } else { pv * 10.0 };
    base - 63.0 + sea + ridge * relief * land + bias
}

impl World {
    pub fn new(p: &Project) -> Self {
        let mut w = World { cells: vec![Cell::default(); p.region.len()] };
        w.recompute_cells(p);
        w
    }

    pub fn recompute_cells(&mut self, p: &Project) {
        let n = p.region.len();
        for i in 0..n {
            self.compute_cell(p, i);
        }
        self.shade_pass(p);
    }

    /// Recompute cells inside an inclusive rectangle (after painting).
    pub fn recompute_rect(&mut self, p: &Project, rect: CellRect) {
        let nx = p.region.nx;
        for j in rect.1..=rect.3 {
            for i in rect.0..=rect.2 {
                self.compute_cell(p, j * nx + i);
            }
        }
        self.shade_pass(p);
    }

    fn compute_cell(&mut self, p: &Project, idx: usize) {
        let clim: [f32; 5] = std::array::from_fn(|ch| p.paint.effective(ch, idx, p.canvas[ch]));
        let mut v = clim;
        let (i, j) = (idx % p.region.nx, idx / p.region.nx);
        let (x, z) = p.region.center(i, j);
        let edge = edge_factor(&p.region, p.padding as f32, x, z, p.edge_width);
        v[CH_C] += (EDGE_C - v[CH_C]) * edge;
        let [c, e, t, h, w] = v;
        let pv = peaks_valleys(w);
        let bias = p.paint.effective(CH_HB, idx, 0.0);
        let abs_m = p.paint.mask[CH_AY][idx];
        let cell = &mut self.cells[idx];
        cell.clim = clim;
        cell.bias = bias;
        cell.abs_m = abs_m;
        cell.abs_mv = abs_m * p.paint.value[CH_AY][idx];
        cell.detail = p.paint.effective(CH_DT, idx, 1.0);
        cell.p = v;
        cell.pv = pv;
        cell.biome = if on_spawn_pad(p, x, z) { crate::biomes::b::SPAWN_CLEARING } else { p.pick_biome(t, h, c, e, w) };
        cell.height = spawn_pad(p, x, z, with_absolute(terrain_height(c, e, pv, bias, p.sea_level), cell.abs_m, cell.abs_mv));
    }

    fn shade_pass(&mut self, p: &Project) {
        let (nx, nz) = (p.region.nx, p.region.nz);
        let at = |c: &Vec<Cell>, i: usize, j: usize| c[j.min(nz - 1) * nx + i.min(nx - 1)].height;
        for j in 0..nz {
            for i in 0..nx {
                let dh = (at(&self.cells, i.saturating_sub(1), j) - at(&self.cells, i + 1, j)) * 0.5
                    + (at(&self.cells, i, j.saturating_sub(1)) - at(&self.cells, i, j + 1)) * 0.5;
                self.cells[j * nx + i].shade = 1.0 + (dh * 0.07).clamp(-0.35, 0.35);
            }
        }
    }

    /// Bilinear blend of the painted inputs (climate before the edge ramp, bias, absolute height) at a block
    /// position. Positions outside the region clamp to the border cells, which is what extends the border outward.
    pub fn sample_smooth(&self, r: &Region, x: f32, z: f32) -> Sample {
        let fx = ((x - r.min_x as f32) / r.cell as f32 - 0.5).clamp(0.0, (r.nx - 1) as f32);
        let fz = ((z - r.min_z as f32) / r.cell as f32 - 0.5).clamp(0.0, (r.nz - 1) as f32);
        let (i0, j0) = (fx.floor() as usize, fz.floor() as usize);
        let (i1, j1) = ((i0 + 1).min(r.nx - 1), (j0 + 1).min(r.nz - 1));
        let (tx, tz) = (fx - i0 as f32, fz - j0 as f32);
        let corners = [
            (&self.cells[j0 * r.nx + i0], (1.0 - tx) * (1.0 - tz)),
            (&self.cells[j0 * r.nx + i1], tx * (1.0 - tz)),
            (&self.cells[j1 * r.nx + i0], (1.0 - tx) * tz),
            (&self.cells[j1 * r.nx + i1], tx * tz),
        ];
        let mut s = Sample { clim: [0.0; 5], bias: 0.0, abs_m: 0.0, abs_mv: 0.0, detail: 0.0 };
        for (c, wgt) in corners {
            for k in 0..5 {
                s.clim[k] += c.clim[k] * wgt;
            }
            s.bias += c.bias * wgt;
            s.abs_m += c.abs_m * wgt;
            s.abs_mv += c.abs_mv * wgt;
            s.detail += c.detail * wgt;
        }
        s
    }

    pub fn at_block(&self, p: &Project, x: f32, z: f32) -> Option<&Cell> {
        p.region.cell_at(x, z).map(|(i, j)| &self.cells[j * p.region.nx + i])
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::biomes;

    #[test]
    fn corridor_is_land_and_traversable() {
        let p = Project::new();
        let w = World::new(&p);
        for z in (-32..=520).step_by(8) {
            for x in (-40..=40).step_by(8) {
                let c = w.at_block(&p, x as f32, z as f32).unwrap();
                assert!(!biomes::is_ocean(c.biome), "ocean at {x},{z}: {}", biomes::biome(c.biome).id);
                assert!(c.height > p.sea_level, "below sea at {x},{z}: {}", c.height);
            }
        }
    }

    #[test]
    fn absolute_height_is_exact_and_raise_moves_it() {
        let mut p = Project::new();
        p.resize(512);
        p.spawn_pad = 0.0;
        p.paint.clear();
        let r = p.region.clone();
        p.paint.stamp(&r, (0.0, 0.0), (0.0, 0.0), 60.0, 1.0, 1.0, &Op::Set(&[(CH_AY, 140.0)]));
        let w = World::new(&p);
        assert!((w.at_block(&p, 0.0, 0.0).unwrap().height - 140.0).abs() < 1e-3);
        // a relative raise on height lifts both painted Y and the climate-driven ground around it
        let before_out = w.at_block(&p, 150.0, 0.0).unwrap().height;
        let mut s = Stroke::new(&p.paint);
        let rect = s.cover(&r, (0.0, 0.0), (0.0, 0.0), 200.0, 1.0, 1.0);
        p.paint.apply_relative(&r, &p.canvas, &s, rect, CH_HB, Rel::Add(12.0));
        let w = World::new(&p);
        assert!((w.at_block(&p, 0.0, 0.0).unwrap().height - 152.0).abs() < 1e-3);
        assert!((w.at_block(&p, 150.0, 0.0).unwrap().height - before_out - 12.0).abs() < 1e-3);
    }
}
