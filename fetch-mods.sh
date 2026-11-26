#!/usr/bin/env bash
# Fills mods/ from mods.json (and regenerates mods.json with --generate). Builds and runs the Rust tool in
# tools/fetch_mods. ./fetch-mods.sh --help lists the options. See docs/features/fetch-mods.md.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cargo build --quiet --release --manifest-path "$DIR/tools/fetch_mods/Cargo.toml"
FETCH_MODS_GAME_DIR="$DIR" exec "$DIR/tools/fetch_mods/target/release/fetch-mods" "$@"
