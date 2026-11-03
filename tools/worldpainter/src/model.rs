use crate::noise::Perlin;
use serde::{Deserialize, Serialize};

pub const NCH: usize = 8;
/// Channel order: the five vanilla climate noises, a height bias in blocks, an absolute height (Y above 0), then a
/// multiplier on the export-only terrain detail.
pub const CH_C: usize = 0;
pub const CH_E: usize = 1;
pub const CH_T: usize = 2;
pub const CH_H: usize = 3;
pub const CH_W: usize = 4;
pub const CH_HB: usize = 5;
pub const CH_AY: usize = 6;
pub const CH_DT: usize = 7;
pub const CH_NAMES: [&str; NCH] = ["Continentalness", "Erosion", "Temperature", "Humidity", "Weirdness", "Height bias", "Absolute Y", "Detail"];
pub const CH_RANGE: [(f32, f32); NCH] = [(-1.2, 1.0), (-1.0, 1.0), (-1.0, 1.0), (-1.0, 1.0), (-1.0, 1.0), (-128.0, 128.0), (0.0, 255.0), (0.0, 2.0)];
/// Blocks that a naturalize noise of 1.0 moves the height by.
const HEIGHT_NOISE_SPAN: f32 = 48.0;

/// What an unpainted cell holds: the canvas for climate, no bias, full detail.
pub fn channel_base(canvas: &[f32; 5], ch: usize) -> f32 {
    match ch {
        0..=4 => canvas[ch],
        CH_DT => 1.0,
        _ => 0.0,
    }
}

/// Inclusive cell rectangle `(i0, j0, i1, j1)`.
pub type CellRect = (usize, usize, usize, usize);

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
    pub fn all(&self) -> CellRect {
        (0, 0, self.nx - 1, self.nz - 1)
    }
}

/// Soft capsule from `a` to `b` (a dab when `a == b`): calls `f(idx, falloff)` for every cell it reaches and
/// returns the touched cell rectangle.
pub fn capsule(region: &Region, a: (f32, f32), b: (f32, f32), radius: f32, hardness: f32, mut f: impl FnMut(usize, f32)) -> CellRect {
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
            let fall = if d <= solid {
                1.0
            } else if d >= radius {
                0.0
            } else {
                let u = 1.0 - (d - solid) / (radius - solid).max(0.001);
                u * u * (3.0 - 2.0 * u)
            };
            if fall > 0.0 {
                f(j * region.nx + i, fall);
            }
        }
    }
    (i0, j0, i1, j1)
}

/// Hand-painted values: per channel a strength mask (0 = the flat canvas, 1 = fully painted) and a value.
#[derive(Clone)]
pub struct Paint {
    pub mask: [Vec<f32>; NCH],
    pub value: [Vec<f32>; NCH],
}

/// Cells store channel lists rather than fixed arrays, so files from before a channel was added still load.
#[derive(Serialize, Deserialize)]
struct PaintSparse {
    n: usize,
    cells: Vec<(u32, Vec<f32>, Vec<f32>)>,
}

impl From<Paint> for PaintSparse {
    fn from(p: Paint) -> Self {
        let n = p.mask[0].len();
        let mut cells = Vec::new();
        for i in 0..n {
            if (0..NCH).any(|c| p.mask[c][i] > 0.0) {
                cells.push((i as u32, (0..NCH).map(|c| p.value[c][i]).collect(), (0..NCH).map(|c| p.mask[c][i]).collect()));
            }
        }
        PaintSparse { n, cells }
    }
}

impl From<PaintSparse> for Paint {
    fn from(s: PaintSparse) -> Self {
        let mut p = Paint::new(s.n);
        for (i, v, m) in s.cells {
            for c in 0..NCH.min(v.len()).min(m.len()) {
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

/// Settings for naturalize (brush and whole map).
#[derive(Clone, Copy)]
pub struct Natural {
    /// 0..1 how far values move toward their blurred neighbourhood.
    pub blur: f32,
    /// Blur radius in blocks.
    pub radius: f32,
    /// 0..1 of each channel's half-range added as noise (height: up to 48 blocks).
    pub noise: f32,
    /// Noise and warp wavelength in blocks.
    pub scale: f32,
    /// How far (blocks) shapes are pushed around, which roughens coastlines and biome edges.
    pub warp: f32,
    pub seed: u32,
    /// Channels it touches: the five climate channels, then height (bias and absolute together).
    pub channels: [bool; 6],
}

/// A relative change, applied once per stroke.
#[derive(Clone, Copy)]
pub enum Rel {
    /// Raise (or lower, when negative) by this much: channel units, or blocks for height.
    Add(f32),
    /// Add fbm noise of this amplitude.
    Noise { amp: f32, scale: f32, octaves: u32, seed: u32 },
}

/// A stroke that rewrites paint from its starting snapshot, so one stroke applies its change exactly once
/// (at the strongest brush weight each cell saw), however long the brush lingers.
pub struct Stroke {
    pub base: Paint,
    pub w: Vec<f32>,
}

impl Stroke {
    pub fn new(p: &Paint) -> Self {
        Stroke { base: p.clone(), w: vec![0.0; p.mask[0].len()] }
    }

    /// The whole map at one weight.
    pub fn full(p: &Paint, w: f32) -> Self {
        Stroke { base: p.clone(), w: vec![w; p.mask[0].len()] }
    }

    pub fn cover(&mut self, region: &Region, a: (f32, f32), b: (f32, f32), radius: f32, hardness: f32, strength: f32) -> CellRect {
        let w = &mut self.w;
        capsule(region, a, b, radius, hardness, |idx, f| w[idx] = w[idx].max(f * strength))
    }
}

pub enum Op<'a> {
    /// Paint these `(channel, value)` pairs.
    Set(&'a [(usize, f32)]),
    /// Remove paint, optionally only on one channel (height bias also clears absolute height).
    Erase(Option<usize>),
}

/// Bilinear sample of a `w`-wide field at fractional cell coordinates, clamped to its edges.
fn bilinear(f: &[f32], w: usize, h: usize, fi: f32, fj: f32) -> f32 {
    let fi = fi.clamp(0.0, (w - 1) as f32);
    let fj = fj.clamp(0.0, (h - 1) as f32);
    let (i0, j0) = (fi.floor() as usize, fj.floor() as usize);
    let (i1, j1) = ((i0 + 1).min(w - 1), (j0 + 1).min(h - 1));
    let (tx, tz) = (fi - i0 as f32, fj - j0 as f32);
    let top = f[j0 * w + i0] * (1.0 - tx) + f[j0 * w + i1] * tx;
    let bottom = f[j1 * w + i0] * (1.0 - tx) + f[j1 * w + i1] * tx;
    top * (1.0 - tz) + bottom * tz
}

/// `f` moved toward its box blur (radius `r` cells) by `mix`.
fn blurred(f: &[f32], w: usize, h: usize, r: usize, mix: f32) -> Vec<f32> {
    if r == 0 || mix <= 0.0 {
        return f.to_vec();
    }
    let sw = w + 1;
    let mut sat = vec![0.0f64; sw * (h + 1)];
    for j in 0..h {
        let mut row = 0.0f64;
        for i in 0..w {
            row += f[j * w + i] as f64;
            sat[(j + 1) * sw + i + 1] = sat[j * sw + i + 1] + row;
        }
    }
    let mut out = vec![0.0f32; w * h];
    for j in 0..h {
        let (a, b) = (j.saturating_sub(r), (j + r + 1).min(h));
        for i in 0..w {
            let (c, d) = (i.saturating_sub(r), (i + r + 1).min(w));
            let sum = sat[b * sw + d] - sat[a * sw + d] - sat[b * sw + c] + sat[a * sw + c];
            let mean = (sum / ((b - a) * (d - c)) as f64) as f32;
            let v = f[j * w + i];
            out[j * w + i] = v + (mean - v) * mix;
        }
    }
    out
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

    /// The value the world sees for a climate channel or the height bias (`base` = canvas value, 0 for bias).
    pub fn effective(&self, ch: usize, idx: usize, base: f32) -> f32 {
        base + (self.value[ch][idx] - base) * self.mask[ch][idx]
    }

    pub fn any_at(&self, i: usize) -> bool {
        (0..NCH).any(|c| self.mask[c][i] > 0.05)
    }

    /// Set cell `idx` of `ch` to its snapshot value (as the world saw it) plus `d`. Height bias also moves any
    /// absolute-height paint, so the final height changes by `d` either way.
    fn shift(&mut self, s: &Stroke, canvas: &[f32; 5], ch: usize, idx: usize, d: f32) {
        let e = s.base.effective(ch, idx, channel_base(canvas, ch));
        self.mask[ch][idx] = 1.0;
        self.value[ch][idx] = (e + d).clamp(CH_RANGE[ch].0, CH_RANGE[ch].1);
        if ch == CH_HB && s.base.mask[CH_AY][idx] > 0.0 {
            self.value[CH_AY][idx] = (s.base.value[CH_AY][idx] + d).clamp(CH_RANGE[CH_AY].0, CH_RANGE[CH_AY].1);
        }
    }

    /// Rewrite `rect` from the stroke snapshot with channel `ch` changed by `rel` times the stroke weight.
    pub fn apply_relative(&mut self, region: &Region, canvas: &[f32; 5], s: &Stroke, rect: CellRect, ch: usize, rel: Rel) {
        let perlin = Perlin::new(match rel {
            Rel::Noise { seed, .. } => seed as u64 * 7_919 + ch as u64 * 104_729 + 11,
            Rel::Add(_) => 0,
        });
        for j in rect.1..=rect.3 {
            for i in rect.0..=rect.2 {
                let idx = j * region.nx + i;
                let w = s.w[idx];
                if w <= 0.0 {
                    continue;
                }
                let d = match rel {
                    Rel::Add(a) => a * w,
                    Rel::Noise { amp, scale, octaves, .. } => {
                        let (x, z) = region.center(i, j);
                        let sc = scale.max(4.0) as f64;
                        perlin.fbm(x as f64 / sc, z as f64 / sc, octaves, 0.5, 2.0) as f32 * 2.0 * amp * w
                    }
                };
                self.shift(s, canvas, ch, idx, d);
            }
        }
    }

    /// Rewrite `rect` from the stroke snapshot, naturalized by the stroke weight: values move toward their blurred
    /// neighbourhood, shapes are warped (rough edges) and noise is added. Works on the whole world, painted or not.
    pub fn naturalize(&mut self, region: &Region, canvas: &[f32; 5], s: &Stroke, rect: CellRect, nat: &Natural) {
        let cell = region.cell as f32;
        let r = (nat.radius.max(0.0) / cell).round() as usize;
        let margin = r + (nat.warp.max(0.0) / cell).ceil() as usize + 2;
        let (wi0, wj0) = (rect.0.saturating_sub(margin), rect.1.saturating_sub(margin));
        let (wi1, wj1) = ((rect.2 + margin).min(region.nx - 1), (rect.3 + margin).min(region.nz - 1));
        let (ww, wh) = (wi1 - wi0 + 1, wj1 - wj0 + 1);
        let window = |f: &dyn Fn(usize) -> f32| -> Vec<f32> {
            let mut out = Vec::with_capacity(ww * wh);
            for j in wj0..=wj1 {
                for i in wi0..=wi1 {
                    out.push(f(j * region.nx + i));
                }
            }
            out
        };
        let scale = nat.scale.max(8.0) as f64;
        let (warp_x, warp_z) = (Perlin::new(nat.seed as u64 * 31 + 5_003), Perlin::new(nat.seed as u64 * 31 + 9_001));
        // cells to rewrite: (idx, window index, warped window coords, weight, world pos)
        let mut todo = Vec::new();
        for j in rect.1..=rect.3 {
            for i in rect.0..=rect.2 {
                let idx = j * region.nx + i;
                let w = s.w[idx];
                if w <= 0.0 {
                    continue;
                }
                let (x, z) = region.center(i, j);
                let (xs, zs) = (x as f64 / scale, z as f64 / scale);
                let dx = warp_x.fbm(xs, zs, 3, 0.5, 2.0) as f32 * 2.0 * nat.warp / cell;
                let dz = warp_z.fbm(xs, zs, 3, 0.5, 2.0) as f32 * 2.0 * nat.warp / cell;
                let (li, lj) = (i - wi0, j - wj0);
                todo.push((idx, lj * ww + li, li as f32 + dx, lj as f32 + dz, w, xs, zs));
            }
        }
        for g in 0..6 {
            if !nat.channels[g] {
                continue;
            }
            let ch = if g < 5 { g } else { CH_HB };
            let base = if g < 5 { canvas[g] } else { 0.0 };
            let span = if g < 5 { (CH_RANGE[g].1 - CH_RANGE[g].0) * 0.5 } else { HEIGHT_NOISE_SPAN };
            let perlin = Perlin::new(nat.seed as u64 * 31 + g as u64 * 977);
            let raw = window(&|k| s.base.effective(ch, k, base));
            let field = blurred(&raw, ww, wh, r, nat.blur);
            // absolute height has no canvas value: blur its strength and strength-weighted value instead
            let abs = (g == 5 && (wj0..=wj1).any(|j| (wi0..=wi1).any(|i| s.base.mask[CH_AY][j * region.nx + i] > 0.0))).then(|| {
                let m = window(&|k| s.base.mask[CH_AY][k]);
                let mv = window(&|k| s.base.mask[CH_AY][k] * s.base.value[CH_AY][k]);
                (blurred(&m, ww, wh, r, nat.blur), blurred(&mv, ww, wh, r, nat.blur))
            });
            for &(idx, k, fi, fj, w, xs, zs) in &todo {
                let n = if nat.noise > 0.0 { perlin.fbm(xs, zs, 3, 0.5, 2.0) as f32 * 2.0 * nat.noise * span } else { 0.0 };
                let e = raw[k];
                let target = bilinear(&field, ww, wh, fi, fj) + n;
                self.mask[ch][idx] = 1.0;
                self.value[ch][idx] = (e + (target - e) * w).clamp(CH_RANGE[ch].0, CH_RANGE[ch].1);
                if let Some((fm, fmv)) = &abs {
                    let (m0, v0) = (s.base.mask[CH_AY][idx], s.base.value[CH_AY][idx]);
                    let (tm, tmv) = (bilinear(fm, ww, wh, fi, fj), bilinear(fmv, ww, wh, fi, fj));
                    let tv = if tm > 1e-4 { tmv / tm } else { v0 };
                    let nm = (m0 + (tm - m0) * w).clamp(0.0, 1.0);
                    let mut nv = if m0 < 0.02 { tv } else { v0 + (tv - v0) * w };
                    nv += n * w;
                    self.mask[CH_AY][idx] = if nm < 0.02 { 0.0 } else { nm };
                    self.value[CH_AY][idx] = nv.clamp(CH_RANGE[CH_AY].0, CH_RANGE[CH_AY].1);
                }
            }
        }
    }

    /// Stamp a soft capsule from `a` to `b` (a dab when `a == b`). Returns the touched cell rectangle.
    #[allow(clippy::too_many_arguments)]
    pub fn stamp(&mut self, region: &Region, a: (f32, f32), b: (f32, f32), radius: f32, hardness: f32, strength: f32, op: &Op) -> CellRect {
        capsule(region, a, b, radius, hardness, |idx, f| {
            let w = f * strength;
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
                        if only.map_or(true, |o| o == ch || (o == CH_HB && ch == CH_AY)) {
                            let m = self.mask[ch][idx] * (1.0 - w);
                            self.mask[ch][idx] = if m < 0.02 { 0.0 } else { m };
                        }
                    }
                }
            }
        })
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
    /// Instances of this type are written to the pack's fixed_structures.json and pasted in game
    /// (the id must be a structure template).
    #[serde(default)]
    pub place_in_game: bool,
}

/// Template-backed types the pack places in game, with their default spots (absolute block x/z).
/// Footprints match the templates' x/z size.
pub fn fixed_types() -> Vec<(StructType, [i32; 2])> {
    vec![
        (StructType { name: "Watchtower".into(), id: "winterheart:watchtower".into(), w: 21, d: 21, color: [120, 255, 140], place_in_game: true }, [0, 400]),
        (StructType { name: "Shipwreck".into(), id: "winterheart:shipwreck".into(), w: 64, d: 32, color: [120, 200, 255], place_in_game: true }, [950, 0]),
        (StructType { name: "Beacon".into(), id: "winterheart:beacon".into(), w: 29, d: 25, color: [255, 240, 120], place_in_game: true }, [-1100, 0]),
    ]
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
    /// Climate of unpainted ground (continentalness, erosion, temperature, humidity, weirdness). There is no
    /// generated noise: everything else is painted.
    #[serde(default = "default_canvas")]
    pub canvas: [f32; 5],
    pub paint: Paint,
    pub types: Vec<StructType>,
    pub structures: Vec<StructInst>,
    pub spawn: [i32; 2],
    /// Y the player and the Starter Structure are placed at; the spawn pad's ground is one block below.
    #[serde(default = "default_spawn_y")]
    pub spawn_y: i32,
    /// Radius (blocks) of the flat pad flattened to `spawn_y - 1` at spawn in the export (0 = off), and the
    /// width it blends back into the terrain over.
    #[serde(default = "default_spawn_pad")]
    pub spawn_pad: f32,
    #[serde(default = "default_spawn_pad_blend")]
    pub spawn_pad_blend: f32,
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
    /// Blocks the border pixels' climate (and so their biomes) is extended outward in the export; the edge ocean
    /// ramp sits at the outside of this padding.
    #[serde(default = "default_padding")]
    pub padding: i32,
    /// Keep the export's detail bumps on absolute-height paint (off = painted Y is exact).
    #[serde(default)]
    pub abs_detail: bool,
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
    /// Set once the in-game template types (`fixed_types`) and their default instances have been added,
    /// so deleting one later sticks.
    #[serde(default)]
    pub fixed_seeded: bool,
    /// Template ids from `fixed_types` whose default instance has been added (so a new built-in type still gets
    /// one in an existing project, and deleting it later sticks).
    #[serde(default)]
    pub seeded_ids: Vec<String>,
}

fn default_spawn_y() -> i32 {
    66
}
fn default_spawn_pad() -> f32 {
    40.0
}
fn default_spawn_pad_blend() -> f32 {
    32.0
}
fn default_canvas() -> [f32; 5] {
    [0.3, 0.25, -0.3, 0.0, 0.3]
}
fn default_padding() -> i32 {
    512
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
            canvas: default_canvas(),
            paint: Paint::new(n),
            types: vec![
                StructType { name: "Escape Camp".into(), id: "winterheart:escape_camp".into(), w: 48, d: 48, color: [255, 120, 40], place_in_game: false },
                StructType { name: "Outpost".into(), id: "winterheart:outpost".into(), w: 24, d: 24, color: [255, 220, 60], place_in_game: false },
                StructType { name: "Ruin".into(), id: "winterheart:ruin".into(), w: 16, d: 16, color: [190, 190, 255], place_in_game: false },
            ],
            structures: vec![],
            spawn: [0, 0],
            spawn_y: default_spawn_y(),
            spawn_pad: default_spawn_pad(),
            spawn_pad_blend: default_spawn_pad_blend(),
            path: PathGuide::default(),
            guide_offset: None,
            guide_radius: None,
            edge_width: default_edge(),
            padding: default_padding(),
            abs_detail: false,
            detail_amp: 6.0,
            detail_scale: default_detail_scale(),
            detail_octaves: default_detail_octaves(),
            detail_persistence: default_detail_persistence(),
            detail_ridged: default_detail_ridged(),
            mid_amp: default_mid_amp(),
            mid_scale: default_mid_scale(),
            rough_by_erosion: default_rough_by_erosion(),
            winter_palette: true,
            fixed_seeded: false,
            seeded_ids: vec![],
        };
        p.apply_corridor_preset();
        p.seed_fixed_structures();
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

    /// Add the in-game template types if missing, and (once per project) an instance of each at its default spot.
    pub fn seed_fixed_structures(&mut self) {
        // projects seeded before `seeded_ids` existed had exactly these two
        if self.fixed_seeded && self.seeded_ids.is_empty() {
            self.seeded_ids = vec!["winterheart:watchtower".into(), "winterheart:shipwreck".into()];
        }
        for (t, [x, z]) in fixed_types() {
            let id = t.id.clone();
            let ty = match self.types.iter().position(|e| e.id == t.id) {
                Some(i) => i,
                None => {
                    self.types.push(t);
                    self.types.len() - 1
                }
            };
            if !self.seeded_ids.contains(&id) {
                if !self.structures.iter().any(|s| s.ty == ty) {
                    self.structures.push(StructInst { ty, x, z, rot: 0 });
                }
                self.seeded_ids.push(id);
            }
        }
        self.fixed_seeded = true;
    }

    /// Instances whose type is placed in game: (type, instance).
    pub fn in_game_structures(&self) -> impl Iterator<Item = (&StructType, &StructInst)> {
        self.structures.iter().map(|s| (&self.types[s.ty.min(self.types.len() - 1)], s)).filter(|(t, _)| t.place_in_game)
    }

    /// Convert a pre-`path` project's single guide offset into a one-point path.
    pub fn migrate(&mut self) {
        self.seed_fixed_structures();
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

    fn nat() -> Natural {
        Natural { blur: 0.8, radius: 32.0, noise: 0.15, scale: 50.0, warp: 24.0, seed: 3, channels: [true; 6] }
    }

    #[test]
    fn global_naturalize_softens_edges_and_reaches_unpainted_ground() {
        let mut p = Project::new();
        p.resize(1024);
        p.paint.clear();
        let r = p.region.clone();
        // a hard-edged dab, so blur has a sharp edge to spread past
        p.paint.stamp(&r, (200.0, -200.0), (200.0, -200.0), 60.0, 1.0, 1.0, &Op::Set(&[(CH_C, 0.6), (CH_AY, 150.0)]));
        let s = Stroke::full(&p.paint, 1.0);
        p.paint.naturalize(&r, &p.canvas, &s, r.all(), &nat());
        let idx = |x: f32, z: f32| {
            let (i, j) = r.cell_at(x, z).unwrap();
            j * r.nx + i
        };
        for ch in 0..NCH {
            for i in 0..r.len() {
                let (m, v) = (p.paint.mask[ch][i], p.paint.value[ch][i]);
                assert!((0.0..=1.0).contains(&m), "mask {m}");
                assert!(v >= CH_RANGE[ch].0 - 1e-3 && v <= CH_RANGE[ch].1 + 1e-3, "value {v}");
            }
        }
        // far from any paint: the flat canvas got noise
        let far = idx(-400.0, 400.0);
        assert!((p.paint.effective(CH_T, far, p.canvas[CH_T]) - p.canvas[CH_T]).abs() > 1e-4);
        // just outside the dab, continentalness was pulled up by the blur
        let out = idx(270.0, -200.0);
        assert!(p.paint.effective(CH_C, out, p.canvas[CH_C]) > p.canvas[CH_C] + 0.02);
        // the absolute-height edge became soft
        assert!(p.paint.mask[CH_AY][out] > 0.0 && p.paint.mask[CH_AY][out] < 1.0);
    }

    #[test]
    fn relative_change_applies_once_per_stroke() {
        let mut p = Project::new();
        p.resize(512);
        p.paint.clear();
        let r = p.region.clone();
        let mut s = Stroke::new(&p.paint);
        // the brush lingers: ten dabs on the same spot still raise by exactly the amount
        for _ in 0..10 {
            let rect = s.cover(&r, (0.0, 0.0), (0.0, 0.0), 40.0, 1.0, 1.0);
            p.paint.apply_relative(&r, &p.canvas, &s, rect, CH_C, Rel::Add(-0.3));
        }
        let (i, j) = r.cell_at(0.0, 0.0).unwrap();
        let k = j * r.nx + i;
        assert!((p.paint.effective(CH_C, k, p.canvas[CH_C]) - (p.canvas[CH_C] - 0.3)).abs() < 1e-5);
        // a second stroke adds again
        let mut s = Stroke::new(&p.paint);
        let rect = s.cover(&r, (0.0, 0.0), (0.0, 0.0), 40.0, 1.0, 1.0);
        p.paint.apply_relative(&r, &p.canvas, &s, rect, CH_C, Rel::Add(-0.3));
        assert!((p.paint.effective(CH_C, k, p.canvas[CH_C]) - (p.canvas[CH_C] - 0.6)).abs() < 1e-5);
    }

    #[test]
    fn noise_injection_touches_only_its_channel() {
        let mut p = Project::new();
        p.resize(512);
        p.paint.clear();
        let r = p.region.clone();
        let mut s = Stroke::new(&p.paint);
        let rect = s.cover(&r, (0.0, 0.0), (0.0, 0.0), 200.0, 0.5, 1.0);
        p.paint.apply_relative(&r, &p.canvas, &s, rect, CH_C, Rel::Noise { amp: 0.4, scale: 60.0, octaves: 3, seed: 1 });
        let changed = (0..r.len()).filter(|&k| (p.paint.effective(CH_C, k, p.canvas[CH_C]) - p.canvas[CH_C]).abs() > 1e-3).count();
        assert!(changed > 50, "changed {changed}");
        for ch in (0..NCH).filter(|c| *c != CH_C) {
            assert!(p.paint.mask[ch].iter().all(|m| *m == 0.0), "channel {ch} touched");
        }
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
    fn old_projects_gain_in_game_types_once() {
        let mut v = serde_json::to_value(Project::new()).unwrap();
        let o = v.as_object_mut().unwrap();
        o.remove("fixed_seeded");
        o.remove("seeded_ids");
        o.insert("types".into(), serde_json::json!([{ "name": "Escape Camp", "id": "winterheart:escape_camp", "w": 48, "d": 48, "color": [255, 120, 40] }]));
        o.insert("structures".into(), serde_json::json!([{ "ty": 0, "x": 0, "z": 400, "rot": 0 }]));
        let mut p: Project = serde_json::from_value(v).unwrap();
        p.migrate();
        let spots: Vec<_> = p.in_game_structures().map(|(t, s)| (t.id.clone(), s.x, s.z)).collect();
        assert_eq!(
            spots,
            vec![("winterheart:watchtower".into(), 0, 400), ("winterheart:shipwreck".into(), 950, 0), ("winterheart:beacon".into(), -1100, 0)]
        );
        // deleting one after seeding sticks across save/load
        p.structures.retain(|s| p.types[s.ty].id != "winterheart:shipwreck");
        let mut q: Project = serde_json::from_value(serde_json::to_value(&p).unwrap()).unwrap();
        q.migrate();
        assert_eq!(q.in_game_structures().count(), 2);
        assert_eq!(q.types.len(), 4);
    }

    #[test]
    fn new_built_in_type_seeds_into_an_already_seeded_project() {
        // saved before the beacon existed: seeded flag set, no per-id list, watchtower moved by hand
        let mut p = Project::new();
        let tower = p.types.iter().position(|t| t.id == "winterheart:beacon").unwrap();
        p.structures.retain(|s| s.ty != tower);
        p.types.remove(tower);
        p.structures.iter_mut().filter(|s| p.types[s.ty].id == "winterheart:watchtower").for_each(|s| s.x = 64);
        let mut v = serde_json::to_value(&p).unwrap();
        v.as_object_mut().unwrap().remove("seeded_ids");
        let mut q: Project = serde_json::from_value(v).unwrap();
        q.migrate();
        let spots: Vec<_> = q.in_game_structures().map(|(t, s)| (t.id.clone(), s.x, s.z)).collect();
        assert_eq!(
            spots,
            vec![("winterheart:watchtower".into(), 64, 400), ("winterheart:shipwreck".into(), 950, 0), ("winterheart:beacon".into(), -1100, 0)]
        );
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
