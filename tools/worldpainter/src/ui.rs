//! Minimal immediate-mode widgets drawn with macroquad primitives.
use macroquad::prelude::*;

const BG: Color = Color::new(0.10, 0.11, 0.13, 0.97);
const WIDGET: Color = Color::new(0.20, 0.22, 0.26, 1.0);
const HOVER: Color = Color::new(0.28, 0.31, 0.37, 1.0);
const ACCENT: Color = Color::new(0.20, 0.45, 0.80, 1.0);

pub struct Ui {
    pub mouse: Vec2,
    pub down: bool,
    pub pressed: bool,
    pub active: Option<u64>,
    pub focus: Option<u64>,
    chars: Vec<char>,
    backspace: bool,
    pub panel: Rect,
    x: f32,
    y: f32,
    w: f32,
    pub tip: Option<String>,
}

fn id_of(label: &str, y: f32) -> u64 {
    let mut h: u64 = 0xcbf2_9ce4_8422_2325;
    for b in label.bytes().chain((y as i32).to_le_bytes()) {
        h ^= b as u64;
        h = h.wrapping_mul(0x100_0000_01b3);
    }
    h
}

impl Ui {
    pub fn new() -> Self {
        Self {
            mouse: Vec2::ZERO,
            down: false,
            pressed: false,
            active: None,
            focus: None,
            chars: vec![],
            backspace: false,
            panel: Rect::new(0.0, 0.0, 0.0, 0.0),
            x: 0.0,
            y: 0.0,
            w: 0.0,
            tip: None,
        }
    }

    pub fn begin_frame(&mut self) {
        let (mx, my) = mouse_position();
        self.mouse = vec2(mx, my);
        self.down = is_mouse_button_down(MouseButton::Left);
        self.pressed = is_mouse_button_pressed(MouseButton::Left);
        if !self.down {
            self.active = None;
        }
        self.chars.clear();
        while let Some(c) = get_char_pressed() {
            if c.is_ascii() && !c.is_ascii_control() {
                self.chars.push(c);
            }
        }
        self.backspace = is_key_pressed(KeyCode::Backspace);
        self.tip = None;
    }

    pub fn over_panel(&self) -> bool {
        self.panel.contains(self.mouse)
    }

    pub fn begin_panel(&mut self, rect: Rect) {
        self.panel = rect;
        draw_rectangle(rect.x, rect.y, rect.w, rect.h, BG);
        draw_line(rect.x + rect.w, rect.y, rect.x + rect.w, rect.y + rect.h, 1.0, Color::new(0.35, 0.37, 0.42, 1.0));
        self.x = rect.x + 10.0;
        self.y = rect.y + 10.0;
        self.w = rect.w - 20.0;
    }

    pub fn gap(&mut self, h: f32) {
        self.y += h;
    }

    pub fn heading(&mut self, t: &str) {
        draw_text(t, self.x, self.y + 14.0, 22.0, Color::new(1.0, 0.85, 0.4, 1.0));
        self.y += 26.0;
    }

    pub fn label(&mut self, t: &str) {
        draw_text(t, self.x, self.y + 12.0, 17.0, LIGHTGRAY);
        self.y += 19.0;
    }

    fn widget_bg(&self, r: Rect, selected: bool) -> Color {
        if selected {
            ACCENT
        } else if r.contains(self.mouse) {
            HOVER
        } else {
            WIDGET
        }
    }

    pub fn button(&mut self, t: &str, selected: bool) -> bool {
        let r = Rect::new(self.x, self.y, self.w, 24.0);
        draw_rectangle(r.x, r.y, r.w, r.h, self.widget_bg(r, selected));
        draw_text(t, r.x + 8.0, r.y + 17.0, 18.0, WHITE);
        self.y += 28.0;
        self.pressed && r.contains(self.mouse)
    }

    /// A row of equally wide buttons; returns the clicked index.
    pub fn row(&mut self, labels: &[&str], sel: Option<usize>) -> Option<usize> {
        let gap = 4.0;
        let bw = (self.w - gap * (labels.len() as f32 - 1.0)) / labels.len() as f32;
        let mut clicked = None;
        for (i, l) in labels.iter().enumerate() {
            let r = Rect::new(self.x + i as f32 * (bw + gap), self.y, bw, 24.0);
            draw_rectangle(r.x, r.y, r.w, r.h, self.widget_bg(r, sel == Some(i)));
            let tw = measure_text(l, None, 16, 1.0).width;
            draw_text(l, r.x + (r.w - tw) * 0.5, r.y + 17.0, 16.0, WHITE);
            if self.pressed && r.contains(self.mouse) {
                clicked = Some(i);
            }
        }
        self.y += 28.0;
        clicked
    }

    pub fn toggle(&mut self, label: &str, v: &mut bool) -> bool {
        let r = Rect::new(self.x, self.y, self.w, 22.0);
        let b = Rect::new(r.x, r.y + 2.0, 18.0, 18.0);
        draw_rectangle(b.x, b.y, b.w, b.h, self.widget_bg(b, *v));
        draw_text(label, r.x + 26.0, r.y + 16.0, 17.0, WHITE);
        self.y += 24.0;
        if self.pressed && r.contains(self.mouse) {
            *v = !*v;
            return true;
        }
        false
    }

    pub fn slider(&mut self, label: &str, v: &mut f32, lo: f32, hi: f32) -> bool {
        let r = Rect::new(self.x, self.y, self.w, 22.0);
        let id = id_of(label, self.y);
        let mut changed = false;
        if self.pressed && r.contains(self.mouse) {
            self.active = Some(id);
        }
        if self.active == Some(id) && self.down {
            let nv = lo + ((self.mouse.x - r.x) / r.w).clamp(0.0, 1.0) * (hi - lo);
            if (nv - *v).abs() > f32::EPSILON {
                *v = nv;
                changed = true;
            }
        }
        draw_rectangle(r.x, r.y, r.w, r.h, if r.contains(self.mouse) { HOVER } else { WIDGET });
        let t = ((*v - lo) / (hi - lo)).clamp(0.0, 1.0);
        draw_rectangle(r.x, r.y, r.w * t, r.h, Color::new(0.20, 0.45, 0.80, 0.75));
        let text = if hi - lo >= 20.0 { format!("{label}: {:.0}", *v) } else { format!("{label}: {:.2}", *v) };
        draw_text(&text, r.x + 6.0, r.y + 16.0, 17.0, WHITE);
        self.y += 25.0;
        changed
    }

    pub fn slider_i(&mut self, label: &str, v: &mut i32, lo: i32, hi: i32) -> bool {
        let r = Rect::new(self.x, self.y, self.w, 22.0);
        let id = id_of(label, self.y);
        let mut changed = false;
        if self.pressed && r.contains(self.mouse) {
            self.active = Some(id);
        }
        if self.active == Some(id) && self.down {
            let nv = (lo as f32 + ((self.mouse.x - r.x) / r.w).clamp(0.0, 1.0) * (hi - lo) as f32).round() as i32;
            if nv != *v {
                *v = nv;
                changed = true;
            }
        }
        draw_rectangle(r.x, r.y, r.w, r.h, if r.contains(self.mouse) { HOVER } else { WIDGET });
        let t = ((*v - lo) as f32 / (hi - lo) as f32).clamp(0.0, 1.0);
        draw_rectangle(r.x, r.y, r.w * t, r.h, Color::new(0.20, 0.45, 0.80, 0.75));
        draw_text(&format!("{label}: {}", *v), r.x + 6.0, r.y + 16.0, 17.0, WHITE);
        self.y += 25.0;
        changed
    }

    pub fn text_field(&mut self, label: &str, s: &mut String) -> bool {
        draw_text(label, self.x, self.y + 12.0, 16.0, GRAY);
        self.y += 15.0;
        let r = Rect::new(self.x, self.y, self.w, 22.0);
        let id = id_of(label, self.y);
        if self.pressed {
            self.focus = if r.contains(self.mouse) { Some(id) } else if self.focus == Some(id) { None } else { self.focus };
        }
        let focused = self.focus == Some(id);
        let mut changed = false;
        if focused {
            for &c in &self.chars {
                s.push(c);
                changed = true;
            }
            if self.backspace && s.pop().is_some() {
                changed = true;
            }
        }
        draw_rectangle(r.x, r.y, r.w, r.h, if focused { Color::new(0.12, 0.14, 0.2, 1.0) } else { WIDGET });
        if focused {
            draw_rectangle_lines(r.x, r.y, r.w, r.h, 2.0, ACCENT);
        }
        let shown = if focused && (get_time() * 2.0) as i64 % 2 == 0 { format!("{s}|") } else { s.clone() };
        draw_text(&shown, r.x + 5.0, r.y + 16.0, 17.0, WHITE);
        self.y += 26.0;
        changed
    }

    /// Grid of colour chips; returns the clicked index. Hover sets a tooltip.
    pub fn swatches(&mut self, colors: &[[u8; 3]], names: &[&str], sel: usize) -> Option<usize> {
        let chip = 26.0;
        let gap = 3.0;
        let cols = ((self.w + gap) / (chip + gap)).floor() as usize;
        let mut clicked = None;
        for (i, c) in colors.iter().enumerate() {
            let r = Rect::new(self.x + (i % cols) as f32 * (chip + gap), self.y + (i / cols) as f32 * (chip + gap), chip, chip);
            draw_rectangle(r.x, r.y, r.w, r.h, Color::from_rgba(c[0], c[1], c[2], 255));
            if i == sel {
                draw_rectangle_lines(r.x - 1.0, r.y - 1.0, r.w + 2.0, r.h + 2.0, 3.0, WHITE);
            }
            if r.contains(self.mouse) {
                self.tip = Some(names[i].to_string());
                if self.pressed {
                    clicked = Some(i);
                }
            }
        }
        self.y += ((colors.len() + cols - 1) / cols) as f32 * (chip + gap) + 4.0;
        clicked
    }

    pub fn draw_tip(&self) {
        if let Some(t) = &self.tip {
            let d = measure_text(t, None, 18, 1.0);
            let (x, y) = (self.mouse.x + 14.0, self.mouse.y + 18.0);
            draw_rectangle(x - 4.0, y - 15.0, d.width + 8.0, 22.0, Color::new(0.0, 0.0, 0.0, 0.85));
            draw_text(t, x, y, 18.0, WHITE);
        }
    }
}
