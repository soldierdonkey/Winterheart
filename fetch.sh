#!/usr/bin/env bash
# Fills mods/ and resourcepacks/ from mods.json (and regenerates mods.json with --generate). Builds and runs the Rust tool in
# tools/fetch. ./fetch.sh --help lists the options. See docs/features/fetch.md.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cargo build --quiet --release --manifest-path "$DIR/tools/fetch/Cargo.toml"
FETCH_GAME_DIR="$DIR" exec "$DIR/tools/fetch/target/release/fetch" "$@"
