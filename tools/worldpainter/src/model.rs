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

/// Settings for the naturalize brush.
#[derive(Clone, Copy)]
pub struct Natural {
    /// 0..1 how strongly painted values/edges are smoothed toward their neighbours.
    pub blur: f32,
    /// 0..1 of each channel's range added as noise to painted values; also roughens painted edges.
    pub noise: f32,
    /// Noise wavelength in blocks.
    pub scale: f32,
    pub seed: u32,
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

    /// Blur painted values/masks and add noise inside a soft capsule. Returns the touched cell rectangle.
    #[allow(clippy::too_many_arguments)]
    pub fn naturalize(&mut self, region: &Region, a: (f32, f32), b: (f32, f32), radius: f32, hardness: f32, strength: f32, nat: Natural) -> (usize, usize, usize, usize) {
        let cell = region.cell as f32;
        let clampi = |v: f32, hi: usize| (v.floor().max(0.0) as usize).min(hi - 1);
        let i0 = clampi((a.0.min(b.0) - radius - region.min_x as f32) / cell, region.nx);
        let i1 = clampi((a.0.max(b.0) + radius - region.min_x as f32) / cell, region.nx);
        let j0 = clampi((a.1.min(b.1) - radius - region.min_z as f32) / cell, region.nz);
        let j1 = clampi((a.1.max(b.1) + radius - region.min_z as f32) / cell, region.nz);
        let (dx, dz) = (b.0 - a.0, b.1 - a.1);
        let len2 = dx * dx + dz * dz;
        let solid = radius * hardness.clamp(0.0, 1.0);
        let at = |i: isize, j: isize| (j.clamp(0, region.nz as isize - 1) as usize) * region.nx + i.clamp(0, region.nx as isize - 1) as usize;
        let scale = nat.scale.max(8.0) as f64;

        for ch in 0..NCH {
            let perlin = crate::noise::Perlin::new(nat.seed as u64 * 31 + ch as u64 * 977);
            let range = CH_RANGE[ch].1 - CH_RANGE[ch].0;
            // snapshot rows i0-1..=i1+1, j0-1..=j1+1 (clamped through `at`)
            let (w, h) = (i1 - i0 + 3, j1 - j0 + 3);
            let (mut sm, mut sv) = (vec![0.0f32; w * h], vec![0.0f32; w * h]);
            for jj in 0..h {
                for ii in 0..w {
                    let k = at(i0 as isize + ii as isize - 1, j0 as isize + jj as isize - 1);
                    sm[jj * w + ii] = self.mask[ch][k];
                    sv[jj * w + ii] = self.value[ch][k];
                }
            }
            for j in j0..=j1 {
                for i in i0..=i1 {
                    let (x, z) = region.center(i, j);
                    let t = if len2 > 0.0 { (((x - a.0) * dx + (z - a.1) * dz) / len2).clamp(0.0, 1.0) } else { 0.0 };
                    let d = ((x - (a.0 + t * dx)).powi(2) + (z - (a.1 + t * dz)).powi(2)).sqrt();
                    let f = if d <= solid { 1.0 } else if d >= radius { 0.0 } else {
                        let u = 1.0 - (d - solid) / (radius - solid).max(0.001);
                        u * u * (3.0 - 2.0 * u)
                    };
                    let wgt = f * strength;
                    if wgt <= 0.0 {
                        continue;
                    }
                    let (li, lj) = (i - i0 + 1, j - j0 + 1);
                    let (mut msum, mut vsum, mut wsum) = (0.0f32, 0.0f32, 0.0f32);
                    for (oj, row) in [[1.0f32, 2.0, 1.0], [2.0, 4.0, 2.0], [1.0, 2.0, 1.0]].iter().enumerate() {
                        for (oi, k) in row.iter().enumerate() {
                            let q = (lj + oj - 1) * w + (li + oi - 1);
                            msum += sm[q] * k;
                            vsum += sv[q] * sm[q] * k;
                            wsum += k;
                        }
                    }
                    let bm = msum / wsum;
                    let idx = j * region.nx + i;
                    let (m, v) = (self.mask[ch][idx], self.value[ch][idx]);
                    let mix = (wgt * nat.blur).clamp(0.0, 1.0);
                    let mut nm = m + (bm - m) * mix;
                    let mut nv = if msum > 1e-4 {
                        let bv = vsum / msum;
                        if m < 0.02 { bv } else { v + (bv - v) * mix }
                    } else {
                        v
                    };
                    if nm > 0.0 && nat.noise > 0.0 {
                        let n = perlin.fbm(x as f64 / scale, z as f64 / scale, 3, 0.5, 2.0) as f32 * 2.0;
                        nv += n * nat.noise * range * 0.5 * wgt;
                        // roughen the fringe: noise modulates partially covered cells
                        if nm < 0.98 {
                            nm *= 1.0 + n * nat.noise * 2.5 * wgt;
                        }
                        nv = nv.clamp(CH_RANGE[ch].0, CH_RANGE[ch].1);
                    }
                    nm = nm.clamp(0.0, 1.0);
                    self.mask[ch][idx] = if nm < 0.02 { 0.0 } else { nm };
                    self.value[ch][idx] = nv;
                }
            }
        }
        (i0, j0, i1, j1)
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
    /// The escape route from spawn to the target, and how its corridor is painted.
    #[serde(default)]
    pub path: PathGuide,
    /// Pre-`path` projects stored only an offset from spawn and a radius; read once, then dropped.
    #[serde(default, skip_serializing)]
    guide_offset: Option<[i32; 2]>,
    #[serde(default, skip_serializing)]
    guide_radius: Option<i32>,
    /// Terrain ramps down to deep ocean over this many blocks at the map edge.
    #[serde(default = "default_edge")]
    pub edge_width: f32,
    /// Fine-scale bumpiness added only in the exported heightmap (blocks, wavelength in blocks).
    #[serde(default = "default_detail_amp")]
    pub detail_amp: f32,
    #[serde(default = "default_detail_scale")]
    pub detail_scale: f32,
    /// Octaves of the fine detail layer (each halves the wavelength), and how much each keeps.
    #[serde(default = "default_detail_octaves")]
    pub detail_octaves: u32,
    #[serde(default = "default_detail_persistence")]
    pub detail_persistence: f32,
    /// 0..1 blend toward ridged noise (sharper creases instead of rounded bumps).
    #[serde(default = "default_detail_ridged")]
    pub detail_ridged: f32,
    /// Mid-scale undulation layer (hills/gullies between the macro noise and the fine detail).
    #[serde(default = "default_mid_amp")]
    pub mid_amp: f32,
    #[serde(default = "default_mid_scale")]
    pub mid_scale: f32,
    /// 0..1: how much extra roughness low-erosion (rugged) terrain gets.
    #[serde(default = "default_rough_by_erosion")]
    pub rough_by_erosion: f32,
    /// Remap warm biomes to snowy ones (the pack's overworld is snowy-only).
    #[serde(default = "default_true")]
    pub winter_palette: bool,
}

fn default_detail_octaves() -> u32 {
    5
}
fn default_detail_persistence() -> f32 {
    0.55
}
fn default_detail_ridged() -> f32 {
    0.25
}
fn default_mid_amp() -> f32 {
    8.0
}
fn default_mid_scale() -> f32 {
    110.0
}
fn default_rough_by_erosion() -> f32 {
    0.6
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
            path: PathGuide::default(),
            guide_offset: None,
            guide_radius: None,
            edge_width: default_edge(),
            detail_amp: 6.0,
            detail_scale: default_detail_scale(),
            detail_octaves: default_detail_octaves(),
            detail_persistence: default_detail_persistence(),
            detail_ridged: default_detail_ridged(),
            mid_amp: default_mid_amp(),
            mid_scale: default_mid_scale(),
            rough_by_erosion: default_rough_by_erosion(),
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

    /// Convert a pre-`path` project's single guide offset into a one-point path.
    pub fn migrate(&mut self) {
        // files that still have `guide_offset` were saved before `path` existed, so `path` is only a default
        if let Some([ox, oz]) = self.guide_offset.take() {
            self.path.points = vec![[self.spawn[0] + ox, self.spawn[1] + oz]];
        }
        if let Some(r) = self.guide_radius.take() {
            self.path.target_radius = r;
        }
        if self.path.points.is_empty() {
            self.path.points = PathGuide::default().points;
        }
    }

    /// Spawn followed by every waypoint; the last entry is the escape target.
    pub fn path_points(&self) -> Vec<(f32, f32)> {
        std::iter::once(self.spawn).chain(self.path.points.iter().copied()).map(|[x, z]| (x as f32, z as f32)).collect()
    }

    /// Paint (or erase) the path's corridor: a capsule along each segment plus clearings at spawn and the target.
    /// Returns the touched cell rectangle.
    pub fn paint_path(&mut self, erase: bool) -> (usize, usize, usize, usize) {
        let g = self.path.clone();
        let land = g.land();
        let op = if erase { Op::Erase(None) } else { Op::Set(&land) };
        // erase with a hard edge over the same radius, so the faded fringe is removed too
        let hardness = if erase { 1.0 } else { g.hardness };
        let pts = self.path_points();
        let mut rect = (usize::MAX, usize::MAX, 0, 0);
        let mut grow = |r: (usize, usize, usize, usize)| rect = (rect.0.min(r.0), rect.1.min(r.1), rect.2.max(r.2), rect.3.max(r.3));
        for w in pts.windows(2) {
            grow(self.paint.stamp(&self.region, w[0], w[1], g.width, hardness, 1.0, &op));
        }
        let (spawn, target) = (pts[0], pts[pts.len() - 1]);
        if g.spawn_clearing > 0.0 {
            grow(self.paint.stamp(&self.region, spawn, spawn, g.spawn_clearing, hardness, 1.0, &op));
        }
        if g.target_clearing > 0.0 {
            grow(self.paint.stamp(&self.region, target, target, g.target_clearing, hardness, 1.0, &op));
        }
        rect
    }

    /// Default corridor: paint the path with its current settings.
    pub fn apply_corridor_preset(&mut self) {
        self.paint_path(false);
    }
}

/// The planned escape route. Points are absolute block coordinates; spawn is the implicit first point.
#[derive(Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct PathGuide {
    /// Waypoints after spawn; the last one is the escape target.
    pub points: Vec<[i32; 2]>,
    /// Accepted distance from the target (the ring drawn on the map and written to structures.json).
    pub target_radius: i32,
    /// Corridor half-width in blocks.
    pub width: f32,
    /// 0..1: fraction of the width painted at full strength before the edge fades.
    pub hardness: f32,
    /// Radius of the round clearing painted at spawn / the target (0 = none).
    pub spawn_clearing: f32,
    pub target_clearing: f32,
    /// Climate the corridor is painted with: gentle, flat land by default.
    pub continentalness: f32,
    pub erosion: f32,
    pub weirdness: f32,
    /// Blocks added to the height; +8 keeps the export's detail noise above sea level.
    pub lift: f32,
}

impl Default for PathGuide {
    fn default() -> Self {
        PathGuide {
            points: vec![[0, 400]],
            target_radius: 100,
            width: 70.0,
            hardness: 0.55,
            spawn_clearing: 90.0,
            target_clearing: 120.0,
            continentalness: 0.12,
            erosion: 0.25,
            weirdness: 0.3,
            lift: 8.0,
        }
    }
}

impl PathGuide {
    pub fn land(&self) -> [(usize, f32); 4] {
        [(CH_C, self.continentalness), (CH_E, self.erosion), (CH_W, self.weirdness), (CH_HB, self.lift)]
    }

    pub fn target(&self, spawn: [i32; 2]) -> [i32; 2] {
        self.points.last().copied().unwrap_or(spawn)
    }

    /// Total length in blocks from spawn through every waypoint.
    pub fn length(&self, spawn: [i32; 2]) -> f32 {
        let mut prev = spawn;
        let mut len = 0.0;
        for &p in &self.points {
            len += (((p[0] - prev[0]).pow(2) + (p[1] - prev[1]).pow(2)) as f32).sqrt();
            prev = p;
        }
        len
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn naturalize_blurs_edges_and_stays_in_range() {
        let mut p = Project::new();
        p.resize(1024);
        let r = p.region.clone();
        // a hard-edged dab, so blur has a sharp edge to spread past
        p.paint.stamp(&r, (200.0, -200.0), (200.0, -200.0), 60.0, 1.0, 1.0, &Op::Set(&[(CH_C, 0.6), (CH_HB, 20.0)]));
        let before = p.paint.clone();
        let nat = Natural { blur: 0.8, noise: 0.15, scale: 50.0, seed: 3 };
        p.paint.naturalize(&r, (0.0, 0.0), (0.0, 0.0), 2000.0, 1.0, 1.0, nat);
        let (mut changed, mut spread) = (0, 0);
        for ch in 0..NCH {
            for i in 0..r.len() {
                let (m, v) = (p.paint.mask[ch][i], p.paint.value[ch][i]);
                assert!((0.0..=1.0).contains(&m), "mask {m}");
                assert!(v >= CH_RANGE[ch].0 - 1e-3 && v <= CH_RANGE[ch].1 + 1e-3, "value {v}");
                if (m - before.mask[ch][i]).abs() > 1e-4 {
                    changed += 1;
                }
                if before.mask[ch][i] == 0.0 && m > 0.0 {
                    spread += 1;
                }
            }
        }
        assert!(changed > 0 && spread > 0, "changed {changed}, spread {spread}");
    }
}

#[cfg(test)]
mod path_tests {
    use super::*;

    #[test]
    fn old_guide_offset_migrates_to_a_one_point_path() {
        let mut v = serde_json::to_value(Project::new()).unwrap();
        let o = v.as_object_mut().unwrap();
        o.remove("path");
        o.insert("guide_offset".into(), serde_json::json!([30, 600]));
        o.insert("guide_radius".into(), serde_json::json!(75));
        let mut p: Project = serde_json::from_value(v).unwrap();
        p.migrate();
        assert_eq!(p.path.points, vec![[30, 600]]);
        assert_eq!(p.path.target_radius, 75);
        let saved = serde_json::to_value(&p).unwrap();
        assert!(saved.get("guide_offset").is_none() && saved.get("path").is_some());
    }

    #[test]
    fn corridor_follows_bends_and_erases_cleanly() {
        let mut p = Project::new();
        p.paint.clear();
        p.path.points = vec![[300, 0], [300, 300]];
        p.path.spawn_clearing = 0.0;
        p.path.target_clearing = 0.0;
        p.paint_path(false);
        let idx = |x: f32, z: f32| {
            let (i, j) = p.region.cell_at(x, z).unwrap();
            j * p.region.nx + i
        };
        let (on_bend, off_path) = (idx(300.0, 150.0), idx(0.0, 300.0));
        assert!(p.paint.mask[CH_C][on_bend] > 0.9);
        assert!((p.paint.value[CH_HB][on_bend] - p.path.lift).abs() < 1e-3);
        assert_eq!(p.paint.mask[CH_C][off_path], 0.0, "a straight spawn-target line would have painted here");
        assert!((p.path.length(p.spawn) - 600.0).abs() < 1e-3);
        p.paint_path(true);
        assert!(p.paint.mask.iter().all(|m| m.iter().all(|v| *v == 0.0)));
    }
}
