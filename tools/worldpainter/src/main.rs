mod biomes;
mod export;
mod install;
mod model;
mod noise;
mod novoatlas;
mod render;
mod sim;
mod ui;

use macroquad::prelude::*;
use model::*;
use render::{render_pixels, tree_color, tree_dots, ViewMode, VIEW_MODES};
use sim::World;
use ui::Ui;

const PANEL_W: f32 = 330.0;
const UNDO_LIMIT: usize = 25;

#[derive(Clone, Copy, PartialEq)]
enum Tab {
    Paint,
    Noise,
    Structures,
    View,
    File,
}

#[derive(Clone, Copy, PartialEq)]
enum Tool {
    Biome,
    Channel,
    Erase,
    Naturalize,
    Structure,
}

struct View {
    center: Vec2,
    zoom: f32,
}

struct App {
    proj: Project,
    world: World,
    ui: Ui,
    view: View,
    tab: Tab,
    tool: Tool,
    mode: ViewMode,
    brush_r: f32,
    hardness: f32,
    strength: f32,
    sel_biome: u8,
    sel_channel: usize,
    chan_value: f32,
    erase_only_channel: bool,
    line_mode: bool,
    stroking: bool,
    last_pos: Vec2,
    line_start: Vec2,
    undo: Vec<Paint>,
    img: Image,
    tex: Texture2D,
    img_dirty: bool,
    show_trees: bool,
    show_shade: bool,
    show_grid: bool,
    show_overlay: bool,
    show_structs: bool,
    show_guide: bool,
    sel_type: usize,
    sel_struct: Option<usize>,
    drag_struct: Option<Vec2>,
    noise_ch: usize,
    reprs: Vec<Option<[f32; 5]>>,
    status: String,
    status_time: f64,
    nat: Natural,
    pending: Option<Pending>,
    disable_kubejs: bool,
    instance_root: Option<std::path::PathBuf>,
}

#[derive(Clone, Copy, PartialEq)]
enum Pending {
    SaveAll,
    Install,
    Uninstall,
}

/// Text with a dark outline so it stays readable over any biome colour.
fn shadow_text(t: &str, x: f32, y: f32, size: f32, col: Color) {
    for (dx, dy) in [(-1.0, 0.0), (1.0, 0.0), (0.0, -1.0), (0.0, 1.0)] {
        draw_text(t, x + dx, y + dy, size, BLACK);
    }
    draw_text(t, x, y, size, col);
}

fn window_conf() -> Conf {
    Conf { window_title: "Winterheart World Painter".to_string(), window_width: 1500, window_height: 950, high_dpi: true, ..Default::default() }
}

impl App {
    fn new() -> Self {
        let proj = export::load_project(export::PROJECT_FILE).unwrap_or_else(|_| Project::new());
        let world = World::new(&proj);
        let (nx, nz) = (proj.region.nx, proj.region.nz);
        let img = Image::gen_image_color(nx as u16, nz as u16, BLACK);
        let tex = Texture2D::from_image(&img);
        tex.set_filter(FilterMode::Nearest);
        let mut app = App {
            proj,
            world,
            ui: Ui::new(),
            view: View { center: vec2(0.0, 250.0), zoom: 0.55 },
            tab: Tab::Paint,
            tool: Tool::Biome,
            mode: ViewMode::Biome,
            brush_r: 80.0,
            hardness: 0.5,
            strength: 1.0,
            sel_biome: biomes::b::PLAINS,
            sel_channel: CH_C,
            chan_value: 0.1,
            erase_only_channel: false,
            line_mode: false,
            stroking: false,
            last_pos: Vec2::ZERO,
            line_start: Vec2::ZERO,
            undo: vec![],
            img,
            tex,
            img_dirty: true,
            show_trees: true,
            show_shade: true,
            show_grid: true,
            show_overlay: false,
            show_structs: true,
            show_guide: true,
            sel_type: 0,
            sel_struct: None,
            drag_struct: None,
            noise_ch: 0,
            reprs: (0..biomes::BIOMES.len() as u8).map(biomes::representative).collect(),
            status: "Ready. Right-drag or Space+drag to pan, wheel to zoom.".into(),
            status_time: get_time(),
            nat: Natural { blur: 0.6, noise: 0.12, scale: 60.0, seed: 7 },
            pending: None,
            disable_kubejs: true,
            instance_root: install::detect_root(),
        };
        app.set_status("Loaded.");
        app
    }

    /// Save everything at once: project file, preview maps + structures manifest, and (when the instance is
    /// known) the Starter Structure spawn config.
    fn save_all(&self) -> Result<String, String> {
        export::save_project(&self.proj, export::PROJECT_FILE)?;
        export::export_all(&self.proj, &self.world, export::EXPORT_DIR)?;
        let mut parts = vec![format!("Saved {} + {}/", export::PROJECT_FILE, export::EXPORT_DIR)];
        if let Some(root) = &self.instance_root {
            let [sx, sz] = self.proj.spawn;
            let detail = novoatlas::Detail::new(self.proj.seed);
            let (h, _) = novoatlas::pixel_fields(&self.proj, &self.world, &detail, sx as f32, sz as f32);
            parts.push(install::sync_spawn(root, sx, h.round() as i32 + 1, sz)?);
        }
        Ok(parts.join("; "))
    }

    /// Runs a queued slow action; called at the top of a frame so the "working" status gets drawn first.
    fn run_pending(&mut self) {
        let Some(action) = self.pending.take() else { return };
        let export_dir = std::path::Path::new(export::EXPORT_DIR);
        let backup = std::path::Path::new(install::BACKUP_DIR);
        let result = match action {
            Pending::SaveAll => self.save_all(),
            Pending::Install => self.save_all().and_then(|saved| {
                let exported = novoatlas::export_pack(&self.proj, &self.world, export_dir)?;
                let root = self.instance_root.clone().ok_or("instance root not found")?;
                let installed = install::install(&export_dir.join(install::PACK_NAME), &root, self.disable_kubejs, backup)?;
                Ok(format!("{saved}. {exported}. {installed}"))
            }),
            Pending::Uninstall => {
                let root = self.instance_root.clone().ok_or("instance root not found".to_string());
                root.and_then(|r| install::uninstall(&r, backup))
            }
        };
        self.set_status(&result.unwrap_or_else(|e| format!("Failed: {e}")));
    }

    fn set_status(&mut self, s: &str) {
        self.status = s.to_string();
        self.status_time = get_time();
    }

    // ---- view helpers ----

    fn map_center(&self) -> Vec2 {
        vec2(PANEL_W + (screen_width() - PANEL_W) * 0.5, screen_height() * 0.5)
    }
    fn w2s(&self, w: Vec2) -> Vec2 {
        self.map_center() + (w - self.view.center) * self.view.zoom
    }
    fn s2w(&self, s: Vec2) -> Vec2 {
        self.view.center + (s - self.map_center()) / self.view.zoom
    }

    fn refresh_image(&mut self) {
        render_pixels(&mut self.img.bytes, &self.proj, &self.world, self.mode, self.show_shade, self.show_overlay);
        self.tex.update(&self.img);
        self.img_dirty = false;
    }

    // ---- input on the map ----

    fn handle_map_input(&mut self) {
        let mouse = self.ui.mouse;
        let on_map = !self.ui.over_panel();
        let wpos = self.s2w(mouse);
        let space = is_key_down(KeyCode::Space);

        if on_map {
            let wheel = mouse_wheel().1;
            if wheel != 0.0 {
                let before = self.s2w(mouse);
                self.view.zoom = (self.view.zoom * 1.12f32.powf(wheel.clamp(-3.0, 3.0))).clamp(0.12, 12.0);
                let after = self.s2w(mouse);
                self.view.center += before - after;
            }
        }
        let panning = (on_map || self.stroking) && (is_mouse_button_down(MouseButton::Right) || is_mouse_button_down(MouseButton::Middle) || (space && self.ui.down));
        if panning {
            let d = mouse_delta_position();
            // mouse_delta_position is in normalised device units (inverted, -1..1)
            self.view.center += vec2(d.x * screen_width() * 0.5, d.y * screen_height() * 0.5) / self.view.zoom;
            return;
        }

        if self.ui.focus.is_none() {
            let cmd = is_key_down(KeyCode::LeftSuper) || is_key_down(KeyCode::RightSuper) || is_key_down(KeyCode::LeftControl);
            if cmd && is_key_pressed(KeyCode::Z) {
                self.undo_paint();
            }
            if cmd && is_key_pressed(KeyCode::S) {
                self.pending = Some(Pending::SaveAll);
                self.set_status("Saving...");
            }
            if is_key_pressed(KeyCode::LeftBracket) {
                self.brush_r = (self.brush_r * 0.85).max(8.0);
            }
            if is_key_pressed(KeyCode::RightBracket) {
                self.brush_r = (self.brush_r / 0.85).min(500.0);
            }
            if (is_key_pressed(KeyCode::Delete) || is_key_pressed(KeyCode::Backspace)) && self.tool == Tool::Structure {
                self.delete_selected();
            }
        }

        match self.tool {
            Tool::Structure => self.handle_structure_input(on_map, wpos),
            _ => self.handle_paint_input(on_map, wpos),
        }
    }

    fn paint_op(&self) -> (Vec<(usize, f32)>, bool) {
        match self.tool {
            Tool::Biome => match self.reprs[self.sel_biome as usize] {
                Some(r) => (vec![(CH_C, r[2]), (CH_E, r[3]), (CH_T, r[0]), (CH_H, r[1]), (CH_W, r[4])], false),
                None => (vec![], false),
            },
            Tool::Channel => (vec![(self.sel_channel, self.chan_value)], false),
            _ => (vec![], true),
        }
    }

    fn apply_stroke(&mut self, a: Vec2, b: Vec2) {
        if self.tool == Tool::Naturalize {
            let rect = self.proj.paint.naturalize(&self.proj.region, (a.x, a.y), (b.x, b.y), self.brush_r, self.hardness, self.strength * 0.5, self.nat);
            self.world.recompute_rect(&self.proj, rect);
            self.img_dirty = true;
            return;
        }
        let (targets, erase) = self.paint_op();
        let op = if erase {
            Op::Erase(if self.erase_only_channel { Some(self.sel_channel) } else { None })
        } else {
            Op::Set(&targets)
        };
        let rect = self.proj.paint.stamp(&self.proj.region, (a.x, a.y), (b.x, b.y), self.brush_r, self.hardness, self.strength, &op);
        self.world.recompute_rect(&self.proj, rect);
        self.img_dirty = true;
    }

    fn handle_paint_input(&mut self, on_map: bool, wpos: Vec2) {
        if self.ui.pressed && on_map && !self.stroking {
            if self.undo.len() >= UNDO_LIMIT {
                self.undo.remove(0);
            }
            self.undo.push(self.proj.paint.clone());
            self.stroking = true;
            self.last_pos = wpos;
            self.line_start = wpos;
            if !self.line_mode {
                self.apply_stroke(wpos, wpos);
            }
        }
        if self.stroking {
            if self.ui.down {
                if !self.line_mode {
                    self.apply_stroke(self.last_pos, wpos);
                    self.last_pos = wpos;
                }
            } else {
                if self.line_mode {
                    self.apply_stroke(self.line_start, wpos);
                }
                self.stroking = false;
            }
        }
    }

    fn undo_paint(&mut self) {
        if let Some(p) = self.undo.pop() {
            self.proj.paint = p;
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
            self.set_status("Undid last paint stroke.");
        }
    }

    fn struct_at(&self, w: Vec2) -> Option<usize> {
        self.proj.structures.iter().enumerate().rev().find_map(|(i, s)| {
            let (fw, fd) = s.footprint(&self.proj.types);
            let half = vec2(fw as f32, fd as f32) * 0.5;
            let pad = 6.0 / self.view.zoom;
            ((w.x - s.x as f32).abs() <= half.x + pad && (w.y - s.z as f32).abs() <= half.y + pad).then_some(i)
        })
    }

    fn handle_structure_input(&mut self, on_map: bool, wpos: Vec2) {
        if self.ui.pressed && on_map {
            if let Some(i) = self.struct_at(wpos) {
                self.sel_struct = Some(i);
                let s = &self.proj.structures[i];
                self.drag_struct = Some(vec2(s.x as f32, s.z as f32) - wpos);
            } else {
                let snap = |v: f32| (v / 4.0).round() as i32 * 4;
                self.proj.structures.push(StructInst { ty: self.sel_type, x: snap(wpos.x), z: snap(wpos.y), rot: 0 });
                self.sel_struct = Some(self.proj.structures.len() - 1);
            }
        }
        if let (Some(off), Some(i)) = (self.drag_struct, self.sel_struct) {
            if self.ui.down {
                let snap = |v: f32| (v / 4.0).round() as i32 * 4;
                let target = wpos + off;
                self.proj.structures[i].x = snap(target.x);
                self.proj.structures[i].z = snap(target.y);
            } else {
                self.drag_struct = None;
            }
        }
    }

    fn delete_selected(&mut self) {
        if let Some(i) = self.sel_struct.take() {
            if i < self.proj.structures.len() {
                self.proj.structures.remove(i);
            }
        }
    }

    // ---- drawing the map ----

    fn draw_map(&mut self) {
        clear_background(Color::new(0.06, 0.07, 0.09, 1.0));
        if self.img_dirty {
            self.refresh_image();
        }
        let r = &self.proj.region;
        let tl = self.w2s(vec2(r.min_x as f32, r.min_z as f32));
        let size = vec2((r.max_x() - r.min_x) as f32, (r.max_z() - r.min_z) as f32) * self.view.zoom;
        draw_texture_ex(&self.tex, tl.x, tl.y, WHITE, DrawTextureParams { dest_size: Some(size), ..Default::default() });

        let s = r.cell as f32 * self.view.zoom;
        if self.show_trees && s >= 2.0 && self.mode == ViewMode::Biome {
            self.draw_trees(s);
        }
        if self.show_grid {
            self.draw_grid();
        }
        if self.show_guide {
            let g = vec2((self.proj.spawn[0] + self.proj.guide_offset[0]) as f32, (self.proj.spawn[1] + self.proj.guide_offset[1]) as f32);
            let sp = self.w2s(vec2(self.proj.spawn[0] as f32, self.proj.spawn[1] as f32));
            let gp = self.w2s(g);
            draw_line(sp.x, sp.y, gp.x, gp.y, 1.5, Color::new(1.0, 0.6, 0.1, 0.8));
            draw_circle_lines(gp.x, gp.y, self.proj.guide_radius as f32 * self.view.zoom, 2.0, Color::new(1.0, 0.6, 0.1, 0.95));
            shadow_text(&format!("target: {} S +-{}", self.proj.guide_offset[1], self.proj.guide_radius), gp.x + 8.0, gp.y - self.proj.guide_radius as f32 * self.view.zoom - 6.0, 18.0, Color::new(1.0, 0.75, 0.25, 1.0));
        }
        if self.show_structs {
            self.draw_structures();
        }
        // spawn
        let sp = self.w2s(vec2(self.proj.spawn[0] as f32, self.proj.spawn[1] as f32));
        draw_circle(sp.x, sp.y, 6.0, RED);
        draw_circle_lines(sp.x, sp.y, 6.0, 2.0, WHITE);
        shadow_text("Spawn", sp.x + 10.0, sp.y - 6.0, 18.0, WHITE);
    }

    fn visible_cells(&self) -> (usize, usize, usize, usize) {
        let r = &self.proj.region;
        let a = self.s2w(vec2(PANEL_W, 0.0));
        let b = self.s2w(vec2(screen_width(), screen_height()));
        let to_i = |x: f32| (((x - r.min_x as f32) / r.cell as f32).floor().max(0.0) as usize).min(r.nx - 1);
        let to_j = |z: f32| (((z - r.min_z as f32) / r.cell as f32).floor().max(0.0) as usize).min(r.nz - 1);
        (to_i(a.x), to_j(a.y), to_i(b.x), to_j(b.y))
    }

    fn draw_trees(&self, s: f32) {
        let (i0, j0, i1, j1) = self.visible_cells();
        let nx = self.proj.region.nx;
        let px = (s * 0.3).clamp(1.5, 7.0);
        for j in j0..=j1 {
            for i in i0..=i1 {
                let cell = &self.world.cells[j * nx + i];
                for dot in tree_dots(i, j, cell, self.proj.sea_level).into_iter().flatten() {
                    let c = tree_color(dot.kind);
                    let col = Color::from_rgba(c[0], c[1], c[2], 255);
                    let wx = self.proj.region.min_x as f32 + (i as f32 + dot.fx) * self.proj.region.cell as f32;
                    let wz = self.proj.region.min_z as f32 + (j as f32 + dot.fy) * self.proj.region.cell as f32;
                    let p = self.w2s(vec2(wx, wz));
                    if s >= 9.0 && dot.kind == biomes::TreeKind::Conifer {
                        draw_triangle(vec2(p.x, p.y - px), vec2(p.x - px * 0.7, p.y + px * 0.6), vec2(p.x + px * 0.7, p.y + px * 0.6), col);
                    } else if s >= 9.0 {
                        draw_circle(p.x, p.y, px * 0.6, col);
                    } else {
                        draw_rectangle(p.x - px * 0.5, p.y - px * 0.5, px, px, col);
                    }
                }
            }
        }
    }

    fn draw_grid(&self) {
        let step = 256.0;
        let r = &self.proj.region;
        let col = Color::new(1.0, 1.0, 1.0, 0.12);
        let top = self.w2s(vec2(0.0, r.min_z as f32)).y.max(0.0);
        let bottom = self.w2s(vec2(0.0, r.max_z() as f32)).y.min(screen_height());
        let left = self.w2s(vec2(r.min_x as f32, 0.0)).x.max(PANEL_W);
        let right = self.w2s(vec2(r.max_x() as f32, 0.0)).x.min(screen_width());
        let mut x = (r.min_x as f32 / step).ceil() * step;
        while x <= r.max_x() as f32 {
            let sx = self.w2s(vec2(x, 0.0)).x;
            if sx >= PANEL_W && sx <= screen_width() {
                draw_line(sx, top, sx, bottom, 1.0, col);
                draw_text(&format!("{}", x as i32), sx + 3.0, top + 14.0, 15.0, Color::new(1.0, 1.0, 1.0, 0.6));
            }
            x += step;
        }
        let mut z = (r.min_z as f32 / step).ceil() * step;
        while z <= r.max_z() as f32 {
            let sy = self.w2s(vec2(0.0, z)).y;
            if sy >= 0.0 && sy <= screen_height() {
                draw_line(left, sy, right, sy, 1.0, col);
                draw_text(&format!("z{}", z as i32), left + 4.0, sy - 3.0, 15.0, Color::new(1.0, 1.0, 1.0, 0.6));
            }
            z += step;
        }
    }

    fn draw_structures(&self) {
        for (i, s) in self.proj.structures.iter().enumerate() {
            let t = &self.proj.types[s.ty.min(self.proj.types.len() - 1)];
            let (fw, fd) = s.footprint(&self.proj.types);
            let c = self.w2s(vec2(s.x as f32, s.z as f32));
            let size = vec2(fw as f32, fd as f32) * self.view.zoom;
            let size = size.max(Vec2::splat(9.0));
            let col = Color::from_rgba(t.color[0], t.color[1], t.color[2], 255);
            draw_rectangle(c.x - size.x * 0.5, c.y - size.y * 0.5, size.x, size.y, Color::new(col.r, col.g, col.b, 0.45));
            let sel = self.sel_struct == Some(i);
            draw_rectangle_lines(c.x - size.x * 0.5, c.y - size.y * 0.5, size.x, size.y, if sel { 3.0 } else { 2.0 }, if sel { WHITE } else { col });
            // facing notch on the "front" edge (rot 0 = north edge)
            let (nx, ny) = match s.rot % 4 {
                0 => (0.0, -1.0),
                1 => (1.0, 0.0),
                2 => (0.0, 1.0),
                _ => (-1.0, 0.0),
            };
            draw_circle(c.x + nx * size.x * 0.5, c.y + ny * size.y * 0.5, 3.5, col);
            shadow_text(&t.name, c.x - size.x * 0.5, c.y - size.y * 0.5 - 5.0, 17.0, WHITE);
        }
    }

    fn draw_brush_preview(&self) {
        if self.ui.over_panel() || self.tool == Tool::Structure {
            return;
        }
        let m = self.ui.mouse;
        let r = self.brush_r * self.view.zoom;
        draw_circle_lines(m.x, m.y, r, 2.0, WHITE);
        draw_circle_lines(m.x, m.y, r * self.hardness, 1.0, Color::new(1.0, 1.0, 1.0, 0.5));
        if self.line_mode && self.stroking {
            let a = self.w2s(self.line_start);
            draw_line(a.x, a.y, m.x, m.y, (self.brush_r * 2.0 * self.view.zoom).max(1.0), Color::new(1.0, 1.0, 1.0, 0.18));
            draw_line(a.x, a.y, m.x, m.y, 2.0, WHITE);
        }
    }

    fn draw_info_box(&self) {
        if self.ui.over_panel() {
            return;
        }
        let w = self.s2w(self.ui.mouse);
        let mut lines = vec![format!("x {:.0}  z {:.0}   (from spawn: {:+.0}, {:+.0})", w.x, w.y, w.x - self.proj.spawn[0] as f32, w.y - self.proj.spawn[1] as f32)];
        if let Some(c) = self.world.at_block(&self.proj, w.x, w.y) {
            lines.push(format!("{}   height ~{:.0}", biomes::biome(c.biome).id, c.height));
            lines.push(format!("cont {:+.2}  eros {:+.2}  pv {:+.2}  (w {:+.2})", c.p[CH_C], c.p[CH_E], c.pv, c.p[CH_W]));
            lines.push(format!("temp {:+.2}  humid {:+.2}", c.p[CH_T], c.p[CH_H]));
        } else {
            lines.push("outside the planned region".into());
        }
        let (x, y) = (PANEL_W + 10.0, screen_height() - 12.0 - lines.len() as f32 * 20.0);
        draw_rectangle(x - 6.0, y - 4.0, 480.0, lines.len() as f32 * 20.0 + 10.0, Color::new(0.0, 0.0, 0.0, 0.7));
        for (k, l) in lines.iter().enumerate() {
            draw_text(l, x, y + 14.0 + k as f32 * 20.0, 18.0, WHITE);
        }
    }

    fn draw_compass(&self) {
        let c = vec2(screen_width() - 46.0, 56.0);
        draw_circle(c.x, c.y, 28.0, Color::new(0.0, 0.0, 0.0, 0.55));
        draw_triangle(vec2(c.x, c.y - 22.0), vec2(c.x - 7.0, c.y), vec2(c.x + 7.0, c.y), WHITE);
        draw_text("N", c.x - 5.0, c.y - 30.0, 20.0, WHITE);
        shadow_text("S = +Z", c.x - 27.0, c.y + 46.0, 17.0, Color::new(1.0, 0.85, 0.5, 1.0));
    }

    // ---- panel ----

    fn draw_panel(&mut self) {
        self.ui.begin_panel(Rect::new(0.0, 0.0, PANEL_W, screen_height()));
        let tab_labels = ["Paint", "Noise", "Struct", "View", "File"];
        let cur = [Tab::Paint, Tab::Noise, Tab::Structures, Tab::View, Tab::File].iter().position(|t| *t == self.tab);
        if let Some(i) = self.ui.row(&tab_labels, cur) {
            self.tab = [Tab::Paint, Tab::Noise, Tab::Structures, Tab::View, Tab::File][i];
            if self.tab == Tab::Structures {
                self.tool = Tool::Structure;
            } else if self.tool == Tool::Structure {
                self.tool = Tool::Biome;
            }
        }
        self.ui.gap(6.0);
        match self.tab {
            Tab::Paint => self.paint_tab(),
            Tab::Noise => self.noise_tab(),
            Tab::Structures => self.structures_tab(),
            Tab::View => self.view_tab(),
            Tab::File => self.file_tab(),
        }
        // status line
        if get_time() - self.status_time < 12.0 {
            draw_text(&self.status, 10.0, screen_height() - 10.0, 16.0, Color::new(0.7, 0.9, 0.7, 1.0));
        }
    }

    fn paint_tab(&mut self) {
        self.ui.heading("Paint");
        let tools = ["Biome", "Chan", "Erase", "Natur"];
        let cur = match self.tool {
            Tool::Biome => Some(0),
            Tool::Channel => Some(1),
            Tool::Erase => Some(2),
            Tool::Naturalize => Some(3),
            Tool::Structure => None,
        };
        if let Some(i) = self.ui.row(&tools, cur) {
            self.tool = [Tool::Biome, Tool::Channel, Tool::Erase, Tool::Naturalize][i];
        }
        self.ui.slider("Brush radius (blocks)", &mut self.brush_r, 8.0, 500.0);
        self.ui.slider("Hardness", &mut self.hardness, 0.0, 1.0);
        self.ui.slider("Strength", &mut self.strength, 0.05, 1.0);
        self.ui.toggle("Line mode (drag a corridor)", &mut self.line_mode);
        self.ui.gap(4.0);
        match self.tool {
            Tool::Biome => {
                let colors: Vec<[u8; 3]> = biomes::BIOMES.iter().map(|b| b.color).collect();
                let names: Vec<&str> = biomes::BIOMES.iter().map(|b| b.name).collect();
                if let Some(i) = self.ui.swatches(&colors, &names, self.sel_biome as usize) {
                    self.sel_biome = i as u8;
                }
                let b = biomes::biome(self.sel_biome);
                self.ui.label(&format!("{}", b.name));
                match self.reprs[self.sel_biome as usize] {
                    Some(r) => {
                        self.ui.label(&format!("sets cont {:+.2} eros {:+.2} wei {:+.2}", r[2], r[3], r[4]));
                        self.ui.label(&format!("temp {:+.2} humid {:+.2}", r[0], r[1]));
                    }
                    None => self.ui.label("(no climate point found)"),
                }
                self.ui.label("Paint sets climate; soft edges blend");
                self.ui.label("into neighbours. Hardness 1 = sharp.");
            }
            Tool::Channel => {
                let names: Vec<&str> = CH_NAMES.to_vec();
                if let Some(i) = self.ui.row(&["Cont", "Eros", "Temp"], Some(self.sel_channel).filter(|c| *c < 3)) {
                    self.sel_channel = i;
                }
                if let Some(i) = self.ui.row(&["Humid", "Weird", "Height"], Some(self.sel_channel).filter(|c| *c >= 3).map(|c| c - 3)) {
                    self.sel_channel = i + 3;
                }
                let (lo, hi) = CH_RANGE[self.sel_channel];
                self.chan_value = self.chan_value.clamp(lo, hi);
                self.ui.slider(&format!("{} value", names[self.sel_channel]), &mut self.chan_value, lo, hi);
                self.ui.label("Height bias is in blocks, added on top.");
            }
            Tool::Erase => {
                self.ui.toggle("Only the selected channel", &mut self.erase_only_channel);
                if self.erase_only_channel {
                    if let Some(i) = self.ui.row(&["Cont", "Eros", "Temp"], Some(self.sel_channel).filter(|c| *c < 3)) {
                        self.sel_channel = i;
                    }
                    if let Some(i) = self.ui.row(&["Humid", "Weird", "Height"], Some(self.sel_channel).filter(|c| *c >= 3).map(|c| c - 3)) {
                        self.sel_channel = i + 3;
                    }
                }
                self.ui.label("Erasing restores the generated noise.");
            }
            Tool::Naturalize => {
                self.ui.slider("Blur", &mut self.nat.blur, 0.0, 1.0);
                self.ui.slider("Noise", &mut self.nat.noise, 0.0, 0.5);
                self.ui.slider("Noise scale (blocks)", &mut self.nat.scale, 10.0, 400.0);
                let mut seed = self.nat.seed as i32;
                if self.ui.slider_i("Noise seed", &mut seed, 0, 99) {
                    self.nat.seed = seed as u32;
                }
                if self.ui.button("Naturalize ALL painted area", false) {
                    self.undo.push(self.proj.paint.clone());
                    let r = &self.proj.region;
                    let c = ((r.min_x + r.max_x()) as f32 * 0.5, (r.min_z + r.max_z()) as f32 * 0.5);
                    let big = (r.max_x() - r.min_x) as f32;
                    let rect = self.proj.paint.naturalize(r, c, c, big, 1.0, 1.0, self.nat);
                    self.world.recompute_rect(&self.proj, rect);
                    self.img_dirty = true;
                    self.set_status("Naturalized all paint.");
                }
                self.ui.label("Brush: blurs paint, adds noise,");
                self.ui.label("roughens edges. Paint over a corridor");
                self.ui.label("a few times; Cmd+Z undoes a stroke.");
            }
            Tool::Structure => {}
        }
        self.ui.gap(6.0);
        self.ui.label("[ ] brush size  Cmd+Z undo  Cmd+S save");
    }

    fn noise_tab(&mut self) {
        self.ui.heading("Noise");
        let mut all = false;
        let mut seed = self.proj.seed as i32;
        if self.ui.slider_i("Seed", &mut seed, 0, 99999) {
            self.proj.seed = seed as u32;
            all = true;
        }
        if self.ui.button("Randomize seed", false) {
            self.proj.seed = macroquad::rand::gen_range(0, 99999u32);
            all = true;
        }
        if self.ui.slider("Sea level", &mut self.proj.sea_level, 40.0, 90.0) {
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
        }
        if self.ui.slider("Edge ocean width", &mut self.proj.edge_width, 32.0, 800.0) {
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
        }
        if self.ui.toggle("Winter palette (snowy biomes only)", &mut self.proj.winter_palette) {
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
        }
        self.ui.gap(4.0);
        self.ui.label("Terrain detail (added at export):");
        self.ui.slider("Fine amplitude (blocks)", &mut self.proj.detail_amp, 0.0, 20.0);
        self.ui.slider("Fine wavelength", &mut self.proj.detail_scale, 8.0, 120.0);
        let mut oct = self.proj.detail_octaves as i32;
        if self.ui.slider_i("Fine octaves", &mut oct, 1, 8) {
            self.proj.detail_octaves = oct as u32;
        }
        self.ui.slider("Fine persistence", &mut self.proj.detail_persistence, 0.2, 0.85);
        self.ui.slider("Ridged blend", &mut self.proj.detail_ridged, 0.0, 1.0);
        self.ui.slider("Mid undulation (blocks)", &mut self.proj.mid_amp, 0.0, 30.0);
        self.ui.slider("Mid wavelength", &mut self.proj.mid_scale, 40.0, 300.0);
        self.ui.slider("Rougher where rugged", &mut self.proj.rough_by_erosion, 0.0, 1.0);
        self.ui.gap(4.0);
        all |= self.ui.slider("Domain warp (blocks)", &mut self.proj.warp_strength, 0.0, 400.0);
        all |= self.ui.slider("Warp scale", &mut self.proj.warp_scale, 50.0, 1500.0);
        self.ui.gap(4.0);
        let labels = ["Cont", "Eros", "Temp", "Humid", "Weird"];
        if let Some(i) = self.ui.row(&labels, Some(self.noise_ch)) {
            self.noise_ch = i;
        }
        self.ui.label(&format!("{} noise", CH_NAMES[self.noise_ch]));
        let ch = self.noise_ch;
        let cfg = &mut self.proj.noise[ch];
        let mut dirty = false;
        dirty |= self.ui.slider("Scale (blocks)", &mut cfg.scale, 60.0, 4000.0);
        let mut oct = cfg.octaves as i32;
        if self.ui.slider_i("Octaves", &mut oct, 1, 8) {
            cfg.octaves = oct as u32;
            dirty = true;
        }
        dirty |= self.ui.slider("Persistence", &mut cfg.persistence, 0.1, 0.9);
        dirty |= self.ui.slider("Lacunarity", &mut cfg.lacunarity, 1.5, 3.5);
        dirty |= self.ui.slider("Contrast", &mut cfg.contrast, 0.2, 5.0);
        dirty |= self.ui.slider("Amplitude", &mut cfg.amplitude, 0.0, 2.0);
        dirty |= self.ui.slider("Bias", &mut cfg.bias, -1.0, 1.0);
        let mut so = cfg.seed_offset as i32;
        if self.ui.slider_i("Seed offset", &mut so, 0, 99) {
            cfg.seed_offset = so as u32;
            dirty = true;
        }
        if self.ui.button("Reset this channel", false) {
            self.proj.noise[ch] = Project::new().noise[ch].clone();
            dirty = true;
        }
        self.ui.gap(4.0);
        self.ui.label("value = bias + amplitude * contrast * fbm");
        self.ui.label("Clamped to vanilla climate ranges.");
        if all {
            self.world.recompute_all(&self.proj);
            self.img_dirty = true;
        } else if dirty {
            self.world.recompute_raw(&self.proj, ch);
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
        }
    }

    fn structures_tab(&mut self) {
        self.ui.heading("Structures");
        self.ui.label("Click map: place / select. Drag: move.");
        self.ui.label("Types (click to choose):");
        for i in 0..self.proj.types.len() {
            let name = self.proj.types[i].name.clone();
            if self.ui.button(&name, self.sel_type == i) {
                self.sel_type = i;
            }
        }
        if self.ui.button("+ New type", false) {
            self.proj.types.push(StructType { name: format!("Structure {}", self.proj.types.len() + 1), id: "winterheart:new_structure".into(), w: 32, d: 32, color: [120, 220, 255] });
            self.sel_type = self.proj.types.len() - 1;
        }
        self.ui.gap(4.0);
        let t = &mut self.proj.types[self.sel_type];
        self.ui.text_field("Name", &mut t.name);
        self.ui.text_field("Structure / template id", &mut t.id);
        let (mut w, mut d) = (t.w, t.d);
        if self.ui.slider_i("Footprint X", &mut w, 4, 256) {
            t.w = w;
        }
        if self.ui.slider_i("Footprint Z", &mut d, 4, 256) {
            t.d = d;
        }
        self.ui.gap(4.0);
        if let Some(i) = self.sel_struct.filter(|i| *i < self.proj.structures.len()) {
            self.ui.heading("Selected");
            let (mut x, mut z) = (self.proj.structures[i].x, self.proj.structures[i].z);
            if self.ui.slider_i("X", &mut x, self.proj.region.min_x, self.proj.region.max_x()) {
                self.proj.structures[i].x = x;
            }
            if self.ui.slider_i("Z", &mut z, self.proj.region.min_z, self.proj.region.max_z()) {
                self.proj.structures[i].z = z;
            }
            let (sx, sz) = (self.proj.spawn[0], self.proj.spawn[1]);
            self.ui.label(&format!("{} blocks {} of spawn, {} {}", (z - sz).abs(), if z >= sz { "south" } else { "north" }, (x - sx).abs(), if x >= sx { "east" } else { "west" }));
            let kinds: Vec<String> = self.proj.types.iter().map(|t| t.name.clone()).collect();
            let ty = self.proj.structures[i].ty;
            if self.ui.button(&format!("Type: {} (click to cycle)", kinds[ty.min(kinds.len() - 1)]), false) {
                self.proj.structures[i].ty = (ty + 1) % kinds.len();
            }
            if self.ui.button("Rotate 90 deg", false) {
                self.proj.structures[i].rot = (self.proj.structures[i].rot + 1) % 4;
            }
            if self.ui.button("Delete (or Del key)", false) {
                self.delete_selected();
            }
        }
        self.ui.gap(4.0);
        self.ui.slider_i("Guide: blocks south of spawn", &mut self.proj.guide_offset[1], 100, 1000);
        self.ui.slider_i("Guide radius", &mut self.proj.guide_radius, 20, 300);
    }

    fn view_tab(&mut self) {
        self.ui.heading("View");
        let labels: Vec<&str> = VIEW_MODES.iter().map(|m| m.1).collect();
        let cur = VIEW_MODES.iter().position(|m| m.0 == self.mode);
        let (a, b) = labels.split_at(4);
        if let Some(i) = self.ui.row(a, cur.filter(|c| *c < 4)) {
            self.mode = VIEW_MODES[i].0;
            self.img_dirty = true;
        }
        if let Some(i) = self.ui.row(b, cur.filter(|c| *c >= 4).map(|c| c - 4)) {
            self.mode = VIEW_MODES[i + 4].0;
            self.img_dirty = true;
        }
        self.ui.gap(4.0);
        self.img_dirty |= self.ui.toggle("Hillshade", &mut self.show_shade);
        self.ui.toggle("Tree pixels", &mut self.show_trees);
        self.ui.toggle("Grid (256 blocks)", &mut self.show_grid);
        self.img_dirty |= self.ui.toggle("Highlight painted areas", &mut self.show_overlay);
        self.ui.toggle("Structures", &mut self.show_structs);
        self.ui.toggle("Escape target ring", &mut self.show_guide);
        self.ui.gap(8.0);
        self.ui.label("Colours: blue = negative, red = positive");
        self.ui.label("(Contin./Erosion/PV/Temp/Humid modes)");
        if self.ui.button("Reset view", false) {
            self.view = View { center: vec2(0.0, 250.0), zoom: 0.55 };
        }
    }

    fn file_tab(&mut self) {
        self.ui.heading("File");
        if self.ui.button("Save all  (Cmd+S)", false) {
            self.pending = Some(Pending::SaveAll);
            self.set_status("Saving...");
        }
        if self.ui.button("Load project", false) {
            match export::load_project(export::PROJECT_FILE) {
                Ok(p) => {
                    self.proj = p;
                    self.world = World::new(&self.proj);
                    self.undo.clear();
                    self.sel_struct = None;
                    self.img_dirty = true;
                    self.set_status("Loaded project.");
                }
                Err(e) => self.set_status(&format!("Load failed: {e}")),
            }
        }
        self.ui.label("Save all = project, preview maps,");
        self.ui.label("structures.json, spawn config.");
        self.ui.gap(6.0);
        self.ui.heading("Modpack");
        match &self.instance_root {
            Some(r) => self.ui.label(&format!("Instance: {}", r.file_name().map(|n| n.to_string_lossy().to_string()).unwrap_or_default())),
            None => self.ui.label("Instance not found (run from the pack)"),
        }
        self.ui.toggle("Move KubeJS overworld.json aside", &mut self.disable_kubejs);
        if self.ui.button("Save all + install to modpack", false) && self.instance_root.is_some() {
            self.pending = Some(Pending::Install);
            self.set_status("Saving and installing (can take a few seconds)...");
        }
        if self.ui.button("Uninstall from modpack", false) && self.instance_root.is_some() {
            self.pending = Some(Pending::Uninstall);
        }
        self.ui.gap(8.0);
        self.ui.label(&format!("Map size: {} blocks (1 px = 1 block)", self.proj.region.nx as i32 * self.proj.region.cell));
        let sizes = [2048, 3072, 4096, 6144];
        if let Some(i) = self.ui.row(&["2048", "3072", "4096", "6144"], None) {
            self.undo.clear();
            self.proj.resize(sizes[i]);
            self.world = World::new(&self.proj);
            self.img_dirty = true;
            self.set_status(&format!("Resized to {} blocks.", sizes[i]));
        }
        self.ui.gap(8.0);
        if self.ui.button("Re-apply corridor preset", false) {
            self.undo.push(self.proj.paint.clone());
            self.proj.apply_corridor_preset();
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
        }
        if self.ui.button("Clear all paint", false) {
            self.undo.push(self.proj.paint.clone());
            self.proj.paint.clear();
            self.world.recompute_cells(&self.proj);
            self.img_dirty = true;
        }
        if self.ui.button("New project (defaults)", false) {
            self.proj = Project::new();
            self.world = World::new(&self.proj);
            self.undo.clear();
            self.sel_struct = None;
            self.img_dirty = true;
        }
        self.ui.gap(8.0);
        self.ui.label("Files go in the working directory");
        self.ui.label("(tools/worldpainter).");
    }
}

async fn gui() {
    let mut app = App::new();
    loop {
        app.run_pending();
        app.ui.begin_frame();
        app.handle_map_input();
        app.draw_map();
        app.draw_brush_preview();
        app.draw_info_box();
        app.draw_compass();
        app.draw_panel();
        app.ui.draw_tip();
        next_frame().await;
    }
}

fn headless(args: &[String]) {
    let p = Project::new();
    let w = World::new(&p);
    let mode = if args.iter().any(|a| a == "--height") { ViewMode::Height } else { ViewMode::Biome };
    let path = args.iter().skip_while(|a| *a != "--png").nth(1).cloned().unwrap_or_else(|| "out.png".into());
    let (bytes, wd, ht) = render::render_scaled(&p, &w, mode, 3, true, true);
    export::save_png(std::path::Path::new(&path), bytes, wd, ht);
    println!("wrote {path}");
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if let Some(dir) = args.iter().skip_while(|a| *a != "--export-novoatlas").nth(1) {
        let p = export::load_project(export::PROJECT_FILE).unwrap_or_else(|_| Project::new());
        let w = World::new(&p);
        match novoatlas::export_pack(&p, &w, std::path::Path::new(dir)) {
            Ok(m) => println!("{m}"),
            Err(e) => eprintln!("export failed: {e}"),
        }
    } else if args.iter().any(|a| a == "--png") {
        headless(&args);
    } else {
        macroquad::Window::from_config(window_conf(), gui());
    }
}
