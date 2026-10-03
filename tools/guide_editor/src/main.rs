//! Winterheart guide editor.
//!
//! Edits kubejs/server_scripts/questing/guides/guides.json. Every valid edit is written back automatically
//! and the game's guide engine hot-reloads it. The page preview is drawn with the textures and font from the
//! instance's enabled resource packs (options.txt), so it looks the way the book does in game.

mod assets;
mod font;
mod model;
mod registry;

use assets::Resources;
use egui_macroquad::egui::{self, Color32, RichText};
use font::{Font, Span, Style, PALETTE};
use macroquad::prelude::*;
use model::{Book, Domain, Guide, MKind, Matcher, Page, Problem, Trigger, KINDS};
use registry::Registry;
use std::path::{Path, PathBuf};

const GUIDES_REL: &str = "kubejs/server_scripts/questing/guides/guides.json";
const PROBE_REL: &str = "kubejs/probe/generated/globals.d.ts";
const TIME_SYSTEM_REL: &str = "kubejs/server_scripts/mechanics/time_system.js";
const PROXIMITY_REL: &str = "kubejs/server_scripts/mechanics/proximity.js";
const BOOK_TEXTURE: &str = "assets/minecraft/textures/gui/book.png";

// vanilla BookViewScreen geometry, in GUI pixels relative to the book texture's top-left
const BOOK_SIZE: f32 = 192.0;
const TEXT_X: f32 = 36.0;
const TEXT_Y: f32 = 30.0;
const TEXT_WIDTH: f32 = 114.0;
const LINE_HEIGHT: f32 = 9.0;
const MAX_LINES: usize = 14; // 128 / 9
const INDICATOR_Y: f32 = 16.0;
const INDICATOR_RIGHT_MARGIN: f32 = 44.0;
const BUTTON_W: f32 = 23.0;
const BUTTON_H: f32 = 13.0;
const BACK_BUTTON_X: f32 = 43.0;
const FORWARD_BUTTON_X: f32 = 116.0;
const BUTTON_Y: f32 = 157.0;

const BLACK: Style = Style::plain([0, 0, 0]);
const TITLE: Style = Style { color: [0xAA, 0, 0], bold: true, italic: false, underline: false, strike: false };

const RED: Color32 = Color32::from_rgb(235, 90, 90);
const GREEN: Color32 = Color32::from_rgb(110, 200, 120);
const AMBER: Color32 = Color32::from_rgb(230, 180, 70);

fn window_conf() -> Conf {
    Conf {
        window_title: "Winterheart Guide Editor".into(),
        window_width: 1700,
        window_height: 1000,
        high_dpi: true,
        ..Default::default()
    }
}

/// Time phases come from the PHASES table in time_system.js (plus ETERNAL_NIGHT, which it uses as a phase too);
/// proximity rule ids are the `id: '...'` entries in proximity.js. Both are read from the scripts so they never drift.
fn script_lists(root: &Path) -> (Vec<String>, Vec<String>) {
    let mut phases = Vec::new();
    if let Ok(text) = std::fs::read_to_string(root.join(TIME_SYSTEM_REL)) {
        let mut in_phases = false;
        for line in text.lines() {
            let t = line.trim();
            if t.starts_with("PHASES:") {
                in_phases = true;
            } else if in_phases && t.starts_with('}') {
                in_phases = false;
            } else if in_phases {
                if let Some((name, _)) = t.split_once(':') {
                    if !name.is_empty() && name.chars().all(|c| c.is_ascii_uppercase() || c == '_') {
                        phases.push(name.to_string());
                    }
                }
            }
        }
        if text.contains("'ETERNAL_NIGHT'") {
            phases.push("ETERNAL_NIGHT".to_string());
        }
    }
    let mut rules = Vec::new();
    if let Ok(text) = std::fs::read_to_string(root.join(PROXIMITY_REL)) {
        for line in text.lines() {
            if let Some(rest) = line.trim().strip_prefix("id:") {
                let name = rest.trim().trim_end_matches(',').trim().trim_matches(|c| c == '\'' || c == '"');
                if !name.is_empty() && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
                    rules.push(name.to_string());
                }
            }
        }
    }
    (phases, rules)
}

fn load_registry(root: &Path, probe: &Path) -> Registry {
    let mut registry = Registry::load(probe);
    let (phases, rules) = script_lists(root);
    registry.summary.push_str(&format!(", {} time phases, {} proximity rules", phases.len(), rules.len()));
    registry.add_list("TimePhase", phases);
    registry.add_list("ProximityRule", rules);
    registry
}

fn find_root() -> PathBuf {
    if let Ok(p) = std::env::var("WINTERHEART_DIR") {
        return PathBuf::from(p);
    }
    let starts = [std::env::current_dir().ok(), std::env::current_exe().ok()];
    for start in starts.into_iter().flatten() {
        let mut dir = start;
        loop {
            if dir.join("options.txt").is_file() && dir.join("kubejs").is_dir() {
                return dir;
            }
            if !dir.pop() {
                break;
            }
        }
    }
    eprintln!("could not find the instance root (a folder with options.txt and kubejs/); set WINTERHEART_DIR");
    std::process::exit(1);
}

fn write_atomic(path: &Path, text: &str) -> std::io::Result<()> {
    let tmp = path.with_extension("json.tmp");
    std::fs::write(&tmp, text)?;
    std::fs::rename(&tmp, path)
}

const MODELS_REL: &str = "kubejs/assets/kubejs/models/item";

/// Writes the item model files for textured books and removes ones this editor generated earlier but no
/// longer needs. Returns true if anything on disk changed (the game then needs F3+T to see new textures).
fn write_models(root: &Path, books: &[Book]) -> std::io::Result<bool> {
    let dir = root.join(MODELS_REL);
    let files = model::model_files(books);
    let mut changed = false;

    if dir.is_dir() {
        for entry in std::fs::read_dir(&dir)?.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            let ours = name.starts_with("guide_book") && name.ends_with(".json");
            let wanted = files.iter().any(|(n, _)| *n == name);
            let generated = std::fs::read_to_string(entry.path()).is_ok_and(|t| t.contains(model::MODEL_MARKER));
            if ours && !wanted && generated {
                std::fs::remove_file(entry.path())?;
                changed = true;
            }
        }
    }
    if !files.is_empty() {
        std::fs::create_dir_all(&dir)?;
        for (name, text) in files {
            let path = dir.join(&name);
            if std::fs::read_to_string(&path).ok().as_deref() != Some(text.as_str()) {
                std::fs::write(&path, text)?;
                changed = true;
            }
        }
    }
    Ok(changed)
}

fn flatten_component(v: &serde_json::Value, out: &mut String) {
    match v {
        serde_json::Value::String(s) => out.push_str(s),
        serde_json::Value::Array(a) => a.iter().for_each(|x| flatten_component(x, out)),
        serde_json::Value::Object(o) => {
            if let Some(t) = o.get("text") {
                flatten_component(t, out);
            }
            if let Some(e) = o.get("extra") {
                flatten_component(e, out);
            }
        }
        _ => {}
    }
}

/// The page exactly as guides_engine.js renders it: bold dark-red title, blank line, black body.
fn page_spans(g: &Guide, title_color: [u8; 3]) -> Vec<Span> {
    let nl = Span { ch: '\n', style: BLACK };
    let mut v = Font::parse_legacy(&g.title, Style { color: title_color, ..TITLE });
    v.push(nl);
    v.push(nl);
    match &g.raw_content {
        Some(raw) => {
            let mut flat = String::new();
            flatten_component(raw, &mut flat);
            v.extend(Font::parse_legacy(&flat, Style::plain([0x55, 0x55, 0x55])));
        }
        None => v.extend(Font::parse_legacy(&g.content, BLACK)),
    }
    v
}

/// Where the book is drawn. `origin`/`scale` are in macroquad units; `gui_scale` is physical pixels per GUI
/// pixel (what Minecraft's GUI scale option means), which is what the status bar shows.
#[derive(Clone, Copy)]
struct Layout {
    origin: Vec2,
    scale: f32,
    gui_scale: i32,
}

struct App {
    root: PathBuf,
    guides_path: PathBuf,
    registry_path: PathBuf,
    font: Font,
    book_tex: Option<Texture2D>,
    registry: Registry,
    books: Vec<Book>,
    pages: Vec<Page>,
    guides: Vec<Guide>,
    cur_book: usize,
    /// index into `guides`; usize::MAX when the current book has no guides
    sel: usize,
    confirm_delete: bool,

    saved_text: String,
    pending: Option<(String, f64)>,
    autosave: bool,
    saved_at: Option<f64>,
    status: String,
    status_error: bool,
    problems: Vec<Problem>,
    last_disk_check: f64,

    allow_unknown: bool,
    scale_pref: i32,
    layout: Option<Layout>,
    hover_button: Option<bool>, // Some(false) = back, Some(true) = forward
    wheel: f32,
    resource_log: Vec<String>,
}

impl App {
    fn new() -> App {
        let root = find_root();
        let guides_path = root.join(GUIDES_REL);
        let registry_path = root.join(PROBE_REL);

        let res = Resources::discover(&root);
        let font = Font::load(&res);
        let mut resource_log = res.log.clone();
        resource_log.extend(font.report.iter().cloned());

        let book_tex = match res.find(BOOK_TEXTURE) {
            Some((pack, bytes)) => {
                resource_log.push(format!("book texture from: {pack}"));
                let t = Texture2D::from_file_with_format(&bytes, Some(ImageFormat::Png));
                t.set_filter(FilterMode::Nearest);
                Some(t)
            }
            None => {
                resource_log.push("book texture not found in any resource pack or the vanilla jar".into());
                None
            }
        };

        let gui_scale = std::fs::read_to_string(root.join("options.txt"))
            .ok()
            .and_then(|s| s.lines().find_map(|l| l.strip_prefix("guiScale:").and_then(|v| v.trim().parse::<i32>().ok())))
            .unwrap_or(3);

        let mut app = App {
            root: root.clone(),
            guides_path,
            registry_path: registry_path.clone(),
            font,
            book_tex,
            registry: load_registry(&root, &registry_path),
            books: vec![],
            pages: vec![],
            guides: vec![],
            cur_book: 0,
            sel: 0,
            confirm_delete: false,
            saved_text: String::new(),
            pending: None,
            autosave: true,
            saved_at: None,
            status: String::new(),
            status_error: false,
            problems: vec![],
            last_disk_check: 0.0,
            allow_unknown: false,
            scale_pref: gui_scale.clamp(1, 6),
            layout: None,
            hover_button: None,
            wheel: 0.0,
            resource_log,
        };
        app.load_from_disk(true);
        app
    }

    fn load_from_disk(&mut self, first: bool) {
        match std::fs::read_to_string(&self.guides_path) {
            Ok(text) => match model::parse(&text) {
                Ok((books, pages, guides)) => {
                    self.books = books;
                    self.pages = pages;
                    self.guides = guides;
                    self.fix_selection();
                    self.saved_text = model::serialize(&self.books, &self.pages, &self.guides);
                    self.autosave = true;
                    self.status = if first { "Loaded guides.json".into() } else { "Reloaded guides.json (changed on disk)".into() };
                    self.status_error = false;
                }
                Err(e) => {
                    self.autosave = false;
                    self.status = format!("guides.json is not valid, autosave is OFF so it is not overwritten: {e}");
                    self.status_error = true;
                }
            },
            Err(_) => {
                self.books = vec![Book::default_book()];
                self.saved_text = String::new();
                self.status = "guides.json does not exist yet; it will be created on the first edit".into();
            }
        }
    }

    fn members(&self) -> Vec<usize> {
        let id = self.books.get(self.cur_book).map_or("", |b| b.id.as_str());
        self.guides.iter().enumerate().filter(|(_, g)| g.book == id).map(|(i, _)| i).collect()
    }

    /// Keeps `cur_book` in range and `sel` on a guide of the current book.
    fn fix_selection(&mut self) {
        self.cur_book = self.cur_book.min(self.books.len().saturating_sub(1));
        model::assign_models(&mut self.books);
        let members = self.members();
        if !members.contains(&self.sel) {
            self.sel = members.first().copied().unwrap_or(usize::MAX);
        }
    }

    /// The title color of the book a guide belongs to (dark red by default, like the game).
    fn title_color_of(&self, g: &Guide) -> [u8; 3] {
        self.books
            .iter()
            .find(|b| b.id == g.book)
            .and_then(|b| model::parse_color(&b.title_color))
            .unwrap_or(TITLE.color)
    }

    fn page_line_count(&self, g: &Guide) -> usize {
        self.font.wrap(&page_spans(g, self.title_color_of(g)), TEXT_WIDTH).len()
    }

    fn refresh_problems(&mut self) {
        let mut problems = model::validate(&self.books, &self.pages, &self.guides, &self.registry, self.allow_unknown);
        for (i, g) in self.guides.iter().enumerate() {
            let lines = self.page_line_count(g);
            if lines > MAX_LINES {
                problems.push(Problem {
                    guide: Some(i),
                    error: false,
                    msg: format!("{}: text overflows the page by {} line(s); the book cuts it off", g.id, lines - MAX_LINES),
                });
            }
        }
        self.problems = problems;
    }

    /// Runs after the UI every frame: validate, and write guides.json once edits have settled.
    fn autosave_tick(&mut self) {
        let now = get_time();
        self.refresh_problems();
        let text = model::serialize(&self.books, &self.pages, &self.guides);

        if text == self.saved_text {
            self.pending = None;
            // pick up edits made to the file from outside while we have nothing unsaved
            if now - self.last_disk_check > 1.0 {
                self.last_disk_check = now;
                if let Ok(disk) = std::fs::read_to_string(&self.guides_path) {
                    if disk != self.saved_text && model::parse(&disk).is_ok_and(|(b, p, g)| model::serialize(&b, &p, &g) != self.saved_text) {
                        self.load_from_disk(false);
                    }
                }
            }
            return;
        }
        if !self.autosave {
            return;
        }

        let since = match &self.pending {
            Some((t, since)) if *t == text => *since,
            _ => {
                self.pending = Some((text.clone(), now));
                now
            }
        };
        if now - since < 0.4 {
            return;
        }

        let errors = self.problems.iter().filter(|p| p.error).count();
        if errors > 0 {
            self.status = format!("Not saved: {errors} error(s) to fix first");
            self.status_error = true;
            return;
        }
        match write_atomic(&self.guides_path, &text) {
            Ok(()) => {
                self.saved_text = text;
                self.pending = None;
                self.saved_at = Some(now);
                self.status = "Saved to guides.json (the game reloads it within ~2s)".into();
                self.status_error = false;
                match write_models(&self.root, &self.books) {
                    Ok(true) => self.status.push_str("; book models changed, press F3+T in game to reload textures"),
                    Ok(false) => {}
                    Err(e) => {
                        self.status = format!("Saved guides.json, but could not write book models: {e}");
                        self.status_error = true;
                    }
                }
            }
            Err(e) => {
                self.status = format!("Could not write guides.json: {e}");
                self.status_error = true;
            }
        }
    }

    // ------------------------------------------------------------------ UI

    fn ui(&mut self, ctx: &egui::Context) {
        let dpi = macroquad::miniquad::window::dpi_scale();
        if (ctx.pixels_per_point() - dpi).abs() > 0.01 {
            ctx.set_pixels_per_point(dpi);
        }
        self.fix_selection();
        self.ui_status(ctx);
        self.ui_list(ctx);
        self.ui_unlock(ctx);
        self.ui_editor(ctx);
        self.ui_book(ctx);
    }

    fn ui_status(&mut self, ctx: &egui::Context) {
        egui::TopBottomPanel::bottom("status").show(ctx, |ui| {
            ui.horizontal(|ui| {
                let color = if self.status_error { RED } else { GREEN };
                let age = self.saved_at.map(|t| format!("  ({:.0}s ago)", get_time() - t)).unwrap_or_default();
                ui.colored_label(color, format!("{}{}", self.status, age));
            });
            ui.horizontal_wrapped(|ui| {
                ui.checkbox(&mut self.allow_unknown, "allow ids not in ProbeJS");
                ui.separator();
                ui.label("GUI scale");
                if ui.small_button("-").clicked() {
                    self.scale_pref = (self.scale_pref - 1).max(1);
                }
                ui.label(match self.layout {
                    Some(l) if l.gui_scale != self.scale_pref => format!("{} (showing {})", self.scale_pref, l.gui_scale),
                    _ => self.scale_pref.to_string(),
                });
                if ui.small_button("+").clicked() {
                    self.scale_pref = (self.scale_pref + 1).min(8);
                }
                ui.separator();
                ui.label(&self.registry.summary);
                if ui.small_button("Reload ProbeJS").clicked() {
                    self.registry = load_registry(&self.root, &self.registry_path);
                }
            });
            if !self.problems.is_empty() {
                egui::ScrollArea::vertical().id_salt("problems").max_height(80.0).show(ui, |ui| {
                    for p in &self.problems {
                        ui.colored_label(if p.error { RED } else { AMBER }, format!("{} {}", if p.error { "error:" } else { "warning:" }, p.msg));
                    }
                });
            }
        });
    }

    fn ui_list(&mut self, ctx: &egui::Context) {
        enum Act {
            New,
            Delete,
            Up,
            Down,
            Select(usize),
            NewBook,
            DeleteBook,
        }
        let mut act: Option<Act> = None;
        let members = self.members();
        let registry = &self.registry;
        let allow_unknown = self.allow_unknown;

        egui::SidePanel::left("list").default_width(250.0).show(ctx, |ui| {
            ui.heading("Book");
            ui.horizontal(|ui| {
                let current = self.books.get(self.cur_book).map_or(String::new(), |b| b.name.clone());
                egui::ComboBox::from_id_salt("book_pick").selected_text(current).width(130.0).show_ui(ui, |ui| {
                    for (i, b) in self.books.iter().enumerate() {
                        if ui.selectable_label(i == self.cur_book, &b.name).clicked() {
                            self.cur_book = i;
                            self.confirm_delete = false;
                        }
                    }
                });
                if ui.small_button("+ Book").clicked() {
                    act = Some(Act::NewBook);
                }
                if ui.small_button("Delete book").clicked() {
                    act = Some(Act::DeleteBook);
                }
            });
            if let Some(book) = self.books.get_mut(self.cur_book) {
                ui.collapsing("Book settings", |ui| {
                    let old_id = book.id.clone();
                    egui::Grid::new("book_settings").num_columns(2).show(ui, |ui| {
                        ui.label("id");
                        let r = ui.add(egui::TextEdit::singleline(&mut book.id).desired_width(150.0));
                        if r.changed() {
                            book.id = book.id.to_lowercase().replace([' ', '-'], "_");
                        }
                        r.on_hover_text("Items already given out keep the old id, so they stop opening this book.");
                        ui.end_row();
                        ui.label("name");
                        ui.add(egui::TextEdit::singleline(&mut book.name).desired_width(150.0))
                            .on_hover_text("Item name and written-book title");
                        ui.end_row();
                        ui.label("author");
                        ui.add(egui::TextEdit::singleline(&mut book.author).desired_width(150.0).hint_text("(default)"));
                        ui.end_row();
                        ui.label("tooltip");
                        ui.add(egui::TextEdit::singleline(&mut book.tooltip).desired_width(150.0).hint_text("(none)"));
                        ui.end_row();
                        ui.label("title color");
                        ui.vertical(|ui| {
                            ui.horizontal_wrapped(|ui| {
                                let mut rgb = model::parse_color(&book.title_color).unwrap_or(TITLE.color);
                                if ui.color_edit_button_srgb(&mut rgb).changed() {
                                    book.title_color = model::color_to_string(rgb);
                                }
                                if ui.small_button("Reset").on_hover_text("back to dark red").clicked() {
                                    book.title_color.clear();
                                }
                                ui.weak(if book.title_color.is_empty() { "dark_red (default)" } else { book.title_color.as_str() });
                            });
                            ui.horizontal_wrapped(|ui| {
                                ui.spacing_mut().item_spacing.x = 2.0;
                                for (name, _, c) in PALETTE {
                                    let b = egui::Button::new("").fill(Color32::from_rgb(c[0], c[1], c[2])).min_size(egui::vec2(14.0, 14.0));
                                    if ui.add(b).on_hover_text(name).clicked() {
                                        book.title_color = name.to_string();
                                    }
                                }
                            });
                        });
                        ui.end_row();
                        ui.label("texture");
                        let key = registry.has_list("Texture").then_some("Texture");
                        let valid = key.is_none_or(|k| allow_unknown || book.texture.is_empty() || registry.contains(k, &book.texture));
                        let mut edit = egui::TextEdit::singleline(&mut book.texture).desired_width(150.0).hint_text("(default book)");
                        if !valid {
                            edit = edit.text_color(RED);
                        }
                        let resp = ui.add(edit).on_hover_text("Item texture, e.g. minecraft:item/enchanted_book. Your own PNGs go in kubejs/assets/<namespace>/textures/item/.");
                        autocomplete(ui, &resp, &mut book.texture, key, registry);
                        ui.end_row();
                    });
                    if book.id != old_id {
                        for g in self.guides.iter_mut().filter(|g| g.book == old_id) {
                            g.book = book.id.clone();
                        }
                    }
                });
            }
            ui.separator();

            ui.collapsing("Lost pages", |ui| {
                ui.small("Items (kubejs:lost_page) a trigger can match with 'lost page'. Get one with /guides page <id>.");
                let mut remove = None;
                for (i, p) in self.pages.iter_mut().enumerate() {
                    ui.push_id(("lost_page", i), |ui| {
                        egui::Grid::new("page_grid").num_columns(2).show(ui, |ui| {
                            ui.label("id");
                            let r = ui.add(egui::TextEdit::singleline(&mut p.id).desired_width(150.0));
                            if r.changed() {
                                p.id = p.id.to_lowercase().replace([' ', '-'], "_");
                            }
                            r.on_hover_text("Items already given out keep the old id, and triggers using it must be updated.");
                            ui.end_row();
                            ui.label("name");
                            ui.add(egui::TextEdit::singleline(&mut p.name).desired_width(150.0));
                            ui.end_row();
                            ui.label("tooltip");
                            ui.add(egui::TextEdit::singleline(&mut p.tooltip).desired_width(150.0).hint_text("(none)"));
                            ui.end_row();
                        });
                        if ui.small_button("Delete page").clicked() {
                            remove = Some(i);
                        }
                        ui.separator();
                    });
                }
                if let Some(i) = remove {
                    self.pages.remove(i);
                }
                if ui.small_button("+ Lost page").clicked() {
                    let p = Page::blank(&self.pages);
                    self.pages.push(p);
                }
            });
            ui.separator();

            ui.heading("Guides");
            ui.small("The list order is the page order in this book.");
            ui.horizontal(|ui| {
                if ui.button("+ New").clicked() {
                    act = Some(Act::New);
                }
                if ui.button("Up").clicked() {
                    act = Some(Act::Up);
                }
                if ui.button("Down").clicked() {
                    act = Some(Act::Down);
                }
            });
            ui.horizontal(|ui| {
                if !self.confirm_delete {
                    if ui.button("Delete").clicked() {
                        self.confirm_delete = true;
                    }
                } else {
                    ui.colored_label(RED, "Delete this guide?");
                    if ui.button("Yes").clicked() {
                        act = Some(Act::Delete);
                    }
                    if ui.button("No").clicked() {
                        self.confirm_delete = false;
                    }
                }
            });
            ui.separator();
            egui::ScrollArea::vertical().id_salt("guide_list").max_height(ui.available_height() - 110.0).show(ui, |ui| {
                for (pos, &i) in members.iter().enumerate() {
                    let g = &self.guides[i];
                    let bad = self.problems.iter().any(|p| p.guide == Some(i) && p.error);
                    let warn = self.problems.iter().any(|p| p.guide == Some(i));
                    let mut text = RichText::new(format!("{}. {}", pos + 1, if g.title.is_empty() { &g.id } else { &g.title }));
                    if bad {
                        text = text.color(RED);
                    } else if warn {
                        text = text.color(AMBER);
                    }
                    if ui.selectable_label(i == self.sel, text).on_hover_text(&g.id).clicked() {
                        act = Some(Act::Select(i));
                    }
                }
            });
            ui.separator();
            ui.collapsing("Resources", |ui| {
                ui.small(format!("root: {}", self.root.display()));
                for line in &self.resource_log {
                    ui.small(line);
                }
            });
        });

        let pos = members.iter().position(|&i| i == self.sel);
        match act {
            Some(Act::New) => {
                let book = self.books.get(self.cur_book).map_or(String::new(), |b| b.id.clone());
                let g = Guide::blank(&self.guides, &book);
                self.guides.push(g);
                self.sel = self.guides.len() - 1;
                self.confirm_delete = false;
            }
            Some(Act::Delete) => {
                if let Some(pos) = pos {
                    self.guides.remove(self.sel);
                    let after = self.members();
                    self.sel = after.get(pos.min(after.len().saturating_sub(1))).copied().unwrap_or(usize::MAX);
                }
                self.confirm_delete = false;
            }
            Some(Act::Up) => {
                if let Some(pos) = pos.filter(|p| *p > 0) {
                    self.guides.swap(self.sel, members[pos - 1]);
                    self.sel = members[pos - 1];
                }
            }
            Some(Act::Down) => {
                if let Some(pos) = pos.filter(|p| p + 1 < members.len()) {
                    self.guides.swap(self.sel, members[pos + 1]);
                    self.sel = members[pos + 1];
                }
            }
            Some(Act::Select(i)) => {
                self.sel = i;
                self.confirm_delete = false;
            }
            Some(Act::NewBook) => {
                let b = Book::blank(&self.books);
                self.books.push(b);
                self.cur_book = self.books.len() - 1;
                self.confirm_delete = false;
            }
            Some(Act::DeleteBook) => {
                if !members.is_empty() {
                    self.status = "Can't delete a book that still has guides: move or delete them first".into();
                    self.status_error = true;
                } else if self.books.len() <= 1 {
                    self.status = "Can't delete the last book".into();
                    self.status_error = true;
                } else {
                    self.books.remove(self.cur_book);
                    self.cur_book = self.cur_book.saturating_sub(1);
                }
            }
            None => {}
        }
    }

    fn ui_editor(&mut self, ctx: &egui::Context) {
        let mut follow: Option<usize> = None;
        egui::TopBottomPanel::bottom("editor").resizable(true).default_height(260.0).show(ctx, |ui| {
            let Some(g) = self.guides.get_mut(self.sel) else {
                ui.label("No guides in this book. Press + New.");
                return;
            };
            ui.horizontal(|ui| {
                ui.label("id");
                let r = ui.add(egui::TextEdit::singleline(&mut g.id).desired_width(170.0));
                if r.changed() {
                    g.id = g.id.to_lowercase().replace([' ', '-'], "_");
                }
                r.on_hover_text("The world's unlocked state is keyed by id; renaming a guide re-locks it.");
                ui.label("title");
                ui.add(egui::TextEdit::singleline(&mut g.title).desired_width(260.0));
                ui.label("book");
                let shown = self.books.iter().find(|b| b.id == g.book).map_or(g.book.clone(), |b| b.name.clone());
                egui::ComboBox::from_id_salt("guide_book").selected_text(shown).show_ui(ui, |ui| {
                    for (i, b) in self.books.iter().enumerate() {
                        if ui.selectable_label(b.id == g.book, &b.name).clicked() && b.id != g.book {
                            g.book = b.id.clone();
                            follow = Some(i);
                        }
                    }
                });
            });

            let content_id = egui::Id::new("content_edit");
            if g.raw_content.is_some() {
                ui.colored_label(AMBER, "This guide's content is a text component, not a plain string. It is kept as is and is not editable here.");
                return;
            }
            ui.horizontal_wrapped(|ui| {
                ui.label("Format:");
                for (name, code, rgb) in PALETTE {
                    let b = egui::Button::new("").fill(Color32::from_rgb(rgb[0], rgb[1], rgb[2])).min_size(egui::vec2(18.0, 18.0));
                    if ui.add(b).on_hover_text(format!("§{code}  {name}")).clicked() {
                        insert_code(ctx, content_id, &mut g.content, &format!("§{code}"));
                    }
                }
                for (label, code) in [("Bold", 'l'), ("Italic", 'o'), ("Underline", 'n'), ("Strike", 'm'), ("Reset", 'r')] {
                    if ui.small_button(label).on_hover_text(format!("§{code}")).clicked() {
                        insert_code(ctx, content_id, &mut g.content, &format!("§{code}"));
                    }
                }
            });
            egui::ScrollArea::vertical().id_salt("content_scroll").show(ui, |ui| {
                ui.add(
                    egui::TextEdit::multiline(&mut g.content)
                        .id(content_id)
                        .desired_width(f32::INFINITY)
                        .desired_rows(8)
                        .hint_text("Page text. § codes format it; the preview above wraps it like the game does."),
                );
            });
        });
        if let Some(i) = follow {
            self.cur_book = i; // moving a guide to another book follows it there
        }
    }

    fn ui_unlock(&mut self, ctx: &egui::Context) {
        egui::SidePanel::right("unlock").default_width(480.0).min_width(380.0).show(ctx, |ui| {
            ui.heading("Unlock when any of:");
            let allow_unknown = self.allow_unknown;
            let Some(g) = self.guides.get_mut(self.sel) else { return };
            let registry = &self.registry;
            let pages = &self.pages;

            egui::ScrollArea::vertical().id_salt("unlock_scroll").show(ui, |ui| {
                let mut remove = None;
                for (i, t) in g.unlock.iter_mut().enumerate() {
                    ui.push_id(i, |ui| {
                        if trigger_ui(ui, t, registry, pages, allow_unknown) {
                            remove = Some(i);
                        }
                    });
                }
                if let Some(i) = remove {
                    g.unlock.remove(i);
                }
                ui.horizontal(|ui| {
                    if ui.button("+ Sweep trigger").on_hover_text("checked twice a second").clicked() {
                        g.unlock.push(Trigger::new(true, "standing_on"));
                    }
                    if ui.button("+ Event trigger").on_hover_text("checked the moment it happens").clicked() {
                        g.unlock.push(Trigger::new(false, "block_broken"));
                    }
                });
                if g.unlock.is_empty() {
                    ui.weak("No triggers: this guide only unlocks through /guides unlock or code.");
                }
                ui.separator();
                ui.collapsing("Generated JSON", |ui| {
                    let mut json = serde_json::to_string_pretty(&g.unlock_json()).unwrap_or_default();
                    ui.add(egui::TextEdit::multiline(&mut json).code_editor().desired_width(f32::INFINITY));
                });
            });
        });
    }

    fn ui_book(&mut self, ctx: &egui::Context) {
        egui::CentralPanel::default().frame(egui::Frame::NONE).show(ctx, |ui| {
            let rect = ui.max_rect();
            let resp = ui.allocate_rect(rect, egui::Sense::click());

            // egui points -> macroquad units (they differ on high-DPI screens), and GUI scale is in physical pixels
            let dpi = macroquad::miniquad::window::dpi_scale();
            let k = screen_width() / ctx.screen_rect().width();
            let phys_w = rect.width() * k * dpi;
            let phys_h = rect.height() * k * dpi;
            let fit = ((phys_h - 4.0) / BOOK_SIZE).floor().min((phys_w / BOOK_SIZE).floor()).max(1.0);
            let gui_scale = (self.scale_pref as f32).min(fit).max(1.0);
            let scale = gui_scale / dpi; // macroquad units per GUI pixel
            let s = scale / k; // egui points per GUI pixel

            let origin_mq = vec2(rect.center().x * k - BOOK_SIZE * scale / 2.0, rect.top() * k + 2.0 * scale);
            self.layout = Some(Layout { origin: origin_mq, scale, gui_scale: gui_scale as i32 });
            let origin = egui::pos2(origin_mq.x / k, origin_mq.y / k);

            let members = self.members();
            let n = members.len();
            let pos = members.iter().position(|&i| i == self.sel);
            let button_rect = |x: f32| egui::Rect::from_min_size(origin + egui::vec2(x, BUTTON_Y) * s, egui::vec2(BUTTON_W, BUTTON_H) * s);
            let back = button_rect(BACK_BUTTON_X);
            let forward = button_rect(FORWARD_BUTTON_X);

            self.hover_button = None;
            if let Some(p) = ctx.pointer_hover_pos().filter(|_| resp.hovered()) {
                if pos.is_some_and(|p| p > 0) && back.contains(p) {
                    self.hover_button = Some(false);
                } else if pos.is_some_and(|q| q + 1 < n) && forward.contains(p) {
                    self.hover_button = Some(true);
                }
            }
            if let (true, Some(pos)) = (resp.clicked(), pos) {
                match self.hover_button {
                    Some(false) => self.sel = members[pos - 1],
                    Some(true) => self.sel = members[pos + 1],
                    None => {}
                }
            }

            match pos {
                Some(pos) if resp.hovered() => {
                    self.wheel += ctx.input(|i| i.raw_scroll_delta.y);
                    if self.wheel > 20.0 {
                        self.sel = members[pos.saturating_sub(1)];
                        self.wheel = 0.0;
                    } else if self.wheel < -20.0 {
                        self.sel = members[(pos + 1).min(n - 1)];
                        self.wheel = 0.0;
                    }
                }
                _ => self.wheel = 0.0,
            }
        });
    }

    // ------------------------------------------------------------------ book preview (macroquad)

    fn draw_book(&self) {
        let Some(Layout { origin, scale: s, .. }) = self.layout else { return };
        let members = self.members();
        let n = members.len();
        let pos = members.iter().position(|&i| i == self.sel);

        match &self.book_tex {
            Some(tex) => {
                let k = tex.width() / 256.0;
                draw_texture_ex(
                    tex,
                    origin.x,
                    origin.y,
                    WHITE,
                    DrawTextureParams {
                        source: Some(Rect::new(0.0, 0.0, BOOK_SIZE * k, BOOK_SIZE * k)),
                        dest_size: Some(vec2(BOOK_SIZE * s, BOOK_SIZE * s)),
                        ..Default::default()
                    },
                );
            }
            None => draw_rectangle(origin.x, origin.y, BOOK_SIZE * s, BOOK_SIZE * s, Color::from_rgba(230, 210, 170, 255)),
        }
        let Some(pos) = pos else { return };

        let indicator = format!("Page {} of {}", pos + 1, n);
        let w = self.font.width(&indicator);
        self.font.draw_line(
            &Font::parse_legacy(&indicator, BLACK),
            origin.x + (BOOK_SIZE - w - INDICATOR_RIGHT_MARGIN) * s,
            origin.y + INDICATOR_Y * s,
            s,
        );

        let lines = self.font.wrap(&page_spans(&self.guides[self.sel], self.title_color_of(&self.guides[self.sel])), TEXT_WIDTH);
        for (i, line) in lines.iter().take(MAX_LINES).enumerate() {
            self.font.draw_line(line, origin.x + TEXT_X * s, origin.y + (TEXT_Y + LINE_HEIGHT * i as f32) * s, s);
        }
        if lines.len() > MAX_LINES {
            // mark where the game would cut the page off
            let y = origin.y + (TEXT_Y + LINE_HEIGHT * MAX_LINES as f32) * s;
            draw_rectangle(origin.x + TEXT_X * s, y, TEXT_WIDTH * s, s, Color::from_rgba(200, 40, 40, 255));
        }

        if let Some(tex) = &self.book_tex {
            let k = tex.width() / 256.0;
            let button = |x: f32, v: f32, hover: bool| {
                let u = if hover { BUTTON_W } else { 0.0 };
                draw_texture_ex(
                    tex,
                    origin.x + x * s,
                    origin.y + BUTTON_Y * s,
                    WHITE,
                    DrawTextureParams {
                        source: Some(Rect::new(u * k, v * k, BUTTON_W * k, BUTTON_H * k)),
                        dest_size: Some(vec2(BUTTON_W * s, BUTTON_H * s)),
                        ..Default::default()
                    },
                );
            };
            if pos + 1 < n {
                button(FORWARD_BUTTON_X, 192.0, self.hover_button == Some(true));
            }
            if pos > 0 {
                button(BACK_BUTTON_X, 192.0 + BUTTON_H, self.hover_button == Some(false));
            }
        }
    }
}

/// Inserts a § code at the text cursor (wrapping a selection, closing it with §r).
fn insert_code(ctx: &egui::Context, id: egui::Id, text: &mut String, code: &str) {
    use egui::text::{CCursor, CCursorRange};
    let mut state = egui::TextEdit::load_state(ctx, id).unwrap_or_default();
    let len = text.chars().count();
    let (a, b) = state
        .cursor
        .char_range()
        .map(|r| (r.primary.index.min(r.secondary.index).min(len), r.primary.index.max(r.secondary.index).min(len)))
        .unwrap_or((len, len));
    let byte = |text: &str, idx: usize| text.char_indices().nth(idx).map_or(text.len(), |(i, _)| i);

    if b > a && code != "§r" {
        let at = byte(text, b);
        text.insert_str(at, "§r");
    }
    let at = byte(text, a);
    text.insert_str(at, code);

    let shift = code.chars().count();
    state.cursor.set_char_range(Some(CCursorRange::two(CCursor::new(a + shift), CCursor::new(b + shift))));
    state.store(ctx, id);
    ctx.memory_mut(|m| m.request_focus(id));
}

fn matcher_kind_name(k: MKind) -> &'static str {
    match k {
        MKind::Id => "id",
        MKind::Tag => "#tag",
        MKind::Regex => "regex",
        MKind::Page => "page",
    }
}

/// A text field with a dropdown of matching registry ids.
fn autocomplete(ui: &mut egui::Ui, resp: &egui::Response, text: &mut String, key: Option<&str>, registry: &Registry) {
    let Some(key) = key else { return };
    let popup_id = resp.id.with("suggestions");

    if resp.gained_focus() || resp.changed() {
        ui.memory_mut(|m| m.open_popup(popup_id));
    }
    if resp.has_focus() && ui.input(|i| i.key_pressed(egui::Key::Escape)) {
        ui.memory_mut(|m| m.close_popup());
    }
    // Enter on something that is not a valid id takes the best suggestion
    if resp.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) && !registry.contains(key, text) {
        if let Some(first) = registry.suggest(key, text, 1).first() {
            *text = first.to_string();
        }
    }

    if !ui.memory(|m| m.is_popup_open(popup_id)) {
        return;
    }
    let suggestions: Vec<String> = registry.suggest(key, text, 12).into_iter().map(str::to_string).collect();
    if suggestions.is_empty() || (suggestions.len() == 1 && suggestions[0] == *text) {
        return;
    }
    let mut picked = None;
    egui::popup::popup_below_widget(ui, popup_id, resp, egui::popup::PopupCloseBehavior::CloseOnClickOutside, |ui| {
        ui.set_min_width(resp.rect.width().max(300.0));
        for s in &suggestions {
            if ui.selectable_label(false, s).clicked() {
                picked = Some(s.clone());
            }
        }
    });
    if let Some(p) = picked {
        *text = p;
        ui.memory_mut(|m| m.close_popup());
    }
}

fn matcher_list(ui: &mut egui::Ui, ms: &mut Vec<Matcher>, domain: Domain, label: &str, registry: &Registry, pages: &[Page], allow_unknown: bool) {
    ui.label(RichText::new(label).strong());
    let (id_key, tag_key) = domain.keys();
    let mut remove = None;
    for (i, m) in ms.iter_mut().enumerate() {
        ui.push_id(i, |ui| {
            ui.horizontal(|ui| {
                egui::ComboBox::from_id_salt("mkind").width(62.0).selected_text(matcher_kind_name(m.kind)).show_ui(ui, |ui| {
                    ui.selectable_value(&mut m.kind, MKind::Id, "id");
                    if tag_key.is_some() {
                        ui.selectable_value(&mut m.kind, MKind::Tag, "#tag");
                    }
                    if domain != Domain::Free {
                        ui.selectable_value(&mut m.kind, MKind::Regex, "regex");
                    }
                    if domain == Domain::Item {
                        ui.selectable_value(&mut m.kind, MKind::Page, "page");
                    }
                });
                if m.kind == MKind::Page {
                    let known = pages.iter().any(|p| p.id == m.text);
                    let shown = if m.text.is_empty() { "(pick a lost page)".to_string() } else { m.text.clone() };
                    egui::ComboBox::from_id_salt("mpage").width(250.0).selected_text(RichText::new(shown).color(if known || m.text.is_empty() { Color32::PLACEHOLDER } else { RED })).show_ui(ui, |ui| {
                        for p in pages {
                            ui.selectable_value(&mut m.text, p.id.clone(), format!("{} ({})", p.name, p.id));
                        }
                    });
                    if ui.small_button("x").clicked() {
                        remove = Some(i);
                    }
                    return;
                }
                let key = match m.kind {
                    MKind::Id => id_key,
                    MKind::Tag => tag_key,
                    MKind::Regex | MKind::Page => None,
                }
                .filter(|k| registry.has_list(k));
                let valid = key.is_none_or(|k| allow_unknown || m.text.is_empty() || registry.contains(k, &m.text));

                let mut edit = egui::TextEdit::singleline(&mut m.text).desired_width(250.0);
                if !valid {
                    edit = edit.text_color(RED);
                }
                if m.kind == MKind::Regex {
                    edit = edit.hint_text("regex tested against the id");
                }
                let resp = ui.add(edit);
                autocomplete(ui, &resp, &mut m.text, key, registry);
                if ui.small_button("x").clicked() {
                    remove = Some(i);
                }
            });
        });
    }
    if let Some(i) = remove {
        ms.remove(i);
    }
    ui.horizontal(|ui| {
        if ui.small_button("+ id").clicked() {
            ms.push(Matcher::new(MKind::Id));
        }
        if tag_key.is_some() && ui.small_button("+ #tag").clicked() {
            ms.push(Matcher::new(MKind::Tag));
        }
        if domain != Domain::Free && ui.small_button("+ regex").clicked() {
            ms.push(Matcher::new(MKind::Regex));
        }
        if domain == Domain::Item && ui.small_button("+ lost page").clicked() {
            ms.push(Matcher::new(MKind::Page));
        }
        if ms.is_empty() {
            ui.weak("(none = anything)");
        }
    });
}

fn hand_picker(ui: &mut egui::Ui, hand: &mut String) {
    ui.horizontal(|ui| {
        ui.label("hand");
        egui::ComboBox::from_id_salt("hand").width(90.0).selected_text(if hand.is_empty() { "either" } else { hand.as_str() }).show_ui(ui, |ui| {
            ui.selectable_value(hand, String::new(), "either");
            ui.selectable_value(hand, "main".to_string(), "main");
            ui.selectable_value(hand, "off".to_string(), "off");
        });
    });
}

/// One trigger card. Returns true if it should be removed.
fn trigger_ui(ui: &mut egui::Ui, t: &mut Trigger, registry: &Registry, pages: &[Page], allow_unknown: bool) -> bool {
    let mut remove = false;
    egui::Frame::group(ui.style()).show(ui, |ui| {
        ui.set_width(ui.available_width());
        ui.horizontal(|ui| {
            let current = format!("{}: {}", if t.sweep { "sweep" } else { "event" }, t.kind);
            egui::ComboBox::from_id_salt("kind").selected_text(current).width(240.0).show_ui(ui, |ui| {
                for (is_sweep, heading) in [(true, "Sweeps (checked twice a second)"), (false, "Events (checked when they happen)")] {
                    ui.label(RichText::new(heading).weak());
                    for k in KINDS.iter().filter(|k| k.sweep == is_sweep) {
                        let selected = t.sweep == k.sweep && t.kind == k.name;
                        if ui.selectable_label(selected, k.name).on_hover_text(k.help).clicked() && !selected {
                            let old = model::spec_of(t.sweep, &t.kind).map(|s| s.domain);
                            t.sweep = k.sweep;
                            t.kind = k.name.to_string();
                            if old != Some(k.domain) {
                                t.matchers.clear();
                            }
                            if !k.by {
                                t.by.clear();
                            }
                        }
                    }
                    ui.separator();
                }
            });
            if ui.button("Remove").clicked() {
                remove = true;
            }
        });

        let Some(spec) = model::spec_of(t.sweep, &t.kind) else {
            ui.colored_label(RED, format!("Unknown kind '{}' (kept as is)", t.kind));
            return;
        };
        ui.small(format!("{}: {}", if t.sweep { "while" } else { "when" }, spec.help));

        if !spec.field.is_empty() {
            matcher_list(ui, &mut t.matchers, spec.domain, spec.label, registry, pages, allow_unknown);
        }
        if spec.by {
            matcher_list(ui, &mut t.by, Domain::Entity, "Attacker", registry, pages, allow_unknown);
        }
        if spec.hand {
            hand_picker(ui, &mut t.hand);
        } else {
            // any trigger can additionally require a held item
            ui.separator();
            matcher_list(ui, &mut t.holding, Domain::Item, "Also holding (optional)", registry, pages, allow_unknown);
            if !t.holding.is_empty() {
                hand_picker(ui, &mut t.hand);
            }
        }
        if spec.distance {
            ui.horizontal(|ui| {
                let mut on = t.distance.is_some();
                if ui.checkbox(&mut on, "max distance").changed() {
                    t.distance = on.then_some(6.0);
                }
                if let Some(d) = &mut t.distance {
                    ui.add(egui::DragValue::new(d).speed(0.25).range(0.5..=64.0).suffix(" blocks"));
                }
            });
        }
        if spec.count {
            ui.horizontal(|ui| {
                let mut on = t.count.is_some();
                if ui.checkbox(&mut on, "at least").changed() {
                    t.count = on.then_some(1);
                }
                if let Some(c) = &mut t.count {
                    ui.add(egui::DragValue::new(c).range(1..=2304).suffix(" items"));
                } else {
                    ui.weak("(1)");
                }
            });
        }
        if spec.days {
            ui.horizontal(|ui| {
                let mut on = t.min_day.is_some();
                if ui.checkbox(&mut on, "from day").changed() {
                    t.min_day = on.then_some(1);
                }
                if let Some(d) = &mut t.min_day {
                    ui.add(egui::DragValue::new(d).range(1..=999));
                }
                let mut on = t.max_day.is_some();
                if ui.checkbox(&mut on, "until day").changed() {
                    t.max_day = on.then_some(t.min_day.unwrap_or(1).max(1));
                }
                if let Some(d) = &mut t.max_day {
                    ui.add(egui::DragValue::new(d).range(1..=999));
                }
            });
        }
        if spec.min_damage {
            ui.horizontal(|ui| {
                let mut on = t.min_damage.is_some();
                if ui.checkbox(&mut on, "at least").changed() {
                    t.min_damage = on.then_some(1.0);
                }
                if let Some(d) = &mut t.min_damage {
                    ui.add(egui::DragValue::new(d).speed(0.1).range(0.0..=1000.0).suffix(" damage"));
                }
            });
        }
        if spec.test {
            ui.horizontal(|ui| {
                ui.label("function");
                ui.add(egui::TextEdit::singleline(&mut t.test).hint_text("name in global.GuideFunctions").desired_width(250.0));
            });
        }
        ui.collapsing("Advanced", |ui| {
            ui.horizontal(|ui| {
                ui.label("extra filter");
                ui.add(egui::TextEdit::singleline(&mut t.where_fn).hint_text("optional name in global.GuideFunctions").desired_width(250.0));
            });
        });
    });
    remove
}

#[macroquad::main(window_conf)]
async fn main() {
    let mut app = App::new();
    // GUIDE_EDITOR_SCREENSHOT=/path/out.png saves the window after a few frames and quits (for checking rendering)
    let screenshot = std::env::var("GUIDE_EDITOR_SCREENSHOT").ok();
    let mut frame = 0;
    loop {
        clear_background(Color::from_rgba(30, 30, 34, 255));
        egui_macroquad::ui(|ctx| app.ui(ctx));
        app.draw_book();
        egui_macroquad::draw();
        app.autosave_tick();
        frame += 1;
        if let Some(path) = &screenshot {
            if frame == 15 {
                get_screen_data().export_png(path);
                for line in &app.resource_log {
                    eprintln!("{line}");
                }
                eprintln!("{}", app.registry.summary);
                return;
            }
        }
        next_frame().await;
    }
}
