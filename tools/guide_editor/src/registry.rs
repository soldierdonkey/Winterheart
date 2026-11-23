//! Valid ids (items, blocks, entities, damage types, advancements and their tags) from `registry_ids.json`, a
//! snapshot written by the game's `/winterheart dump_ids` command: `{ "Item": ["a:b", ...], "BlockTag": [...], ... }`.

use std::collections::{HashMap, HashSet};
use std::path::Path;

#[derive(Default)]
pub struct Registry {
    lists: HashMap<String, Vec<String>>,
    sets: HashMap<String, HashSet<String>>,
    pub summary: String,
}

impl Registry {
    pub fn load(path: &Path) -> Registry {
        let mut reg = Registry::default();
        let text = match std::fs::read_to_string(path) {
            Ok(t) => t,
            Err(e) => {
                reg.summary = format!("registry ids not loaded ({}): {e}", path.display());
                return reg;
            }
        };

        let json: serde_json::Value = match serde_json::from_str(&text) {
            Ok(v) => v,
            Err(e) => {
                reg.summary = format!("registry_ids.json is not valid JSON: {e}");
                return reg;
            }
        };
        if let Some(map) = json.as_object() {
            for (name, list) in map {
                let mut values: Vec<String> = list.as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(str::to_string)).collect()).unwrap_or_default();
                values.sort();
                values.dedup();
                reg.sets.insert(name.clone(), values.iter().cloned().collect());
                reg.lists.insert(name.clone(), values);
            }
        }

        let parts: Vec<String> = ["Item", "Block", "EntityType", "DamageType", "Advancement"]
            .iter()
            .map(|k| format!("{} {}", reg.lists.get(*k).map_or(0, Vec::len), k))
            .collect();
        reg.summary = format!("registry: {}", parts.join(", "));
        reg
    }

    /// Adds a list that does not come from the registry dump (time phases, proximity rules).
    pub fn add_list(&mut self, key: &str, mut values: Vec<String>) {
        values.sort();
        values.dedup();
        self.sets.insert(key.to_string(), values.iter().cloned().collect());
        self.lists.insert(key.to_string(), values);
    }

    pub fn has_list(&self, key: &str) -> bool {
        self.lists.get(key).is_some_and(|l| !l.is_empty())
    }

    pub fn contains(&self, key: &str, value: &str) -> bool {
        self.sets.get(key).is_some_and(|s| s.contains(value))
    }

    /// Best matches first: prefix of the whole id, prefix of the path after `ns:`, then substring.
    pub fn suggest(&self, key: &str, query: &str, limit: usize) -> Vec<&str> {
        let Some(list) = self.lists.get(key) else { return vec![] };
        let q = query.to_lowercase();
        let (mut a, mut b, mut c) = (Vec::new(), Vec::new(), Vec::new());
        for v in list {
            let lv = v.to_lowercase();
            if lv.starts_with(&q) {
                a.push(v.as_str());
            } else if lv.split_once(':').is_some_and(|(_, p)| p.starts_with(&q)) {
                b.push(v.as_str());
            } else if lv.contains(&q) {
                c.push(v.as_str());
            }
            if a.len() >= limit {
                break;
            }
        }
        a.extend(b);
        a.extend(c);
        a.truncate(limit);
        a
    }
}
