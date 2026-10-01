use crate::noise::NoiseCfg;
use serde::{Deserialize, Serialize};

pub const NCH: usize = 6;
/// Channel order: the five vanilla climate noises, then a height bias in blocks.
pub const CH_C: usize = 0;
pub const CH_E: usize = 1;
pub const CH_T: usize = 2;
pub const CH_H: usize = 3;
pub const CH_W: usize = 4;
pub const CH_HB: usize = 5;
pub const CH_NAMES: [&str; NCH] = ["Continentalness", "Erosion", "Temperature", "Humidity", "Weirdness", "Height bias"];
pub const CH_RANGE: [(f32, f32); NCH] = [(-1.2, 1.0), (-1.0, 1.0), (-1.0, 1.0), (-1.0, 1.0), (-1.0, 1.0), (-64.0, 64.0)];

#[derive(Clone, Serialize, Deserialize)]
pub struct Region {
    pub min_x: i32,
    pub min_z: i32,
    pub nx: usize,
    pub nz: usize,
    /// Blocks per macro cell.
    pub cell: i32,
}

impl Region {
    /// A square region centred on the world origin, as NovoAtlas requires.
    pub fn centered(size: i32, cell: i32) -> Self {
        let n = (size / cell) as usize;
        Region { min_x: -size / 2, min_z: -size / 2, nx: n, nz: n, cell }
    }
    pub fn is_centered(&self) -> bool {
        self.min_x == -(self.nx as i32 * self.cell) / 2 && self.min_z == -(self.nz as i32 * self.cell) / 2
    }
    pub fn len(&self) -> usize {
        self.nx * self.nz
    }
    pub fn center(&self, i: usize, j: usize) -> (f32, f32) {
        (
            self.min_x as f32 + (i as f32 + 0.5) * self.cell as f32,
            self.min_z as f32 + (j as f32 + 0.5) * self.cell as f32,
        )
    }
    pub fn cell_at(&self, x: f32, z: f32) -> Option<(usize, usize)> {
        let i = ((x - self.min_x as f32) / self.cell as f32).floor();
        let j = ((z - self.min_z as f32) / self.cell as f32).floor();
        if i < 0.0 || j < 0.0 || i >= self.nx as f32 || j >= self.nz as f32 {
            None
        } else {
            Some((i as usize, j as usize))
        }
    }
    pub fn max_x(&self) -> i32 {
        self.min_x + self.nx as i32 * self.cell
    }
    pub fn max_z(&self) -> i32 {
        self.min_z + self.nz as i32 * self.cell
    }
}

/// Hand-painted overrides: per channel a strength mask (0 = pure noise, 1 = fully painted) and a value.
#[derive(Clone)]
pub struct Paint {
    pub mask: [Vec<f32>; NCH],
    pub value: [Vec<f32>; NCH],
}

#[derive(Serialize, Deserialize)]
struct PaintSparse {
    n: usize,
    cells: Vec<(u32, [f32; NCH], [f32; NCH])>,
}

impl From<Paint> for PaintSparse {
    fn from(p: Paint) -> Self {
        let n = p.mask[0].len();
        let mut cells = Vec::new();
        for i in 0..n {
            if (0..NCH).any(|c| p.mask[c][i] > 0.0) {
                cells.push((
                    i as u32,
                    std::array::from_fn(|c| p.value[c][i]),
                    std::array::from_fn(|c| p.mask[c][i]),
                ));
            }
        }
        PaintSparse { n, cells }
    }
}

impl From<PaintSparse> for Paint {
    fn from(s: PaintSparse) -> Self {
        let mut p = Paint::new(s.n);
        for (i, v, m) in s.cells {
            for c in 0..NCH {
                p.value[c][i as usize] = v[c];
                p.mask[c][i as usize] = m[c];
            }
        }
        p
    }
}

// serde glue: Paint serialises through its sparse form.
impl Serialize for Paint {
    fn serialize<S: serde::Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        PaintSparse::from(self.clone()).serialize(s)
    }
}
impl<'de> Deserialize<'de> for Paint {
    fn deserialize<D: serde::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        PaintSparse::deserialize(d).map(Paint::from)
    }
}

pub enum Op<'a> {
    /// Paint these `(channel, value)` pairs.
    Set(&'a [(usize, f32)]),
    /// Remove paint, optionally only on one channel.
    Erase(Option<usize>),
}

impl Paint {
    pub fn new(n: usize) -> Self {
        Self {
            mask: std::array::from_fn(|_| vec![0.0; n]),
            value: std::array::from_fn(|_| vec![0.0; n]),
        }
    }

    pub fn clear(&mut self) {
        for c in 0..NCH {
            self.mask[c].fill(0.0);
        }
    }

    pub fn any_at(&self, i: usize) -> bool {
        (0..NCH).any(|c| self.mask[c][i] > 0.05)
    }

    /// Stamp a soft capsule from `a` to `b` (a dab when `a == b`). Returns the touched cell rectangle
    /// `(i0, j0, i1, j1)`, inclusive.
    #[allow(clippy::too_many_arguments)]
    pub fn stamp(
        &mut self,
        region: &Region,
        a: (f32, f32),
        b: (f32, f32),
        radius: f32,
        hardness: f32,
        strength: f32,
        op: &Op,
    ) -> (usize, usize, usize, usize) {
        let cell = region.cell as f32;
        let clampi = |v: f32, hi: usize| (v.floor().max(0.0) as usize).min(hi - 1);
        let i0 = clampi((a.0.min(b.0) - radius - region.min_x as f32) / cell, region.nx);
        let i1 = clampi((a.0.max(b.0) + radius - region.min_x as f32) / cell, region.nx);
        let j0 = clampi((a.1.min(b.1) - radius - region.min_z as f32) / cell, region.nz);
        let j1 = clampi((a.1.max(b.1) + radius - region.min_z as f32) / cell, region.nz);
        let (dx, dz) = (b.0 - a.0, b.1 - a.1);
        let len2 = dx * dx + dz * dz;
        let solid = radius * hardness.clamp(0.0, 1.0);

        for j in j0..=j1 {
            for i in i0..=i1 {
                let (x, z) = region.center(i, j);
                let t = if len2 > 0.0 { (((x - a.0) * dx + (z - a.1) * dz) / len2).clamp(0.0, 1.0) } else { 0.0 };
                let d = ((x - (a.0 + t * dx)).powi(2) + (z - (a.1 + t * dz)).powi(2)).sqrt();
                let f = if d <= solid {
                    1.0
                } else if d >= radius {
                    0.0
                } else {
                    let u = 1.0 - (d - solid) / (radius - solid).max(0.001);
                    u * u * (3.0 - 2.0 * u)
                };
                let w = f * strength;
                if w <= 0.0 {
                    continue;
                }
                let idx = j * region.nx + i;
                match op {
                    Op::Set(targets) => {
                        for &(ch, val) in targets.iter() {
                            let m = self.mask[ch][idx];
                            let nm = w + m * (1.0 - w);
                            self.value[ch][idx] = (val * w + self.value[ch][idx] * m * (1.0 - w)) / nm;
                            self.mask[ch][idx] = nm;
                        }
                    }
                    Op::Erase(only) => {
                        for ch in 0..NCH {
                            if only.map_or(true, |o| o == ch) {
                                let m = self.mask[ch][idx] * (1.0 - w);
                                self.mask[ch][idx] = if m < 0.02 { 0.0 } else { m };
                            }
                        }
                    }
                }
            }
        }
        (i0, j0, i1, j1)
    }
}

#[derive(Clone, Serialize, Deserialize)]
pub struct StructType {
    pub name: String,
    /// Resource id of the structure / template (e.g. `winterheart:escape_camp`).
    pub id: String,
    pub w: i32,
    pub d: i32,
    pub color: [u8; 3],
}

#[derive(Clone, Serialize, Deserialize)]
pub struct StructInst {
    pub ty: usize,
    pub x: i32,
    pub z: i32,
    /// Quarter turns clockwise.
    pub rot: u8,
}

impl StructInst {
    pub fn footprint(&self, types: &[StructType]) -> (i32, i32) {
        let t = &types[self.ty.min(types.len() - 1)];
        if self.rot % 2 == 1 {
            (t.d, t.w)
        } else {
            (t.w, t.d)
        }
    }
}

#[derive(Clone, Serialize, Deserialize)]
pub struct Project {
    pub seed: u32,
    pub sea_level: f32,
    pub region: Region,
    /// Continentalness, erosion, temperature, humidity, weirdness.
    pub noise: [NoiseCfg; 5],
    pub warp_strength: f32,
    pub warp_scale: f32,
    pub paint: Paint,
    pub types: Vec<StructType>,
    pub structures: Vec<StructInst>,
    pub spawn: [i32; 2],
    /// Offset from spawn the escape structure should land at, and the accepted radius.
    pub guide_offset: [i32; 2],
    pub guide_radius: i32,
    /// Terrain ramps down to deep ocean over this many blocks at the map edge.
    #[serde(default = "default_edge")]
    pub edge_width: f32,
    /// Fine-scale bumpiness added only in the exported heightmap (blocks, wavelength in blocks).
    #[serde(default = "default_detail_amp")]
    pub detail_amp: f32,
    #[serde(default = "default_detail_scale")]
    pub detail_scale: f32,
    /// Remap warm biomes to snowy ones (the pack's overworld is snowy-only).
    #[serde(default = "default_true")]
    pub winter_palette: bool,
}

fn default_true() -> bool {
    true
}

fn default_edge() -> f32 {
    256.0
}
fn default_detail_amp() -> f32 {
    4.0
}
fn default_detail_scale() -> f32 {
    30.0
}

impl Project {
    pub fn new() -> Self {
        let region = Region::centered(3072, 8);
        let n = region.len();
        let mut p = Project {
            seed: 1337,
            sea_level: 63.0,
            region,
            noise: [
                NoiseCfg::new(900.0, 4, 2.4, 0.0),   // continentalness
                NoiseCfg::new(520.0, 4, 2.2, 0.0),   // erosion
                NoiseCfg::new(1100.0, 3, 2.4, -0.3), // temperature (cold bias for Winterheart)
                NoiseCfg::new(800.0, 3, 2.4, 0.0),   // humidity
                NoiseCfg::new(480.0, 4, 2.2, 0.0),   // weirdness
            ],
            warp_strength: 140.0,
            warp_scale: 350.0,
            paint: Paint::new(n),
            types: vec![
                StructType { name: "Escape Camp".into(), id: "winterheart:escape_camp".into(), w: 48, d: 48, color: [255, 120, 40] },
                StructType { name: "Outpost".into(), id: "winterheart:outpost".into(), w: 24, d: 24, color: [255, 220, 60] },
                StructType { name: "Ruin".into(), id: "winterheart:ruin".into(), w: 16, d: 16, color: [190, 190, 255] },
            ],
            structures: vec![],
            spawn: [0, 0],
            guide_offset: [0, 400],
            guide_radius: 100,
            edge_width: default_edge(),
            detail_amp: default_detail_amp(),
            detail_scale: default_detail_scale(),
            winter_palette: true,
        };
        p.apply_corridor_preset();
        p.structures.push(StructInst { ty: 0, x: 0, z: 400, rot: 0 });
        p
    }

    pub fn pick_biome(&self, t: f32, h: f32, c: f32, e: f32, w: f32) -> u8 {
        let b = crate::biomes::pick(t, h, c, e, w);
        if self.winter_palette {
            crate::biomes::winterize(b)
        } else {
            b
        }
    }

    /// Change the map size (blocks, square, centred on the origin), keeping paint where it still fits.
    pub fn resize(&mut self, size: i32) {
        let new = Region::centered(size, self.region.cell);
        let mut paint = Paint::new(new.len());
        for j in 0..new.nz {
            for i in 0..new.nx {
                let (x, z) = new.center(i, j);
                if let Some((oi, oj)) = self.region.cell_at(x, z) {
                    let (a, b) = (oj * self.region.nx + oi, j * new.nx + i);
                    for c in 0..NCH {
                        paint.mask[c][b] = self.paint.mask[c][a];
                        paint.value[c][b] = self.paint.value[c][a];
                    }
                }
            }
        }
        self.region = new;
        self.paint = paint;
    }

    /// Placeholder traversable corridor south (+Z) of spawn, ending in a clearing at the escape site.
    pub fn apply_corridor_preset(&mut self) {
        let land = [(CH_C, 0.12), (CH_E, 0.25), (CH_W, 0.3)];
        let [sx, sz] = self.spawn;
        let [ox, oz] = self.guide_offset;
        let (a, b) = ((sx as f32, sz as f32 - 40.0), ((sx + ox) as f32, (sz + oz) as f32 + 120.0));
        self.paint.stamp(&self.region, a, b, 70.0, 0.55, 1.0, &Op::Set(&land));
        let site = ((sx + ox) as f32, (sz + oz) as f32);
        self.paint.stamp(&self.region, site, site, 120.0, 0.6, 1.0, &Op::Set(&land));
        self.paint.stamp(&self.region, (sx as f32, sz as f32), (sx as f32, sz as f32), 90.0, 0.6, 1.0, &Op::Set(&land));
    }
}
