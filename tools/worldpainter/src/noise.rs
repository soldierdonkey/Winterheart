use serde::{Deserialize, Serialize};

/// Classic improved-Perlin 2D noise with a seeded permutation table.
pub struct Perlin {
    p: [u8; 512],
}

fn splitmix(state: &mut u64) -> u64 {
    *state = state.wrapping_add(0x9E37_79B9_7F4A_7C15);
    let mut z = *state;
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

fn fade(t: f64) -> f64 {
    t * t * t * (t * (t * 6.0 - 15.0) + 10.0)
}

fn lerp(t: f64, a: f64, b: f64) -> f64 {
    a + t * (b - a)
}

fn grad(h: u8, x: f64, y: f64) -> f64 {
    match h & 7 {
        0 => x + y,
        1 => x - y,
        2 => -x + y,
        3 => -x - y,
        4 => x,
        5 => -x,
        6 => y,
        _ => -y,
    }
}

impl Perlin {
    pub fn new(seed: u64) -> Self {
        let mut perm: Vec<u8> = (0..=255u8).collect();
        let mut s = seed ^ 0x1234_5678_9ABC_DEF0;
        for i in (1..256usize).rev() {
            let j = (splitmix(&mut s) % (i as u64 + 1)) as usize;
            perm.swap(i, j);
        }
        let mut p = [0u8; 512];
        for (i, v) in p.iter_mut().enumerate() {
            *v = perm[i & 255];
        }
        Self { p }
    }

    /// Roughly in [-1, 1], typically within +-0.7.
    pub fn get(&self, x: f64, y: f64) -> f64 {
        let xf0 = x.floor();
        let yf0 = y.floor();
        let (xf, yf) = (x - xf0, y - yf0);
        let xi = (xf0 as i64 & 255) as usize;
        let yi = (yf0 as i64 & 255) as usize;
        let (u, v) = (fade(xf), fade(yf));
        let a = self.p[xi] as usize + yi;
        let b = self.p[xi + 1] as usize + yi;
        let (aa, ab, ba, bb) = (self.p[a], self.p[a + 1], self.p[b], self.p[b + 1]);
        lerp(
            v,
            lerp(u, grad(aa, xf, yf), grad(ba, xf - 1.0, yf)),
            lerp(u, grad(ab, xf, yf - 1.0), grad(bb, xf - 1.0, yf - 1.0)),
        )
    }

    /// Fractal Brownian motion, normalised by the summed amplitudes.
    pub fn fbm(&self, x: f64, z: f64, octaves: u32, persistence: f64, lacunarity: f64) -> f64 {
        let (mut amp, mut freq, mut sum, mut norm) = (1.0, 1.0, 0.0, 0.0);
        for o in 0..octaves.max(1) {
            sum += amp * self.get(x * freq + o as f64 * 17.31, z * freq - o as f64 * 9.77);
            norm += amp;
            amp *= persistence;
            freq *= lacunarity;
        }
        sum / norm
    }
}

/// Everything the user can tune for one noise channel.
#[derive(Clone, Serialize, Deserialize, PartialEq)]
pub struct NoiseCfg {
    /// Wavelength of the lowest octave, in blocks.
    pub scale: f32,
    pub octaves: u32,
    pub persistence: f32,
    pub lacunarity: f32,
    /// Multiplier applied before bias; fbm output is clustered near 0 so this stretches it.
    pub contrast: f32,
    pub amplitude: f32,
    pub bias: f32,
    pub seed_offset: u32,
}

impl NoiseCfg {
    pub fn new(scale: f32, octaves: u32, contrast: f32, bias: f32) -> Self {
        Self {
            scale,
            octaves,
            persistence: 0.5,
            lacunarity: 2.0,
            contrast,
            amplitude: 1.0,
            bias,
            seed_offset: 0,
        }
    }

    pub fn perlin(&self, project_seed: u32, channel: usize) -> Perlin {
        Perlin::new(project_seed as u64 * 1_000_003 + channel as u64 * 7919 + self.seed_offset as u64 * 104_729)
    }

    /// Unclamped channel value at a block position.
    pub fn sample(&self, p: &Perlin, x: f64, z: f64) -> f32 {
        let s = self.scale.max(1.0) as f64;
        let n = p.fbm(x / s, z / s, self.octaves, self.persistence as f64, self.lacunarity as f64);
        self.bias + self.amplitude * self.contrast * n as f32
    }
}
