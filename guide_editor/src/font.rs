//! Minecraft's default font, rebuilt from the resource stack the same way the game does it:
//! the merged `font/default.json` providers (modified packs first, vanilla last), bitmap glyph sheets,
//! glyph widths measured from the pixels, and the `space` provider for advances.

use crate::assets::Resources;
use macroquad::prelude::*;
use serde_json::Value;
use std::collections::HashMap;

#[derive(Clone, Copy, PartialEq)]
pub struct Style {
    pub color: [u8; 3],
    pub bold: bool,
    pub italic: bool,
    pub underline: bool,
    pub strike: bool,
}

impl Style {
    pub const fn plain(color: [u8; 3]) -> Style {
        Style { color, bold: false, italic: false, underline: false, strike: false }
    }
}

#[derive(Clone, Copy)]
pub struct Span {
    pub ch: char,
    pub style: Style,
}

struct Glyph {
    tex: usize,
    sx: f32,
    sy: f32,
    sw: f32,
    sh: f32,
    dw: f32,
    dh: f32,
    top: f32,
    advance: f32,
}

pub struct Font {
    textures: Vec<Texture2D>,
    glyphs: HashMap<char, Glyph>,
    advances: HashMap<char, f32>,
    pub report: Vec<String>,
}

pub const PALETTE: [(&str, char, [u8; 3]); 16] = [
    ("black", '0', [0x00, 0x00, 0x00]),
    ("dark_blue", '1', [0x00, 0x00, 0xAA]),
    ("dark_green", '2', [0x00, 0xAA, 0x00]),
    ("dark_aqua", '3', [0x00, 0xAA, 0xAA]),
    ("dark_red", '4', [0xAA, 0x00, 0x00]),
    ("dark_purple", '5', [0xAA, 0x00, 0xAA]),
    ("gold", '6', [0xFF, 0xAA, 0x00]),
    ("gray", '7', [0xAA, 0xAA, 0xAA]),
    ("dark_gray", '8', [0x55, 0x55, 0x55]),
    ("blue", '9', [0x55, 0x55, 0xFF]),
    ("green", 'a', [0x55, 0xFF, 0x55]),
    ("aqua", 'b', [0x55, 0xFF, 0xFF]),
    ("red", 'c', [0xFF, 0x55, 0x55]),
    ("light_purple", 'd', [0xFF, 0x55, 0xFF]),
    ("yellow", 'e', [0xFF, 0xFF, 0x55]),
    ("white", 'f', [0xFF, 0xFF, 0xFF]),
];

fn split_id(id: &str) -> (&str, &str) {
    id.split_once(':').unwrap_or(("minecraft", id))
}

fn collect_providers(res: &Resources, id: &str, out: &mut Vec<Value>, report: &mut Vec<String>, depth: u32) {
    if depth > 5 {
        return;
    }
    let (ns, path) = split_id(id);
    let rel = format!("assets/{ns}/font/{path}.json");
    for (pack, bytes) in res.read_all(&rel) {
        let json: Value = match serde_json::from_slice(&bytes) {
            Ok(v) => v,
            Err(e) => {
                report.push(format!("{pack}: {rel} is not valid JSON ({e}), skipped"));
                continue;
            }
        };
        for p in json["providers"].as_array().into_iter().flatten() {
            if p["type"].as_str() == Some("reference") {
                collect_providers(res, p["id"].as_str().unwrap_or(""), out, report, depth + 1);
            } else {
                out.push(p.clone());
            }
        }
    }
}

fn is_private_use(c: char) -> bool {
    ('\u{E000}'..='\u{F8FF}').contains(&c)
}

impl Font {
    pub fn load(res: &Resources) -> Font {
        let mut font = Font { textures: vec![], glyphs: HashMap::new(), advances: HashMap::new(), report: vec![] };
        let mut providers = Vec::new();
        let mut report = Vec::new();
        collect_providers(res, "minecraft:default", &mut providers, &mut report, 0);

        for p in &providers {
            match p["type"].as_str() {
                Some("space") => {
                    if let Some(map) = p["advances"].as_object() {
                        for (k, v) in map {
                            if let (Some(c), Some(a)) = (k.chars().next(), v.as_f64()) {
                                font.advances.entry(c).or_insert(a as f32);
                            }
                        }
                    }
                }
                Some("bitmap") => font.add_bitmap(res, p, &mut report),
                _ => {}
            }
        }
        report.push(format!("font: {} glyphs from {} sheets", font.glyphs.len(), font.textures.len()));
        font.report = report;
        font
    }

    fn add_bitmap(&mut self, res: &Resources, p: &Value, report: &mut Vec<String>) {
        let rows: Vec<Vec<char>> = p["chars"]
            .as_array()
            .into_iter()
            .flatten()
            .filter_map(|r| r.as_str().map(|s| s.chars().collect()))
            .collect();
        // sheets that only define private-use icons (other packs' GUI tricks) are irrelevant to book text
        if rows.iter().flatten().all(|&c| c == '\0' || is_private_use(c)) {
            return;
        }
        let Some(file) = p["file"].as_str() else { return };
        let (ns, path) = split_id(file);
        let rel = format!("assets/{ns}/textures/{path}");
        let Some((pack, bytes)) = res.find(&rel) else {
            report.push(format!("font sheet {file} not found"));
            return;
        };
        let Ok(img) = Image::from_file_with_format(&bytes, Some(ImageFormat::Png)) else {
            report.push(format!("font sheet {file} ({pack}) is not a readable PNG"));
            return;
        };

        let cols = rows.iter().map(|r| r.len()).max().unwrap_or(0);
        let nrows = rows.len();
        if cols == 0 || nrows == 0 {
            return;
        }
        let cell_w = img.width() / cols;
        let cell_h = img.height() / nrows;
        if cell_w == 0 || cell_h == 0 {
            return;
        }
        let height = p["height"].as_f64().unwrap_or(8.0) as f32;
        let ascent = p["ascent"].as_f64().unwrap_or(7.0) as f32;
        let scale = height / cell_h as f32;

        let tex = Texture2D::from_image(&img);
        tex.set_filter(FilterMode::Nearest);
        let tex_idx = self.textures.len();
        self.textures.push(tex);

        let alpha = |x: usize, y: usize| img.bytes[(y * img.width() + x) * 4 + 3];
        for (r, row) in rows.iter().enumerate() {
            for (c, &ch) in row.iter().enumerate() {
                if ch == '\0' || self.glyphs.contains_key(&ch) {
                    continue;
                }
                // the game measures the rightmost opaque column of each cell
                let mut w = 0;
                'cols: for x in (0..cell_w).rev() {
                    for y in 0..cell_h {
                        if alpha(c * cell_w + x, r * cell_h + y) != 0 {
                            w = x + 1;
                            break 'cols;
                        }
                    }
                }
                if w == 0 {
                    continue;
                }
                self.glyphs.insert(
                    ch,
                    Glyph {
                        tex: tex_idx,
                        sx: (c * cell_w) as f32,
                        sy: (r * cell_h) as f32,
                        sw: w as f32,
                        sh: cell_h as f32,
                        dw: w as f32 * scale,
                        dh: cell_h as f32 * scale,
                        top: 7.0 - ascent,
                        advance: (0.5 + w as f32 * scale).floor() + 1.0,
                    },
                );
            }
        }
    }

    fn glyph_for(&self, ch: char) -> Option<&Glyph> {
        self.glyphs.get(&ch).or_else(|| if self.advances.contains_key(&ch) { None } else { self.glyphs.get(&'?') })
    }

    pub fn advance(&self, ch: char, bold: bool) -> f32 {
        let base = if let Some(g) = self.glyphs.get(&ch) {
            g.advance
        } else if let Some(a) = self.advances.get(&ch) {
            *a
        } else {
            self.glyphs.get(&'?').map(|g| g.advance).unwrap_or(6.0)
        };
        if bold && ch != ' ' {
            base + 1.0
        } else {
            base
        }
    }

    pub fn width(&self, text: &str) -> f32 {
        text.chars().map(|c| self.advance(c, false)).sum()
    }

    /// Legacy `§` codes -> styled characters. A color code clears other formatting, `§r` returns to `base`.
    pub fn parse_legacy(text: &str, base: Style) -> Vec<Span> {
        let mut style = base;
        let mut out = Vec::new();
        let mut it = text.chars();
        while let Some(c) = it.next() {
            if c == '§' {
                let Some(code) = it.next() else { break };
                let code = code.to_ascii_lowercase();
                if let Some((_, _, rgb)) = PALETTE.iter().find(|(_, k, _)| *k == code) {
                    style = Style::plain(*rgb);
                } else {
                    match code {
                        'l' => style.bold = true,
                        'o' => style.italic = true,
                        'n' => style.underline = true,
                        'm' => style.strike = true,
                        'r' => style = base,
                        _ => {}
                    }
                }
                continue;
            }
            out.push(Span { ch: c, style });
        }
        out
    }

    /// Word wrap like the vanilla string splitter: break after the last space, or mid-word if there is none.
    pub fn wrap(&self, spans: &[Span], max: f32) -> Vec<Vec<Span>> {
        let mut lines: Vec<Vec<Span>> = Vec::new();
        let mut cur: Vec<Span> = Vec::new();
        let mut w = 0.0;
        let mut last_space: Option<usize> = None;
        let mut i = 0;
        while i < spans.len() {
            let s = spans[i];
            if s.ch == '\n' {
                lines.push(std::mem::take(&mut cur));
                w = 0.0;
                last_space = None;
                i += 1;
                continue;
            }
            let cw = self.advance(s.ch, s.style.bold);
            if w + cw > max && !cur.is_empty() {
                if let Some(ls) = last_space {
                    let rest = cur.split_off(ls + 1);
                    lines.push(std::mem::replace(&mut cur, rest));
                    w = cur.iter().map(|sp| self.advance(sp.ch, sp.style.bold)).sum();
                    last_space = None;
                } else {
                    lines.push(std::mem::take(&mut cur));
                    w = 0.0;
                }
                continue;
            }
            cur.push(s);
            w += cw;
            if s.ch == ' ' {
                last_space = Some(cur.len() - 1);
            }
            i += 1;
        }
        lines.push(cur);
        lines
    }

    /// Draws one line with its top-left at (x, y) in screen pixels; `s` is the GUI scale.
    pub fn draw_line(&self, spans: &[Span], x: f32, y: f32, s: f32) {
        let mut cx = 0.0;
        for sp in spans {
            let adv = self.advance(sp.ch, sp.style.bold);
            let color = Color::from_rgba(sp.style.color[0], sp.style.color[1], sp.style.color[2], 255);
            if let Some(g) = self.glyph_for(sp.ch) {
                self.draw_glyph(g, x + cx * s, y, s, &sp.style, color);
                if sp.style.bold {
                    self.draw_glyph(g, x + (cx + 1.0) * s, y, s, &sp.style, color);
                }
            }
            if sp.style.underline {
                draw_rectangle(x + (cx - 1.0) * s, y + 8.0 * s, (adv + 1.0) * s, s, color);
            }
            if sp.style.strike {
                draw_rectangle(x + (cx - 1.0) * s, y + 4.0 * s, (adv + 1.0) * s, s, color);
            }
            cx += adv;
        }
    }

    fn draw_glyph(&self, g: &Glyph, x: f32, y: f32, s: f32, style: &Style, color: Color) {
        let tex = &self.textures[g.tex];
        let (tw, th) = (tex.width(), tex.height());
        let x0 = x;
        let x1 = x + g.dw * s;
        let y0 = y + g.top * s;
        let y1 = y0 + g.dh * s;
        let (top_shift, bottom_shift) = if style.italic { (s, -s) } else { (0.0, 0.0) };
        let (u0, u1) = (g.sx / tw, (g.sx + g.sw) / tw);
        let (v0, v1) = (g.sy / th, (g.sy + g.sh) / th);
        let mesh = Mesh {
            vertices: vec![
                Vertex::new(x0 + top_shift, y0, 0.0, u0, v0, color),
                Vertex::new(x1 + top_shift, y0, 0.0, u1, v0, color),
                Vertex::new(x1 + bottom_shift, y1, 0.0, u1, v1, color),
                Vertex::new(x0 + bottom_shift, y1, 0.0, u0, v1, color),
            ],
            indices: vec![0, 1, 2, 0, 2, 3],
            texture: Some(tex.clone()),
        };
        draw_mesh(&mesh);
    }
}
