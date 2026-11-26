#!/usr/bin/env bash
# Launch the Winterheart instance from the shell (offline, client or dedicated server, headless smoke tests,
# server RCON). Builds and runs the Rust tool in tools/run_pack. ./run-pack.sh --help lists the options.
# See docs/features/run-pack.md.
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
cargo build --quiet --release --manifest-path "$DIR/tools/run_pack/Cargo.toml"
RUN_PACK_GAME_DIR="$DIR" exec "$DIR/tools/run_pack/target/release/run-pack" "$@"
