//! Approximation of vanilla 1.20.1 overworld multi-noise biome selection
//! (`OverworldBiomeBuilder`). It follows the same structure -- temperature/humidity/erosion bands,
//! continentalness zones and a peaks-and-valleys slice -- but is simplified, so treat it as a planner.

#[derive(Clone, Copy, PartialEq, Debug)]
pub enum TreeKind {
    Oak,
    Conifer,
    Birch,
    DarkOak,
    Jungle,
    Acacia,
    Cherry,
    Mangrove,
    Bamboo,
}

pub struct Biome {
    pub id: &'static str,
    pub name: &'static str,
    pub color: [u8; 3],
    /// Tree style and density in 0..1 (0 = bare).
    pub tree: Option<(TreeKind, f32)>,
}

macro_rules! biomes {
    ($( $c:ident, $id:literal, $name:literal, $color:expr, $tree:expr; )*) => {
        #[allow(non_camel_case_types, dead_code, clippy::upper_case_acronyms)]
        #[repr(u8)]
        enum Idx { $($c),* }
        #[allow(dead_code)]
        pub mod b { $(pub const $c: u8 = super::Idx::$c as u8;)* }
        pub const BIOMES: &[Biome] = &[ $(Biome { id: $id, name: $name, color: $color, tree: $tree }),* ];
    };
}

use TreeKind::*;

biomes! {
    DEEP_FROZEN_OCEAN, "minecraft:deep_frozen_ocean", "Deep Frozen Ocean", [58, 78, 132], None;
    DEEP_COLD_OCEAN, "minecraft:deep_cold_ocean", "Deep Cold Ocean", [32, 56, 120], None;
    DEEP_OCEAN, "minecraft:deep_ocean", "Deep Ocean", [18, 40, 108], None;
    DEEP_LUKEWARM_OCEAN, "minecraft:deep_lukewarm_ocean", "Deep Lukewarm Ocean", [18, 52, 120], None;
    FROZEN_OCEAN, "minecraft:frozen_ocean", "Frozen Ocean", [110, 130, 190], None;
    COLD_OCEAN, "minecraft:cold_ocean", "Cold Ocean", [50, 90, 170], None;
    OCEAN, "minecraft:ocean", "Ocean", [36, 70, 160], None;
    LUKEWARM_OCEAN, "minecraft:lukewarm_ocean", "Lukewarm Ocean", [40, 100, 175], None;
    WARM_OCEAN, "minecraft:warm_ocean", "Warm Ocean", [50, 130, 190], None;
    MUSHROOM_FIELDS, "minecraft:mushroom_fields", "Mushroom Fields", [150, 100, 140], None;
    BEACH, "minecraft:beach", "Beach", [230, 218, 160], None;
    SNOWY_BEACH, "minecraft:snowy_beach", "Snowy Beach", [235, 235, 225], None;
    STONY_SHORE, "minecraft:stony_shore", "Stony Shore", [130, 130, 128], None;
    RIVER, "minecraft:river", "River", [60, 110, 200], None;
    FROZEN_RIVER, "minecraft:frozen_river", "Frozen River", [160, 190, 230], None;
    PLAINS, "minecraft:plains", "Plains", [140, 188, 90], None;
    SUNFLOWER_PLAINS, "minecraft:sunflower_plains", "Sunflower Plains", [170, 200, 80], None;
    SNOWY_PLAINS, "minecraft:snowy_plains", "Snowy Plains", [235, 240, 245], None;
    ICE_SPIKES, "minecraft:ice_spikes", "Ice Spikes", [175, 220, 245], None;
    FOREST, "minecraft:forest", "Forest", [86, 148, 66], Some((Oak, 0.8));
    FLOWER_FOREST, "minecraft:flower_forest", "Flower Forest", [120, 160, 90], Some((Oak, 0.55));
    BIRCH_FOREST, "minecraft:birch_forest", "Birch Forest", [110, 165, 85], Some((Birch, 0.8));
    OLD_GROWTH_BIRCH_FOREST, "minecraft:old_growth_birch_forest", "Old Growth Birch Forest", [100, 158, 80], Some((Birch, 0.95));
    DARK_FOREST, "minecraft:dark_forest", "Dark Forest", [58, 98, 44], Some((DarkOak, 1.0));
    TAIGA, "minecraft:taiga", "Taiga", [70, 130, 90], Some((Conifer, 0.7));
    SNOWY_TAIGA, "minecraft:snowy_taiga", "Snowy Taiga", [185, 205, 205], Some((Conifer, 0.7));
    OLD_GROWTH_PINE_TAIGA, "minecraft:old_growth_pine_taiga", "Old Growth Pine Taiga", [88, 120, 80], Some((Conifer, 0.9));
    OLD_GROWTH_SPRUCE_TAIGA, "minecraft:old_growth_spruce_taiga", "Old Growth Spruce Taiga", [78, 112, 82], Some((Conifer, 0.9));
    SAVANNA, "minecraft:savanna", "Savanna", [186, 176, 90], Some((Acacia, 0.12));
    SAVANNA_PLATEAU, "minecraft:savanna_plateau", "Savanna Plateau", [180, 160, 80], Some((Acacia, 0.12));
    DESERT, "minecraft:desert", "Desert", [226, 202, 130], None;
    BADLANDS, "minecraft:badlands", "Badlands", [200, 120, 70], None;
    WOODED_BADLANDS, "minecraft:wooded_badlands", "Wooded Badlands", [190, 125, 75], Some((Oak, 0.25));
    JUNGLE, "minecraft:jungle", "Jungle", [60, 150, 40], Some((Jungle, 1.0));
    SPARSE_JUNGLE, "minecraft:sparse_jungle", "Sparse Jungle", [90, 160, 60], Some((Jungle, 0.5));
    BAMBOO_JUNGLE, "minecraft:bamboo_jungle", "Bamboo Jungle", [100, 170, 50], Some((Bamboo, 0.9));
    SWAMP, "minecraft:swamp", "Swamp", [80, 110, 70], Some((Oak, 0.25));
    MANGROVE_SWAMP, "minecraft:mangrove_swamp", "Mangrove Swamp", [90, 105, 60], Some((Mangrove, 0.45));
    MEADOW, "minecraft:meadow", "Meadow", [130, 190, 120], None;
    CHERRY_GROVE, "minecraft:cherry_grove", "Cherry Grove", [215, 160, 190], Some((Cherry, 0.45));
    GROVE, "minecraft:grove", "Grove", [190, 215, 200], Some((Conifer, 0.55));
    SNOWY_SLOPES, "minecraft:snowy_slopes", "Snowy Slopes", [225, 232, 238], None;
    JAGGED_PEAKS, "minecraft:jagged_peaks", "Jagged Peaks", [235, 238, 242], None;
    FROZEN_PEAKS, "minecraft:frozen_peaks", "Frozen Peaks", [200, 220, 238], None;
    STONY_PEAKS, "minecraft:stony_peaks", "Stony Peaks", [150, 142, 135], None;
    WINDSWEPT_HILLS, "minecraft:windswept_hills", "Windswept Hills", [120, 140, 115], Some((Conifer, 0.12));
    WINDSWEPT_GRAVELLY_HILLS, "minecraft:windswept_gravelly_hills", "Windswept Gravelly Hills", [135, 135, 130], None;
    WINDSWEPT_FOREST, "minecraft:windswept_forest", "Windswept Forest", [95, 130, 95], Some((Conifer, 0.55));
    WINDSWEPT_SAVANNA, "minecraft:windswept_savanna", "Windswept Savanna", [170, 165, 95], Some((Acacia, 0.1));
}

pub fn biome(i: u8) -> &'static Biome {
    &BIOMES[(i as usize).min(BIOMES.len() - 1)]
}

pub fn is_ocean(i: u8) -> bool {
    i <= b::WARM_OCEAN
}

pub fn is_river(i: u8) -> bool {
    i == b::RIVER || i == b::FROZEN_RIVER
}

// ---- climate bands (vanilla values) ----

pub const T_EDGES: [f32; 4] = [-0.45, -0.15, 0.2, 0.55];
pub const H_EDGES: [f32; 4] = [-0.35, -0.1, 0.1, 0.3];
pub const E_EDGES: [f32; 6] = [-0.78, -0.375, -0.2225, 0.05, 0.45, 0.55];

pub const T_CENTERS: [f32; 5] = [-0.725, -0.3, 0.025, 0.375, 0.775];
pub const H_CENTERS: [f32; 5] = [-0.675, -0.225, 0.0, 0.2, 0.65];
pub const E_CENTERS: [f32; 7] = [-0.89, -0.58, -0.30, -0.09, 0.25, 0.5, 0.775];
/// mushroom, deep ocean, ocean, coast, near inland, mid inland, far inland
pub const C_CENTERS: [f32; 7] = [-1.1, -0.75, -0.32, -0.15, -0.04, 0.165, 0.65];
/// valley, low, mid, high, peak (weirdness values giving those peaks-and-valleys zones)
pub const W_ZONES: [f32; 5] = [0.0, 0.1, 0.3, 0.484, 0.667];

fn band(v: f32, edges: &[f32]) -> usize {
    edges.iter().take_while(|&&e| v >= e).count()
}

pub fn peaks_valleys(w: f32) -> f32 {
    -((w.abs() - 2.0 / 3.0).abs() - 1.0 / 3.0) * 3.0
}

fn middle(t: usize, h: usize, variant: bool) -> u8 {
    const NORMAL: [[u8; 5]; 5] = [
        [b::SNOWY_PLAINS, b::SNOWY_PLAINS, b::SNOWY_PLAINS, b::SNOWY_TAIGA, b::TAIGA],
        [b::PLAINS, b::PLAINS, b::FOREST, b::TAIGA, b::OLD_GROWTH_SPRUCE_TAIGA],
        [b::FLOWER_FOREST, b::PLAINS, b::FOREST, b::BIRCH_FOREST, b::DARK_FOREST],
        [b::SAVANNA, b::SAVANNA, b::FOREST, b::JUNGLE, b::JUNGLE],
        [b::DESERT, b::DESERT, b::DESERT, b::DESERT, b::DESERT],
    ];
    const VARIANT: [[u8; 5]; 5] = [
        [b::ICE_SPIKES, 255, b::SNOWY_TAIGA, 255, 255],
        [255, 255, 255, 255, b::OLD_GROWTH_PINE_TAIGA],
        [b::SUNFLOWER_PLAINS, 255, 255, b::OLD_GROWTH_BIRCH_FOREST, 255],
        [255, 255, b::PLAINS, b::SPARSE_JUNGLE, b::BAMBOO_JUNGLE],
        [255; 5],
    ];
    let v = VARIANT[t][h];
    if variant && v != 255 {
        v
    } else {
        NORMAL[t][h]
    }
}

fn middle_or_badlands(t: usize, h: usize, variant: bool) -> u8 {
    if t == 4 {
        if h >= 3 {
            b::WOODED_BADLANDS
        } else {
            b::BADLANDS
        }
    } else {
        middle(t, h, variant)
    }
}

fn plateau(t: usize, h: usize, variant: bool) -> u8 {
    const P: [[u8; 5]; 5] = [
        [b::SNOWY_PLAINS, b::SNOWY_PLAINS, b::SNOWY_PLAINS, b::SNOWY_TAIGA, b::SNOWY_TAIGA],
        [b::MEADOW, b::MEADOW, b::FOREST, b::TAIGA, b::OLD_GROWTH_SPRUCE_TAIGA],
        [b::MEADOW, b::MEADOW, b::MEADOW, b::MEADOW, b::DARK_FOREST],
        [b::SAVANNA_PLATEAU, b::SAVANNA_PLATEAU, b::FOREST, b::FOREST, b::JUNGLE],
        [b::BADLANDS, b::BADLANDS, b::BADLANDS, b::WOODED_BADLANDS, b::WOODED_BADLANDS],
    ];
    if variant && ((t == 1 && h == 0) || (t == 2 && h <= 1)) {
        return b::CHERRY_GROVE;
    }
    P[t][h]
}

fn shattered(t: usize, h: usize) -> u8 {
    match t {
        0 | 1 => {
            if h <= 1 {
                b::WINDSWEPT_GRAVELLY_HILLS
            } else {
                b::WINDSWEPT_HILLS
            }
        }
        2 => {
            if h <= 2 {
                b::WINDSWEPT_HILLS
            } else {
                b::WINDSWEPT_FOREST
            }
        }
        3 => {
            if h <= 1 {
                b::WINDSWEPT_SAVANNA
            } else {
                b::WINDSWEPT_FOREST
            }
        }
        _ => b::DESERT,
    }
}

fn slopes(t: usize) -> u8 {
    if t <= 1 {
        b::SNOWY_SLOPES
    } else if t <= 2 {
        b::GROVE
    } else {
        b::STONY_PEAKS
    }
}

fn peaks(t: usize) -> u8 {
    [b::JAGGED_PEAKS, b::FROZEN_PEAKS, b::STONY_PEAKS, b::STONY_PEAKS, b::STONY_PEAKS][t]
}

fn swamp(t: usize, variant: bool) -> u8 {
    if t >= 3 && variant {
        b::MANGROVE_SWAMP
    } else {
        b::SWAMP
    }
}

/// Pick the biome for a set of climate parameters (all in vanilla's -1..1 convention).
pub fn pick(t: f32, h: f32, c: f32, e: f32, w: f32) -> u8 {
    let ti = band(t, &T_EDGES);
    let hi = band(h, &H_EDGES);
    let ei = band(e, &E_EDGES);
    let pv = peaks_valleys(w);
    let variant = w > 0.0;

    if c < -1.05 {
        return b::MUSHROOM_FIELDS;
    }
    if c < -0.455 {
        return [b::DEEP_FROZEN_OCEAN, b::DEEP_COLD_OCEAN, b::DEEP_OCEAN, b::DEEP_LUKEWARM_OCEAN, b::WARM_OCEAN][ti];
    }
    if c < -0.19 {
        return [b::FROZEN_OCEAN, b::COLD_OCEAN, b::OCEAN, b::LUKEWARM_OCEAN, b::WARM_OCEAN][ti];
    }

    let coast = c < -0.11;
    let near = c < 0.03;

    if pv <= -0.85 {
        // valleys
        if coast {
            return if ei <= 1 { b::STONY_SHORE } else { beach(ti) };
        }
        if ei == 6 && ti > 0 {
            return swamp(ti, variant);
        }
        return if ti == 0 { b::FROZEN_RIVER } else { b::RIVER };
    }

    if coast {
        return match ei {
            0 | 1 => b::STONY_SHORE,
            6 if ti > 0 => swamp(ti, variant),
            _ => beach(ti),
        };
    }

    if pv > 0.7 {
        // peaks
        return match ei {
            0 => peaks(ti),
            1 => if ti <= 1 { b::SNOWY_SLOPES } else { b::STONY_PEAKS },
            2 => plateau(ti, hi, variant),
            3 => if near { middle(ti, hi, variant) } else { plateau(ti, hi, variant) },
            _ => middle(ti, hi, variant),
        };
    }
    if pv > 0.2 {
        // high slice
        return match ei {
            0 => slopes(ti),
            1 => if near { slopes(ti) } else { plateau(ti, hi, variant) },
            2 => plateau(ti, hi, variant),
            3 => if near { middle(ti, hi, variant) } else { plateau(ti, hi, variant) },
            _ => middle(ti, hi, variant),
        };
    }
    if pv > -0.6 {
        // mid slice
        return match ei {
            0 => if ti <= 2 { slopes(ti) } else { plateau(ti, hi, variant) },
            1 | 2 => if near { middle_or_badlands(ti, hi, variant) } else { plateau(ti, hi, variant) },
            3 | 4 => middle(ti, hi, variant),
            5 => shattered(ti, hi),
            _ => middle(ti, hi, variant),
        };
    }
    // low slice
    match ei {
        0 | 1 => middle_or_badlands(ti, hi, variant),
        2 => if near { middle(ti, hi, variant) } else { plateau(ti, hi, variant) },
        3 | 4 => middle(ti, hi, variant),
        5 => if near { middle(ti, hi, variant) } else { shattered(ti, hi) },
        _ => if ti > 0 { swamp(ti, variant) } else { middle(ti, hi, variant) },
    }
}

/// Remap warm biomes to snowy equivalents, matching the pack's snowy-only overworld
/// (frozen oceans, snowy plains/taiga, slopes, groves, peaks).
pub fn winterize(i: u8) -> u8 {
    match i {
        b::DEEP_FROZEN_OCEAN | b::DEEP_COLD_OCEAN | b::DEEP_OCEAN | b::DEEP_LUKEWARM_OCEAN => b::DEEP_FROZEN_OCEAN,
        b::FROZEN_OCEAN | b::COLD_OCEAN | b::OCEAN | b::LUKEWARM_OCEAN | b::WARM_OCEAN | b::MUSHROOM_FIELDS => b::FROZEN_OCEAN,
        b::BEACH => b::SNOWY_BEACH,
        b::RIVER => b::FROZEN_RIVER,
        b::PLAINS | b::SUNFLOWER_PLAINS | b::MEADOW | b::SAVANNA | b::SAVANNA_PLATEAU | b::DESERT | b::BADLANDS | b::WOODED_BADLANDS | b::SWAMP | b::MANGROVE_SWAMP | b::JUNGLE | b::SPARSE_JUNGLE | b::BAMBOO_JUNGLE | b::WINDSWEPT_SAVANNA => b::SNOWY_PLAINS,
        b::FOREST | b::FLOWER_FOREST | b::BIRCH_FOREST | b::OLD_GROWTH_BIRCH_FOREST | b::DARK_FOREST | b::TAIGA | b::OLD_GROWTH_PINE_TAIGA | b::OLD_GROWTH_SPRUCE_TAIGA | b::WINDSWEPT_FOREST => b::SNOWY_TAIGA,
        b::CHERRY_GROVE => b::GROVE,
        b::WINDSWEPT_HILLS | b::WINDSWEPT_GRAVELLY_HILLS => b::SNOWY_SLOPES,
        other => other,
    }
}

fn beach(ti: usize) -> u8 {
    match ti {
        0 => b::SNOWY_BEACH,
        4 => b::DESERT,
        _ => b::BEACH,
    }
}

/// Representative climate point `[t, h, c, e, w]` for painting a biome directly. Found by brute-force
/// search over band centres so that `pick(repr) == biome`, preferring gentle, flat land.
pub fn representative(target: u8) -> Option<[f32; 5]> {
    const C_ORDER: [usize; 7] = [5, 4, 6, 3, 2, 1, 0];
    const W_ORDER: [usize; 5] = [2, 1, 3, 0, 4];
    const E_ORDER: [usize; 7] = [4, 3, 5, 2, 6, 1, 0];
    for &ci in &C_ORDER {
        for &wi in &W_ORDER {
            for &ei in &E_ORDER {
                for ti in 0..5 {
                    for hi in 0..5 {
                        for sign in [-1.0f32, 1.0] {
                            let w = W_ZONES[wi] * sign;
                            let (t, h, c, e) = (T_CENTERS[ti], H_CENTERS[hi], C_CENTERS[ci], E_CENTERS[ei]);
                            if pick(t, h, c, e, w) == target {
                                return Some([t, h, c, e, w]);
                            }
                        }
                    }
                }
            }
        }
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn every_biome_is_paintable() {
        for i in 0..BIOMES.len() as u8 {
            let r = representative(i);
            println!("{:<34} {:?}", BIOMES[i as usize].id, r);
            if let Some(r) = r {
                assert_eq!(pick(r[0], r[1], r[2], r[3], r[4]), i);
            }
        }
    }

    #[test]
    fn constants_match_table() {
        assert_eq!(BIOMES[b::PLAINS as usize].id, "minecraft:plains");
        assert_eq!(BIOMES[b::WINDSWEPT_SAVANNA as usize].id, "minecraft:windswept_savanna");
        assert_eq!(BIOMES.len(), b::WINDSWEPT_SAVANNA as usize + 1);
    }
}
