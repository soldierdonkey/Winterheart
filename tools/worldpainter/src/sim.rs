use crate::biomes::peaks_valleys;
use crate::model::*;
use crate::noise::Perlin;

#[derive(Clone, Copy, Default)]
pub struct Cell {
    /// Final climate after paint: continentalness, erosion, temperature, humidity, weirdness.
    pub p: [f32; 5],
    pub pv: f32,
    pub biome: u8,
    pub height: f32,
    /// Painted height bias in blocks.
    pub bias: f32,
    /// Hillshade multiplier around 1.0.
    pub shade: f32,
}

pub struct World {
    /// Noise before paint, per climate channel.
    pub raw: [Vec<f32>; 5],
    warp: Vec<(f32, f32)>,
    pub cells: Vec<Cell>,
}

/// Terrain height from climate: continentalness sets the base, erosion damps relief, and peaks-and-valleys
/// adds ridges and cuts valleys, mirroring how vanilla's terrain splines are driven.
/// Continentalness the map edge ramps toward (deep ocean).
pub const EDGE_C: f32 = -0.85;

/// 0 in the interior, rising smoothly to 1 at the region boundary over `width` blocks.
pub fn edge_factor(r: &Region, x: f32, z: f32, width: f32) -> f32 {
    let d = (x - r.min_x as f32).min(r.max_x() as f32 - x).min(z - r.min_z as f32).min(r.max_z() as f32 - z);
    let t = (1.0 - d / width.max(1.0)).clamp(0.0, 1.0);
    t * t * (3.0 - 2.0 * t)
}

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
        let n = p.region.len();
        let mut w = World { raw: std::array::from_fn(|_| vec![0.0; n]), warp: vec![(0.0, 0.0); n], cells: vec![Cell::default(); n] };
        w.recompute_all(p);
        w
    }

    pub fn recompute_all(&mut self, p: &Project) {
        self.recompute_warp(p);
        for ch in 0..5 {
            self.recompute_raw(p, ch);
        }
        self.recompute_cells(p);
    }

    fn recompute_warp(&mut self, p: &Project) {
        let r = &p.region;
        let (wx, wz) = (Perlin::new(p.seed as u64 ^ 0xABCD), Perlin::new(p.seed as u64 ^ 0x5151));
        let s = p.warp_scale.max(1.0) as f64;
        for j in 0..r.nz {
            for i in 0..r.nx {
                let (x, z) = r.center(i, j);
                let (x, z) = (x as f64 / s, z as f64 / s);
                self.warp[j * r.nx + i] = (
                    (wx.fbm(x, z, 3, 0.5, 2.0) * 2.0) as f32 * p.warp_strength,
                    (wz.fbm(x, z, 3, 0.5, 2.0) * 2.0) as f32 * p.warp_strength,
                );
            }
        }
    }

    /// Rebuild the raw noise for one channel (after editing its settings).
    pub fn recompute_raw(&mut self, p: &Project, ch: usize) {
        let r = &p.region;
        let cfg = &p.noise[ch];
        let perlin = cfg.perlin(p.seed, ch);
        for j in 0..r.nz {
            for i in 0..r.nx {
                let (x, z) = r.center(i, j);
                let (wx, wz) = self.warp[j * r.nx + i];
                self.raw[ch][j * r.nx + i] = cfg.sample(&perlin, (x + wx) as f64, (z + wz) as f64).clamp(CH_RANGE[ch].0, CH_RANGE[ch].1);
            }
        }
    }

    pub fn recompute_cells(&mut self, p: &Project) {
        let n = p.region.len();
        for i in 0..n {
            self.compute_cell(p, i);
        }
        self.shade_pass(p);
    }

    /// Recompute cells inside an inclusive rectangle (after painting).
    pub fn recompute_rect(&mut self, p: &Project, rect: (usize, usize, usize, usize)) {
        let nx = p.region.nx;
        for j in rect.1..=rect.3 {
            for i in rect.0..=rect.2 {
                self.compute_cell(p, j * nx + i);
            }
        }
        self.shade_pass(p);
    }

    fn compute_cell(&mut self, p: &Project, idx: usize) {
        let mut v = [0.0f32; 5];
        for ch in 0..5 {
            let m = p.paint.mask[ch][idx];
            v[ch] = self.raw[ch][idx] + (p.paint.value[ch][idx] - self.raw[ch][idx]) * m;
        }
        let (i, j) = (idx % p.region.nx, idx / p.region.nx);
        let (x, z) = p.region.center(i, j);
        let edge = edge_factor(&p.region, x, z, p.edge_width);
        v[CH_C] += (EDGE_C - v[CH_C]) * edge;
        let [c, e, t, h, w] = v;
        let pv = peaks_valleys(w);
        let bias = p.paint.mask[CH_HB][idx] * p.paint.value[CH_HB][idx];
        let cell = &mut self.cells[idx];
        cell.bias = bias;
        cell.p = v;
        cell.pv = pv;
        cell.biome = p.pick_biome(t, h, c, e, w);
        cell.height = terrain_height(c, e, pv, bias, p.sea_level);
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

    /// Bilinear blend of the climate parameters (and painted bias) at an arbitrary block position.
    pub fn sample_smooth(&self, r: &Region, x: f32, z: f32) -> ([f32; 5], f32) {
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
        let mut p = [0.0f32; 5];
        let mut bias = 0.0;
        for (c, wgt) in corners {
            for k in 0..5 {
                p[k] += c.p[k] * wgt;
            }
            bias += c.bias * wgt;
        }
        (p, bias)
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
}
