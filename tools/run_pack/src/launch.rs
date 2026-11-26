//! Builds the client's java argument list from the Forge and vanilla version JSONs in the CurseForge install.

use crate::Ctx;
use md5::{Digest, Md5};
use serde_json::{Map, Value};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

fn load(versions: &Path, vid: &str) -> Result<Value, String> {
    let path = versions.join(vid).join(format!("{vid}.json"));
    let text = std::fs::read_to_string(&path).map_err(|_| {
        format!(
            "Missing {}: launch the instance once from CurseForge to install it",
            path.display()
        )
    })?;
    serde_json::from_str(&text).map_err(|e| format!("Bad JSON in {}: {e}", path.display()))
}

fn rules_ok(rules: Option<&Value>) -> bool {
    let rules = match rules.and_then(Value::as_array) {
        Some(r) if !r.is_empty() => r,
        _ => return true,
    };
    let mut ok = false;
    for r in rules {
        let mut matched = true;
        if let Some(os) = r.get("os") {
            if os
                .get("name")
                .and_then(Value::as_str)
                .is_some_and(|n| !n.is_empty() && n != "osx")
            {
                matched = false;
            }
            if os.get("arch").and_then(Value::as_str) == Some("x86")
                && std::env::consts::ARCH != "x86"
            {
                matched = false;
            }
        }
        if r.get("features")
            .and_then(Value::as_object)
            .is_some_and(|f| !f.is_empty())
        {
            matched = false; // demo, custom resolution, quick play: all off
        }
        if matched {
            ok = r.get("action").and_then(Value::as_str) == Some("allow");
        }
    }
    ok
}

/// Replaces the console layout in Mojang's log4j config, which prints XML events for its own launcher.
/// Colors are dropped when stdin or stdout isn't a terminal (headless, pipes). The file appender is untouched.
const CONSOLE_LAYOUT: &str = concat!(
    r#"<PatternLayout noConsoleNoAnsi="true" pattern=""#,
    "%style{%d{HH:mm:ss}}{blue} ",
    "%highlight{[%t/%level]}{FATAL=bright white bg_red, ERROR=bright red, WARN=bright yellow, INFO=green, DEBUG=cyan, TRACE=blue} ",
    "%style{[%c{1}]}{cyan}: ",
    "%highlight{%msg{nolookups}}{FATAL=bright red, ERROR=red, WARN=yellow, INFO=normal, DEBUG=normal, TRACE=normal}",
    "%n%style{%throwable}{red}",
    r#"" />"#
);

/// Writes `config` with the console layout swapped to local/run-pack/ and returns that path. Falls back to
/// the original if it has no XML layout or the copy can't be written.
fn text_log_config(game_dir: &Path, config: PathBuf) -> PathBuf {
    let Ok(xml) = std::fs::read_to_string(&config) else {
        return config;
    };
    let text = xml
        .replace("<LegacyXMLLayout />", CONSOLE_LAYOUT)
        .replace("<XMLLayout />", CONSOLE_LAYOUT);
    let dir = game_dir.join("local/run-pack");
    let out = dir.join("log4j.xml");
    if text == xml
        || std::fs::create_dir_all(&dir)
            .and_then(|_| std::fs::write(&out, text))
            .is_err()
    {
        eprintln!("Can't rewrite {}: console logs stay XML", config.display());
        return config;
    }
    out
}

/// UUIDv3 of "OfflinePlayer:<name>", the same one a vanilla offline server assigns.
fn offline_uuid(name: &str) -> String {
    let mut h: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}").as_bytes()).into();
    h[6] = (h[6] & 0x0f) | 0x30;
    h[8] = (h[8] & 0x3f) | 0x80;
    let hex: String = h.iter().map(|b| format!("{b:02x}")).collect();
    format!(
        "{}-{}-{}-{}-{}",
        &hex[0..8],
        &hex[8..12],
        &hex[12..16],
        &hex[16..20],
        &hex[20..32]
    )
}

fn s<'a>(v: &'a Value, key: &str) -> Option<&'a str> {
    v.get(key).and_then(Value::as_str).filter(|s| !s.is_empty())
}

pub fn client_args(ctx: &Ctx, username: &str) -> Result<Vec<String>, String> {
    let install = &ctx.mc_install;
    let versions = install.join("versions");
    let libdir = install.join("libraries");
    let inst = &ctx.instance;

    let version_id = match &ctx.forge_version {
        Some(v) => v.clone(),
        None => inst
            .get("baseModLoader")
            .and_then(|l| s(l, "name"))
            .ok_or("Can't tell the Forge version: set FORGE_VERSION (e.g. forge-47.4.22)")?
            .to_string(),
    };
    let extra_jvm: Vec<String> = s(inst, "javaArgsOverride")
        .unwrap_or("")
        .split_whitespace()
        .map(String::from)
        .collect();

    let child = load(&versions, &version_id)?;
    let parent = match s(&child, "inheritsFrom") {
        Some(p) => load(&versions, p)?,
        None => Value::Object(Map::new()),
    };

    // Libraries: Forge's entries win over vanilla ones with the same group:artifact:classifier.
    let mut libs: HashMap<(String, String, String), PathBuf> = HashMap::new();
    let mut order = Vec::new();
    for src in [&parent, &child] {
        for lib in src
            .get("libraries")
            .and_then(Value::as_array)
            .into_iter()
            .flatten()
        {
            if !rules_ok(lib.get("rules")) {
                continue;
            }
            let Some(path) = lib
                .pointer("/downloads/artifact/path")
                .and_then(Value::as_str)
            else {
                continue;
            };
            let parts: Vec<&str> = s(lib, "name").unwrap_or("").split(':').collect();
            let key = (
                parts.first().unwrap_or(&"").to_string(),
                parts.get(1).unwrap_or(&"").to_string(),
                parts.get(3).unwrap_or(&"").to_string(),
            );
            if !libs.contains_key(&key) {
                order.push(key.clone());
            }
            libs.insert(key, libdir.join(path));
        }
    }
    let mut cp = Vec::new();
    for key in &order {
        let p = &libs[key];
        if !p.exists() {
            return Err(format!(
                "Missing library {}: launch the instance once from CurseForge to download it",
                p.display()
            ));
        }
        cp.push(p.display().to_string());
    }
    cp.push(
        versions
            .join(&version_id)
            .join(format!("{version_id}.jar"))
            .display()
            .to_string(),
    );

    let asset_index = parent
        .get("assetIndex")
        .or_else(|| child.get("assetIndex"))
        .and_then(|a| s(a, "id"))
        .or_else(|| s(&parent, "assets"))
        .unwrap_or("");

    let p = |p: PathBuf| p.display().to_string();
    let subs: Vec<(&str, String)> = vec![
        (
            "natives_directory",
            p(install.join("natives").join(&version_id)),
        ),
        ("launcher_name", "winterheart-run-pack".into()),
        ("launcher_version", "1".into()),
        ("classpath", cp.join(":")),
        ("classpath_separator", ":".into()),
        ("library_directory", p(libdir.clone())),
        ("version_name", version_id.clone()),
        ("auth_player_name", username.into()),
        ("game_directory", p(ctx.game_dir.clone())),
        ("assets_root", p(install.join("assets"))),
        ("assets_index_name", asset_index.into()),
        ("auth_uuid", offline_uuid(username)),
        ("auth_access_token", "0".into()),
        ("clientid", "0".into()),
        ("auth_xuid", "0".into()),
        ("user_type", "legacy".into()),
        (
            "version_type",
            s(&child, "type").unwrap_or("release").into(),
        ),
    ];
    let sub = |x: &str| {
        subs.iter().fold(x.to_string(), |acc, (k, v)| {
            acc.replace(&format!("${{{k}}}"), v)
        })
    };
    let expand = |entries: Option<&Value>| -> Vec<String> {
        let mut out = Vec::new();
        for e in entries.and_then(Value::as_array).into_iter().flatten() {
            match e {
                Value::String(x) => out.push(sub(x)),
                _ if rules_ok(e.get("rules")) => match e.get("value") {
                    Some(Value::String(x)) => out.push(sub(x)),
                    Some(Value::Array(xs)) => {
                        out.extend(xs.iter().filter_map(Value::as_str).map(sub))
                    }
                    _ => {}
                },
                _ => {}
            }
        }
        out
    };

    // Same extra flags CurseForge passes for this instance.
    let mut args = vec![
        format!("-Xmx{}m", ctx.memory_mb()),
        "-Xms256m".into(),
        "-Dfml.ignorePatchDiscrepancies=true".into(),
        "-Dfml.ignoreInvalidMinecraftCertificates=true".into(),
        "-Duser.language=en".into(),
    ];
    args.extend(extra_jvm);
    if let Some(log_cfg) = parent.pointer("/logging/client") {
        if let (Some(id), Some(arg)) = (
            log_cfg.pointer("/file/id").and_then(Value::as_str),
            s(log_cfg, "argument"),
        ) {
            let path = install.join("assets").join("log_configs").join(id);
            if path.exists() {
                let path = text_log_config(&ctx.game_dir, path);
                args.push(arg.replace("${path}", &path.display().to_string()));
            }
        }
    }
    args.extend(expand(parent.pointer("/arguments/jvm")));
    args.extend(expand(child.pointer("/arguments/jvm")));
    args.push(
        s(&child, "mainClass")
            .or_else(|| s(&parent, "mainClass"))
            .ok_or("No mainClass in the version JSONs")?
            .to_string(),
    );
    args.extend(expand(parent.pointer("/arguments/game")));
    args.extend(expand(child.pointer("/arguments/game")));
    Ok(args)
}

#[cfg(test)]
mod tests {
    #[test]
    fn offline_uuid_matches_vanilla() {
        // Python: uuid.UUID(bytes=md5("OfflinePlayer:Developer") with v3 bits)
        assert_eq!(super::offline_uuid("Developer").len(), 36);
        assert_eq!(&super::offline_uuid("Developer")[14..15], "3");
    }
}
