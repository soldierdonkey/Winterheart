//! Guide data model, (de)serialisation to guides.json, and validation.
//! The trigger kinds mirror guides_engine.js; keep them in sync when the engine gains a kind.

use crate::registry::Registry;
use serde_json::{json, Map, Value};
use std::collections::HashSet;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Domain {
    Block,
    Item,
    Entity,
    Damage,
    Advancement,
    /// time phases, read from mechanics/time_system.js
    Phase,
    /// proximity rule ids, read from mechanics/proximity.js
    Proximity,
    Free,
    None,
}

impl Domain {
    /// ProbeJS type names: (ids, tags)
    pub fn keys(self) -> (Option<&'static str>, Option<&'static str>) {
        match self {
            Domain::Block => (Some("Block"), Some("BlockTag")),
            Domain::Item => (Some("Item"), Some("ItemTag")),
            Domain::Entity => (Some("EntityType"), Some("EntityTypeTag")),
            Domain::Damage => (Some("DamageType"), Some("DamageTypeTag")),
            Domain::Advancement => (Some("Advancement"), None),
            Domain::Phase => (Some("TimePhase"), None),
            Domain::Proximity => (Some("ProximityRule"), None),
            Domain::Free | Domain::None => (None, None),
        }
    }
}

pub struct KindSpec {
    pub name: &'static str,
    pub sweep: bool,
    pub domain: Domain,
    /// JSON key holding the matchers ("" = none)
    pub field: &'static str,
    pub label: &'static str,
    pub help: &'static str,
    pub distance: bool,
    pub count: bool,
    pub min_damage: bool,
    pub by: bool,
    pub test: bool,
    pub days: bool,
    /// shows the hand picker as a main field (the holding sweep)
    pub hand: bool,
}

const fn spec(name: &'static str, sweep: bool, domain: Domain, field: &'static str, label: &'static str, help: &'static str) -> KindSpec {
    KindSpec { name, sweep, domain, field, label, help, distance: false, count: false, min_damage: false, by: false, test: false, days: false, hand: false }
}

pub static KINDS: &[KindSpec] = &[
    spec("standing_on", true, Domain::Block, "match", "Block", "the block under the player's feet"),
    spec("standing_in", true, Domain::Block, "match", "Block", "the block at the player's feet"),
    KindSpec { distance: true, ..spec("looking_at_block", true, Domain::Block, "match", "Block", "the block under the crosshair") },
    KindSpec { distance: true, ..spec("looking_at_entity", true, Domain::Entity, "match", "Entity", "the mob/entity under the crosshair") },
    KindSpec { count: true, ..spec("inventory", true, Domain::Item, "item", "Item", "stacks in the inventory that add up to a count") },
    KindSpec { hand: true, ..spec("holding", true, Domain::Item, "item", "Item", "the player holds a matching item") },
    KindSpec { days: true, ..spec("time", true, Domain::Phase, "match", "Phase", "the time system is in one of these phases (optionally within a day range)") },
    KindSpec { test: true, ..spec("custom", true, Domain::None, "", "", "a function registered in global.GuideFunctions") },
    spec("block_broken", false, Domain::Block, "match", "Block", "the player breaks a block"),
    spec("block_placed", false, Domain::Block, "match", "Block", "the player places a block"),
    spec("block_used", false, Domain::Block, "match", "Block", "the player right-clicks a block"),
    spec("item_used", false, Domain::Item, "match", "Item", "the player right-clicks with an item"),
    spec("item_crafted", false, Domain::Item, "match", "Item", "the player crafts an item"),
    spec("item_smelted", false, Domain::Item, "match", "Item", "the player takes a smelted item"),
    spec("item_picked_up", false, Domain::Item, "match", "Item", "the player picks up an item"),
    spec("item_eaten", false, Domain::Item, "match", "Item", "the player finishes eating an item"),
    spec("entity_killed", false, Domain::Entity, "match", "Entity", "the player kills an entity"),
    KindSpec { min_damage: true, ..spec("entity_hurt", false, Domain::Entity, "match", "Entity", "the player damages an entity") },
    KindSpec { min_damage: true, by: true, ..spec("player_hurt", false, Domain::Damage, "match", "Damage type", "the player takes damage") },
    spec("player_died", false, Domain::Damage, "match", "Damage type", "the player dies"),
    spec("advancement", false, Domain::Advancement, "match", "Advancement", "the player earns an advancement"),
    KindSpec { days: true, ..spec("time_phase", false, Domain::Phase, "match", "Phase", "a time-system phase begins (optionally within a day range)") },
    spec("proximity", false, Domain::Proximity, "match", "Proximity rule", "a proximity rule is confirmed near the player (mechanics/proximity.js)"),
    spec("custom", false, Domain::Free, "match", "Event name", "fired from code: global.Guides.fire(player, 'name')"),
];

pub fn spec_of(sweep: bool, name: &str) -> Option<&'static KindSpec> {
    KINDS.iter().find(|k| k.sweep == sweep && k.name == name)
}

// ---------------------------------------------------------------- matchers

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum MKind {
    Id,
    Tag,
    Regex,
    /// a lost page item carrying this page id in its NBT: { "page": "id" }
    Page,
}

#[derive(Clone, PartialEq, Debug)]
pub struct Matcher {
    pub kind: MKind,
    pub text: String,
}

impl Matcher {
    pub fn new(kind: MKind) -> Matcher {
        Matcher { kind, text: String::new() }
    }

    fn to_value(&self) -> Value {
        match self.kind {
            MKind::Id => json!(self.text),
            MKind::Tag => json!(format!("#{}", self.text)),
            MKind::Regex => json!({ "regex": self.text }),
            MKind::Page => json!({ "page": self.text }),
        }
    }

    fn from_value(v: &Value) -> Option<Matcher> {
        match v {
            Value::String(s) if s == "*" => None,
            Value::String(s) => Some(match s.strip_prefix('#') {
                Some(tag) => Matcher { kind: MKind::Tag, text: tag.to_string() },
                None => Matcher { kind: MKind::Id, text: s.clone() },
            }),
            Value::Object(o) if o.get("regex").is_some_and(Value::is_string) => {
                Some(Matcher { kind: MKind::Regex, text: o["regex"].as_str().unwrap().to_string() })
            }
            Value::Object(o) if o.get("page").is_some_and(Value::is_string) => {
                Some(Matcher { kind: MKind::Page, text: o["page"].as_str().unwrap().to_string() })
            }
            other => Some(Matcher { kind: MKind::Id, text: other.to_string() }),
        }
    }
}

fn matchers_from(v: Option<&Value>) -> Vec<Matcher> {
    match v {
        None | Some(Value::Null) => vec![],
        Some(Value::Array(a)) => a.iter().filter_map(Matcher::from_value).collect(),
        Some(single) => Matcher::from_value(single).into_iter().collect(),
    }
}

fn matchers_to(ms: &[Matcher]) -> Option<Value> {
    match ms.len() {
        0 => None,
        1 => Some(ms[0].to_value()),
        _ => Some(Value::Array(ms.iter().map(Matcher::to_value).collect())),
    }
}

// ---------------------------------------------------------------- triggers

#[derive(Clone)]
pub struct Trigger {
    pub sweep: bool,
    pub kind: String,
    pub matchers: Vec<Matcher>,
    pub by: Vec<Matcher>,
    pub distance: Option<f64>,
    pub count: Option<i64>,
    pub min_damage: Option<f64>,
    pub min_day: Option<i64>,
    pub max_day: Option<i64>,
    pub test: String,
    pub where_fn: String,
    /// optional requirement on any trigger: the player must also be holding one of these items
    pub holding: Vec<Matcher>,
    /// which hand: "" (either), "main" or "off"
    pub hand: String,
    /// unknown keys are carried through untouched
    pub extra: Map<String, Value>,
}

impl Trigger {
    pub fn new(sweep: bool, kind: &str) -> Trigger {
        Trigger {
            sweep,
            kind: kind.to_string(),
            matchers: vec![],
            by: vec![],
            distance: None,
            count: None,
            min_damage: None,
            min_day: None,
            max_day: None,
            test: String::new(),
            where_fn: String::new(),
            holding: vec![],
            hand: String::new(),
            extra: Map::new(),
        }
    }

    fn from_value(v: &Value) -> Trigger {
        let obj = v.as_object().cloned().unwrap_or_default();
        let sweep = obj.contains_key("sweep");
        let kind = obj.get(if sweep { "sweep" } else { "event" }).and_then(Value::as_str).unwrap_or("").to_string();
        let field = spec_of(sweep, &kind).map_or("match", |s| if s.field.is_empty() { "match" } else { s.field });
        let mut t = Trigger::new(sweep, &kind);
        t.matchers = matchers_from(obj.get(field));
        t.by = matchers_from(obj.get("by"));
        t.distance = obj.get("distance").and_then(Value::as_f64);
        t.count = obj.get("count").and_then(Value::as_i64);
        t.min_damage = obj.get("minDamage").and_then(Value::as_f64);
        t.min_day = obj.get("minDay").and_then(Value::as_i64);
        t.max_day = obj.get("maxDay").and_then(Value::as_i64);
        t.holding = matchers_from(obj.get("holding"));
        t.hand = obj.get("hand").and_then(Value::as_str).unwrap_or("").to_string();
        t.test = obj.get("test").and_then(Value::as_str).unwrap_or("").to_string();
        t.where_fn = obj.get("where").and_then(Value::as_str).unwrap_or("").to_string();
        for (k, val) in obj {
            if !["sweep", "event", "match", "item", "by", "distance", "count", "minDamage", "minDay", "maxDay", "test", "where", "holding", "hand"].contains(&k.as_str()) {
                t.extra.insert(k, val);
            }
        }
        t
    }

    fn to_value(&self) -> Value {
        let mut o = Map::new();
        o.insert(if self.sweep { "sweep" } else { "event" }.into(), json!(self.kind));
        let spec = spec_of(self.sweep, &self.kind);
        let field = spec.map_or("match", |s| s.field);
        if !field.is_empty() {
            if let Some(v) = matchers_to(&self.matchers) {
                o.insert(field.into(), v);
            }
        }
        if spec.is_some_and(|s| s.by) {
            if let Some(v) = matchers_to(&self.by) {
                o.insert("by".into(), v);
            }
        }
        if spec.is_some_and(|s| s.distance) {
            if let Some(d) = self.distance {
                o.insert("distance".into(), json!(d));
            }
        }
        if spec.is_some_and(|s| s.count) {
            if let Some(c) = self.count {
                o.insert("count".into(), json!(c));
            }
        }
        if spec.is_some_and(|s| s.min_damage) {
            if let Some(d) = self.min_damage {
                o.insert("minDamage".into(), json!(d));
            }
        }
        if spec.is_some_and(|s| s.days) {
            if let Some(d) = self.min_day {
                o.insert("minDay".into(), json!(d));
            }
            if let Some(d) = self.max_day {
                o.insert("maxDay".into(), json!(d));
            }
        }
        if let Some(v) = matchers_to(&self.holding) {
            o.insert("holding".into(), v);
        }
        if (!self.holding.is_empty() || spec.is_some_and(|s| s.hand)) && matches!(self.hand.as_str(), "main" | "off") {
            o.insert("hand".into(), json!(self.hand));
        }
        if spec.is_some_and(|s| s.test) && !self.test.is_empty() {
            o.insert("test".into(), json!(self.test));
        }
        if !self.where_fn.is_empty() {
            o.insert("where".into(), json!(self.where_fn));
        }
        for (k, v) in &self.extra {
            o.insert(k.clone(), v.clone());
        }
        Value::Object(o)
    }
}

// ---------------------------------------------------------------- guides

#[derive(Clone)]
pub struct Guide {
    pub id: String,
    pub title: String,
    /// id of the book this page belongs to
    pub book: String,
    pub content: String,
    /// content that is not a plain string (text components); preserved, not editable here
    pub raw_content: Option<Value>,
    pub unlock: Vec<Trigger>,
    pub extra: Map<String, Value>,
}

impl Guide {
    pub fn blank(existing: &[Guide], book: &str) -> Guide {
        let mut n = existing.len() + 1;
        while existing.iter().any(|g| g.id == format!("new_guide_{n}")) {
            n += 1;
        }
        Guide {
            id: format!("new_guide_{n}"),
            title: "New Guide".into(),
            book: book.to_string(),
            content: String::new(),
            raw_content: None,
            unlock: vec![],
            extra: Map::new(),
        }
    }

    fn from_value(v: &Value) -> Guide {
        let obj = v.as_object().cloned().unwrap_or_default();
        let (content, raw_content) = match obj.get("content") {
            Some(Value::String(s)) => (s.clone(), None),
            Some(other) => (String::new(), Some(other.clone())),
            None => (String::new(), None),
        };
        let unlock = match obj.get("unlock") {
            Some(Value::Array(a)) => a.iter().map(Trigger::from_value).collect(),
            Some(single @ Value::Object(_)) => vec![Trigger::from_value(single)],
            _ => vec![],
        };
        let mut extra = Map::new();
        for (k, val) in &obj {
            if !["id", "title", "book", "content", "unlock"].contains(&k.as_str()) {
                extra.insert(k.clone(), val.clone());
            }
        }
        Guide {
            id: obj.get("id").and_then(Value::as_str).unwrap_or("").to_string(),
            title: obj.get("title").and_then(Value::as_str).unwrap_or("").to_string(),
            book: obj.get("book").and_then(Value::as_str).unwrap_or("").to_string(),
            content,
            raw_content,
            unlock,
            extra,
        }
    }

    pub fn unlock_json(&self) -> Value {
        Value::Array(self.unlock.iter().map(Trigger::to_value).collect())
    }

    fn to_value(&self) -> Value {
        let mut o = Map::new();
        o.insert("id".into(), json!(self.id));
        o.insert("title".into(), json!(self.title));
        o.insert("book".into(), json!(self.book));
        o.insert("content".into(), self.raw_content.clone().unwrap_or_else(|| json!(self.content)));
        o.insert("unlock".into(), self.unlock_json());
        for (k, v) in &self.extra {
            o.insert(k.clone(), v.clone());
        }
        Value::Object(o)
    }
}

// ---------------------------------------------------------------- books

/// A book is a guide-book item's identity: its name, author and tooltip. Its pages are the guides whose
/// `book` is its id, in file order.
#[derive(Clone)]
pub struct Book {
    pub id: String,
    pub name: String,
    pub author: String,
    pub tooltip: String,
    /// page title color: a Minecraft color name (dark_red) or #rrggbb; empty = dark_red
    pub title_color: String,
    /// item texture ("namespace:path", e.g. minecraft:item/enchanted_book); empty = the default book texture
    pub texture: String,
    /// CustomModelData number that selects this book's model; assigned once and then kept stable
    pub model: Option<i64>,
    pub extra: Map<String, Value>,
}

impl Book {
    pub fn default_book() -> Book {
        Book {
            id: "guide".into(),
            name: "Guide Book".into(),
            author: String::new(),
            tooltip: String::new(),
            title_color: String::new(),
            texture: String::new(),
            model: None,
            extra: Map::new(),
        }
    }

    pub fn blank(existing: &[Book]) -> Book {
        let mut n = existing.len() + 1;
        while existing.iter().any(|b| b.id == format!("new_book_{n}")) {
            n += 1;
        }
        Book { id: format!("new_book_{n}"), name: "New Book".into(), ..Book::default_book() }
    }

    fn from_value(v: &Value) -> Book {
        let obj = v.as_object().cloned().unwrap_or_default();
        let text = |k: &str| obj.get(k).and_then(Value::as_str).unwrap_or("").to_string();
        let mut extra = Map::new();
        for (k, val) in &obj {
            if !["id", "name", "author", "tooltip", "titleColor", "texture", "model"].contains(&k.as_str()) {
                extra.insert(k.clone(), val.clone());
            }
        }
        Book {
            id: text("id"),
            name: text("name"),
            author: text("author"),
            tooltip: text("tooltip"),
            title_color: text("titleColor"),
            texture: text("texture"),
            model: obj.get("model").and_then(Value::as_i64),
            extra,
        }
    }

    fn to_value(&self) -> Value {
        let mut o = Map::new();
        o.insert("id".into(), json!(self.id));
        o.insert("name".into(), json!(self.name));
        if !self.author.is_empty() {
            o.insert("author".into(), json!(self.author));
        }
        if !self.tooltip.is_empty() {
            o.insert("tooltip".into(), json!(self.tooltip));
        }
        if !self.title_color.is_empty() {
            o.insert("titleColor".into(), json!(self.title_color));
        }
        if !self.texture.is_empty() {
            o.insert("texture".into(), json!(self.texture));
            if let Some(m) = self.model {
                o.insert("model".into(), json!(m));
            }
        }
        for (k, v) in &self.extra {
            o.insert(k.clone(), v.clone());
        }
        Value::Object(o)
    }
}

/// A Minecraft color name (dark_red) or #rrggbb as RGB.
pub fn parse_color(c: &str) -> Option<[u8; 3]> {
    if let Some((_, _, rgb)) = crate::font::PALETTE.iter().find(|(name, _, _)| *name == c) {
        return Some(*rgb);
    }
    let hex = c.strip_prefix('#')?;
    if hex.len() != 6 || !hex.chars().all(|ch| ch.is_ascii_hexdigit()) {
        return None;
    }
    let v = u32::from_str_radix(hex, 16).ok()?;
    Some([(v >> 16) as u8, (v >> 8) as u8, v as u8])
}

/// The string to store for a picked color: its name if it is exactly a palette color, else #rrggbb.
pub fn color_to_string(rgb: [u8; 3]) -> String {
    match crate::font::PALETTE.iter().find(|(_, _, c)| *c == rgb) {
        Some((name, _, _)) => name.to_string(),
        None => format!("#{:02x}{:02x}{:02x}", rgb[0], rgb[1], rgb[2]),
    }
}

/// Gives every textured book a CustomModelData number (the next free one) if it has none yet.
pub fn assign_models(books: &mut [Book]) {
    let mut next = books.iter().filter_map(|b| b.model).max().unwrap_or(0) + 1;
    for b in books.iter_mut().filter(|b| !b.texture.is_empty() && b.model.is_none()) {
        b.model = Some(next);
        next += 1;
    }
}

// ---------------------------------------------------------------- lost pages

/// A lost page is an item (kubejs:lost_page) tagged with its id; triggers can match it by `{ "page": id }`.
#[derive(Clone)]
pub struct Page {
    pub id: String,
    pub name: String,
    pub tooltip: String,
    pub extra: Map<String, Value>,
}

impl Page {
    pub fn blank(existing: &[Page]) -> Page {
        let mut n = existing.len() + 1;
        while existing.iter().any(|p| p.id == format!("lost_page_{n}")) {
            n += 1;
        }
        Page { id: format!("lost_page_{n}"), name: format!("Lost Page {n}"), tooltip: String::new(), extra: Map::new() }
    }

    fn from_value(v: &Value) -> Page {
        let obj = v.as_object().cloned().unwrap_or_default();
        let text = |k: &str| obj.get(k).and_then(Value::as_str).unwrap_or("").to_string();
        let extra = obj.iter().filter(|(k, _)| !["id", "name", "tooltip"].contains(&k.as_str())).map(|(k, v)| (k.clone(), v.clone())).collect();
        Page { id: text("id"), name: text("name"), tooltip: text("tooltip"), extra }
    }

    fn to_value(&self) -> Value {
        let mut o = Map::new();
        o.insert("id".into(), json!(self.id));
        o.insert("name".into(), json!(self.name));
        if !self.tooltip.is_empty() {
            o.insert("tooltip".into(), json!(self.tooltip));
        }
        for (k, v) in &self.extra {
            o.insert(k.clone(), v.clone());
        }
        Value::Object(o)
    }
}

/// Item model JSON for the guide book item: the default texture plus one override per textured book.
/// Returns (file name, contents) pairs for kubejs/assets/kubejs/models/item/. Files carry a marker key so
/// the editor only ever deletes files it generated itself.
pub fn model_files(books: &[Book]) -> Vec<(String, String)> {
    let mut out = Vec::new();
    let textured: Vec<&Book> = books.iter().filter(|b| !b.texture.is_empty() && b.model.is_some()).collect();
    if textured.is_empty() {
        return out;
    }
    let overrides: Vec<Value> = textured
        .iter()
        .map(|b| json!({ "predicate": { "custom_model_data": b.model }, "model": format!("kubejs:item/guide_book_{}", b.id) }))
        .collect();
    let base = json!({
        "_generated_by": MODEL_MARKER,
        "parent": "item/generated",
        "textures": { "layer0": "minecraft:item/writable_book" },
        "overrides": overrides,
    });
    out.push(("guide_book.json".to_string(), serde_json::to_string_pretty(&base).unwrap() + "\n"));
    for b in textured {
        let m = json!({ "_generated_by": MODEL_MARKER, "parent": "item/generated", "textures": { "layer0": b.texture } });
        out.push((format!("guide_book_{}.json", b.id), serde_json::to_string_pretty(&m).unwrap() + "\n"));
    }
    out
}

pub const MODEL_MARKER: &str = "guide_editor";

/// Files without a `books` list (older ones) get one default book, and guides without a `book` join the first.
pub fn parse(text: &str) -> Result<(Vec<Book>, Vec<Page>, Vec<Guide>), String> {
    let root: Value = serde_json::from_str(text).map_err(|e| e.to_string())?;
    let (books_v, guides_v) = match &root {
        Value::Array(a) => (None, a),
        Value::Object(o) => (o.get("books").and_then(Value::as_array), o.get("guides").and_then(Value::as_array).ok_or("missing \"guides\" array")?),
        _ => return Err("expected an object with a \"guides\" array".into()),
    };
    let pages: Vec<Page> = match &root {
        Value::Object(o) => o.get("pages").and_then(Value::as_array).map(|a| a.iter().map(Page::from_value).collect()).unwrap_or_default(),
        _ => vec![],
    };
    let mut books: Vec<Book> = books_v.map(|a| a.iter().map(Book::from_value).collect()).unwrap_or_default();
    if books.is_empty() {
        books.push(Book::default_book());
    }
    let mut guides: Vec<Guide> = guides_v.iter().map(Guide::from_value).collect();
    for g in &mut guides {
        if g.book.is_empty() {
            g.book = books[0].id.clone();
        }
    }
    Ok((books, pages, guides))
}

pub fn serialize(books: &[Book], pages: &[Page], guides: &[Guide]) -> String {
    let mut root = json!({
        "books": books.iter().map(Book::to_value).collect::<Vec<_>>(),
        "guides": guides.iter().map(Guide::to_value).collect::<Vec<_>>(),
    });
    if !pages.is_empty() {
        root["pages"] = Value::Array(pages.iter().map(Page::to_value).collect());
    }
    let mut s = serde_json::to_string_pretty(&root).unwrap_or_default();
    s.push('\n');
    s
}

// ---------------------------------------------------------------- validation

pub struct Problem {
    pub guide: Option<usize>,
    pub error: bool,
    pub msg: String,
}

fn check_matchers(ms: &[Matcher], pages: &[Page], domain: Domain, reg: &Registry, allow_unknown: bool, what: &str, out: &mut Vec<String>) {
    let (ids, tags) = domain.keys();
    for m in ms {
        if m.text.trim().is_empty() {
            out.push(format!("{what}: empty matcher"));
            continue;
        }
        if allow_unknown {
            continue;
        }
        match m.kind {
            MKind::Id => {
                if let Some(key) = ids.filter(|k| reg.has_list(k)) {
                    if !reg.contains(key, &m.text) {
                        out.push(format!("{what}: '{}' is not a known {key}", m.text));
                    }
                }
            }
            MKind::Tag => match tags.filter(|k| reg.has_list(k)) {
                Some(key) => {
                    if !reg.contains(key, &m.text) {
                        out.push(format!("{what}: '#{}' is not a known {key}", m.text));
                    }
                }
                None if tags.is_none() && !matches!(domain, Domain::Free | Domain::None) => {
                    out.push(format!("{what}: tags are not supported here"));
                }
                None => {}
            },
            MKind::Regex => {}
            MKind::Page => {
                if !pages.iter().any(|p| p.id == m.text) {
                    out.push(format!("{what}: lost page '{}' does not exist", m.text));
                }
            }
        }
    }
}

pub fn validate(books: &[Book], pages: &[Page], guides: &[Guide], reg: &Registry, allow_unknown: bool) -> Vec<Problem> {
    let mut out = Vec::new();
    let mut seen_pages = HashSet::new();
    for (i, p) in pages.iter().enumerate() {
        let name = if p.id.is_empty() { format!("lost page #{}", i + 1) } else { format!("lost page {}", p.id) };
        let mut err = |msg: String| out.push(Problem { guide: None, error: true, msg });
        if p.id.is_empty() || !p.id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_') {
            err(format!("{name}: id must be made of a-z, 0-9 and _"));
        } else if !seen_pages.insert(p.id.clone()) {
            err(format!("{name}: duplicate id"));
        }
        if p.name.trim().is_empty() {
            err(format!("{name}: name is empty"));
        }
    }
    let mut seen_books = HashSet::new();
    for (i, b) in books.iter().enumerate() {
        let name = if b.id.is_empty() { format!("book #{}", i + 1) } else { format!("book {}", b.id) };
        if b.name.chars().count() > 32 {
            out.push(Problem {
                guide: None,
                error: false,
                msg: format!("{name}: name is over 32 characters; the opened book's title is cut to 32 (the item name is not)"),
            });
        }
        let mut err = |msg: String| out.push(Problem { guide: None, error: true, msg });
        if b.id.is_empty() || !b.id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_') {
            err(format!("{name}: id must be made of a-z, 0-9 and _"));
        } else if !seen_books.insert(b.id.clone()) {
            err(format!("{name}: duplicate id"));
        }
        if b.name.trim().is_empty() {
            err(format!("{name}: name is empty"));
        }
        if !b.title_color.is_empty() && parse_color(&b.title_color).is_none() {
            err(format!("{name}: title color must be a color name or #rrggbb"));
        }
        let t = b.texture.trim();
        if !t.is_empty() {
            if !t.contains(':') || t.contains(' ') || t.ends_with(".png") || t.starts_with("textures/") {
                err(format!("{name}: texture must look like namespace:item/name (no .png, no textures/)"));
            } else if !allow_unknown && reg.has_list("Texture") && !reg.contains("Texture", t) {
                err(format!("{name}: texture '{t}' is not a known texture"));
            }
        }
    }
    let mut seen = HashSet::new();
    for (i, g) in guides.iter().enumerate() {
        let mut err = |msg: String| out.push(Problem { guide: Some(i), error: true, msg });
        let name = if g.id.is_empty() { format!("#{}", i + 1) } else { g.id.clone() };

        if g.id.is_empty() || !g.id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_') {
            err(format!("{name}: id must be made of a-z, 0-9 and _"));
        } else if !seen.insert(g.id.clone()) {
            err(format!("{name}: duplicate id"));
        }
        if g.title.trim().is_empty() {
            err(format!("{name}: title is empty"));
        }
        if !books.iter().any(|b| b.id == g.book) {
            err(format!("{name}: book '{}' does not exist", g.book));
        }
        if g.raw_content.is_none() && g.content.trim().is_empty() {
            err(format!("{name}: content is empty"));
        }

        for (n, t) in g.unlock.iter().enumerate() {
            let what = format!("{name} trigger {}", n + 1);
            let Some(spec) = spec_of(t.sweep, &t.kind) else {
                err(format!("{what}: unknown {} '{}'", if t.sweep { "sweep" } else { "event" }, t.kind));
                continue;
            };
            let mut problems = Vec::new();
            if !spec.field.is_empty() {
                check_matchers(&t.matchers, pages, spec.domain, reg, allow_unknown, &format!("{what} ({})", spec.label.to_lowercase()), &mut problems);
                if (spec.name == "inventory" || (spec.domain == Domain::Free)) && t.matchers.is_empty() {
                    problems.push(format!("{what}: needs at least one {}", spec.label.to_lowercase()));
                }
            }
            if spec.by {
                check_matchers(&t.by, pages, Domain::Entity, reg, allow_unknown, &format!("{what} (attacker)"), &mut problems);
            }
            if spec.test && t.test.trim().is_empty() {
                problems.push(format!("{what}: needs a function name"));
            }
            check_matchers(&t.holding, pages, Domain::Item, reg, allow_unknown, &format!("{what} (also holding)"), &mut problems);
            if spec.name == "holding" && spec.sweep && t.matchers.is_empty() {
                problems.push(format!("{what}: needs at least one item"));
            }
            if spec.days && t.min_day.zip(t.max_day).is_some_and(|(a, b)| a > b) {
                problems.push(format!("{what}: 'from day' is after 'until day'"));
            }
            if spec.count && t.count.is_some_and(|c| c < 1) {
                problems.push(format!("{what}: count must be at least 1"));
            }
            for p in problems {
                err(p);
            }
        }

        if g.unlock.is_empty() {
            out.push(Problem { guide: Some(i), error: false, msg: format!("{name}: no unlock trigger (can only be unlocked by command or code)") });
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE: &str = r##"{
      "books": [ { "id": "guide", "name": "Guide Book", "author": "Me", "tooltip": "hi", "texture": "minecraft:item/book", "model": 3, "extra_key": 1 } ],
      "guides": [
        { "id": "a", "title": "A", "book": "guide", "content": "x", "note": 1,
          "unlock": [
            { "sweep": "looking_at_block", "match": ["minecraft:stone", "#minecraft:logs", {"regex": "ore$"}], "distance": 6.0, "future": true },
            { "sweep": "inventory", "item": "minecraft:flint", "count": 3 },
            { "event": "player_hurt", "match": "fall", "by": "minecraft:zombie", "minDamage": 2.5 },
            { "event": "custom", "match": "welcome", "where": "fn_name" }
          ] }
      ] }"##;

    #[test]
    fn round_trip_preserves_everything() {
        let (books, pages, guides) = parse(SAMPLE).unwrap();
        let once = serialize(&books, &pages, &guides);
        let (b2, p2, g2) = parse(&once).unwrap();
        let again = serialize(&b2, &p2, &g2);
        assert_eq!(once, again);

        let original: Value = serde_json::from_str(SAMPLE).unwrap();
        let written: Value = serde_json::from_str(&once).unwrap();
        assert_eq!(original, written);
    }

    #[test]
    fn validation_flags_bad_data() {
        let reg = Registry::default(); // no ProbeJS data: ids are not checked
        let (books, pages, mut guides) = parse(SAMPLE).unwrap();
        assert!(validate(&books, &pages, &guides, &reg, false).iter().all(|p| !p.error));

        guides[0].id = "Bad Id".into();
        guides[0].unlock[1].matchers.clear();
        guides.push(guides[0].clone());
        guides[1].book = "nope".into();
        let errors: Vec<_> = validate(&books, &pages, &guides, &reg, false).into_iter().filter(|p| p.error).map(|p| p.msg).collect();
        assert!(errors.iter().any(|m| m.contains("id must be")), "{errors:?}");
        assert!(errors.iter().any(|m| m.contains("needs at least one item")), "{errors:?}");
        assert!(errors.iter().any(|m| m.contains("book 'nope' does not exist")), "{errors:?}");
    }

    #[test]
    fn models_are_assigned_and_generated() {
        let (mut books, _, _) = parse(SAMPLE).unwrap();
        books.push(Book { id: "b2".into(), name: "B2".into(), texture: "minecraft:item/enchanted_book".into(), ..Book::default_book() });
        assign_models(&mut books);
        assert_eq!(books[0].model, Some(3));
        assert_eq!(books[1].model, Some(4));
        let files = model_files(&books);
        assert_eq!(files.len(), 3);
        assert!(files[0].1.contains("\"custom_model_data\": 4"));
        assert!(files.iter().any(|(n, _)| n == "guide_book_b2.json"));
    }

    #[test]
    fn time_and_proximity_triggers_round_trip() {
        let text = r#"{"guides":[{"id":"a","title":"A","book":"guide","content":"x","unlock":[
            {"sweep":"time","match":["DUSK","NIGHT"],"minDay":2,"maxDay":9},
            {"event":"time_phase","match":"MIDNIGHT","minDay":3},
            {"event":"proximity","match":"gore"}]}]}"#;
        let (b, pages, g) = parse(text).unwrap();
        let again = parse(&serialize(&b, &pages, &g)).unwrap();
        assert_eq!(serialize(&b, &pages, &g), serialize(&again.0, &again.1, &again.2));
        assert_eq!(g[0].unlock[0].min_day, Some(2));
        assert_eq!(g[0].unlock[1].max_day, None);
        assert_eq!(spec_of(false, "proximity").unwrap().domain, Domain::Proximity);
    }

    #[test]
    fn holding_requirement_round_trips() {
        let text = r##"{"guides":[{"id":"a","title":"A","book":"guide","content":"x","unlock":[
            {"sweep":"holding","item":["minecraft:stick","#minecraft:axes"],"hand":"off"},
            {"event":"block_broken","match":"minecraft:stone","holding":"#minecraft:pickaxes"}]}]}"##;
        let (b, pages, g) = parse(text).unwrap();
        assert_eq!(g[0].unlock[0].hand, "off");
        assert_eq!(g[0].unlock[1].holding.len(), 1);
        let again = parse(&serialize(&b, &pages, &g)).unwrap();
        assert_eq!(serialize(&b, &pages, &g), serialize(&again.0, &again.1, &again.2));
        let reg = Registry::default();
        assert!(validate(&b, &pages, &g, &reg, false).iter().all(|p| !p.error));
    }

    #[test]
    fn title_colors_parse_and_validate() {
        assert_eq!(parse_color("dark_blue"), Some([0, 0, 0xAA]));
        assert_eq!(parse_color("#2a6f97"), Some([0x2a, 0x6f, 0x97]));
        assert_eq!(parse_color("#12345"), None);
        assert_eq!(parse_color("nope"), None);
        assert_eq!(color_to_string([0xAA, 0, 0]), "dark_red");
        assert_eq!(color_to_string([1, 2, 3]), "#010203");

        let (mut books, pages, guides) = parse(SAMPLE).unwrap();
        books[0].title_color = "bogus".into();
        let reg = Registry::default();
        assert!(validate(&books, &pages, &guides, &reg, false).iter().any(|p| p.error && p.msg.contains("title color")));
        books[0].title_color = "#2a6f97".into();
        assert!(validate(&books, &pages, &guides, &reg, false).iter().all(|p| !p.error));
        let (b2, _, _) = parse(&serialize(&books, &pages, &guides)).unwrap();
        assert_eq!(b2[0].title_color, "#2a6f97");
    }

    #[test]
    fn lost_pages_round_trip_and_match() {
        let text = r#"{"pages":[{"id":"torn","name":"Torn Page","tooltip":"t"}],"guides":[{"id":"a","title":"A","book":"guide","content":"x","unlock":[
            {"sweep":"inventory","item":{"page":"torn"}},
            {"event":"item_picked_up","match":[{"page":"torn"},"minecraft:stick"]}]}]}"#;
        let (b, pages, g) = parse(text).unwrap();
        assert_eq!(pages.len(), 1);
        assert_eq!(g[0].unlock[0].matchers[0].kind, MKind::Page);
        let again = parse(&serialize(&b, &pages, &g)).unwrap();
        assert_eq!(serialize(&b, &pages, &g), serialize(&again.0, &again.1, &again.2));
        let reg = Registry::default();
        assert!(validate(&b, &pages, &g, &reg, false).iter().all(|p| !p.error));
        assert!(validate(&b, &[], &g, &reg, false).iter().any(|p| p.error && p.msg.contains("does not exist")));
    }

    #[test]
    fn old_files_get_a_default_book() {
        let (books, _, guides) = parse(r#"{"guides":[{"id":"a","title":"A","content":"x"}]}"#).unwrap();
        assert_eq!(books.len(), 1);
        assert_eq!(guides[0].book, books[0].id);
    }

    #[test]
    fn empty_matchers_are_omitted_and_single_is_not_an_array() {
        let mut t = Trigger::new(false, "block_broken");
        assert_eq!(t.to_value(), json!({ "event": "block_broken" }));
        t.matchers.push(Matcher { kind: MKind::Tag, text: "minecraft:logs".into() });
        assert_eq!(t.to_value(), json!({ "event": "block_broken", "match": "#minecraft:logs" }));
    }
}
