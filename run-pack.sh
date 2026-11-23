#!/usr/bin/env bash
# Launch the Winterheart instance from the shell, in offline mode, without the CurseForge app, as a client or
# as a Forge dedicated server, and send commands to a running game. See docs/features/run-pack.md.
#
# Usage: ./run-pack.sh [--username NAME] [--world NAME] [--dry-run] [-- extra game args]
#        ./run-pack.sh --server [--world NAME] [--accept-eula] [--run CMD]...
#        ./run-pack.sh [--server] --headless [--world NAME] [--until REGEX] [--timeout SECS] [--keep-running] [--run CMD]...
#        ./run-pack.sh --send CMD [--send CMD]...      (dedicated server only)
#        ./run-pack.sh --console                       (dedicated server only)
#
#   --world NAME     client: join single-player world NAME on start; server: level-name (a world in server/)
#   --server         run a Forge dedicated server in server/ (installed on first use; no window)
#   --accept-eula    server: accept the Minecraft EULA without asking (needed once, when not interactive)
#   --headless       run in the background (client window hidden); output to logs/run-pack-{headless,server}.out
#   --until REGEX    headless: wait for REGEX in the game's latest.log
#                    (default: "<user> joined the game" with a client --world, "Done (" for a server)
#   --timeout SECS   headless: give up after SECS (default 600 with a REGEX, else no limit)
#   --keep-running   headless: leave the game running once REGEX is seen instead of stopping it
#   --run CMD        server: once it is ready, run CMD (repeatable; no leading / needed)
#   --send CMD       run CMD in an already running dedicated server and print its output, then exit (repeatable)
#   --console        interactive command prompt to an already running dedicated server
# The client has no command listener (Soulrend's needs its soulrend_dev_utils mod), so --run, --send and
# --console only work with the dedicated server.
#
# Env overrides:
#   MC_INSTALL     CurseForge install dir (default ~/Documents/curseforge/minecraft/Install)
#   FORGE_VERSION  e.g. forge-47.4.22 (default: baseModLoader from minecraftinstance.json)
#   JAVA           java binary (default: CurseForge's java-runtime-gamma, Java 17)
#   MEMORY_MB      heap size (default: allocatedMemory from minecraftinstance.json, else 7040)
set -euo pipefail

GAME_DIR="$(cd "$(dirname "$0")" && pwd)"
MC_INSTALL="${MC_INSTALL:-$HOME/Documents/curseforge/minecraft/Install}"
PY_DIR="$GAME_DIR/python/run_pack"
SERVER_DIR="$GAME_DIR/server"
SERVER_RCON_PORT=25575
RCON_PASSWORD_FILE="$GAME_DIR/local/run-pack/rcon-password"

USERNAME="Dev"
DRY_RUN=0
HEADLESS=0
SERVER=0
ACCEPT_EULA=0
WORLD=""
UNTIL=""
TIMEOUT=""
KEEP_RUNNING=0
CONSOLE=0
RUN=()
SEND=()
EXTRA=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --username)     USERNAME="$2"; shift 2 ;;
    --world)        WORLD="$2"; shift 2 ;;
    --dry-run)      DRY_RUN=1; shift ;;
    --headless)     HEADLESS=1; shift ;;
    --server)       SERVER=1; shift ;;
    --accept-eula)  ACCEPT_EULA=1; shift ;;
    --until)        UNTIL="$2"; shift 2 ;;
    --timeout)      TIMEOUT="$2"; shift 2 ;;
    --keep-running) KEEP_RUNNING=1; shift ;;
    --run)          RUN+=("$2"); shift 2 ;;
    --send)         SEND+=("$2"); shift 2 ;;
    --console)      CONSOLE=1; shift ;;
    --)             shift; EXTRA=("$@"); break ;;
    -h|--help)      sed -n '2,30p' "$0"; exit 0 ;;
    *)              echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

if [[ $HEADLESS -eq 0 && ( -n "$UNTIL" || -n "$TIMEOUT" || $KEEP_RUNNING -eq 1 ) ]]; then
  echo "--until, --timeout and --keep-running only apply with --headless" >&2
  exit 2
fi
if [[ -n "$TIMEOUT" && ! "$TIMEOUT" =~ ^[0-9]+$ ]]; then
  echo "--timeout takes whole seconds" >&2
  exit 2
fi
if [[ $SERVER -eq 0 && ${#RUN[@]} -gt 0 ]]; then
  echo "--run needs --server: the client has no command listener" >&2
  exit 2
fi
if [[ ${#SEND[@]} -gt 0 && $CONSOLE -eq 1 ]]; then
  echo "Use either --send or --console" >&2
  exit 2
fi

# ---- RCON: on-demand commands ----
# Dedicated server only (vanilla RCON). The password is generated once into local/ (gitignored).

ensure_password() {
  [[ -s "$RCON_PASSWORD_FILE" ]] && return 0
  mkdir -p "$(dirname "$RCON_PASSWORD_FILE")"
  (umask 077; python3 -I -c 'import secrets; print(secrets.token_hex(16))' >"$RCON_PASSWORD_FILE")
}

port_open() { python3 -I "$PY_DIR/rcon.py" --port "$1" --probe; }

rcon() {  # rcon PORT [rcon.py args...]
  local port="$1"; shift
  python3 -I "$PY_DIR/rcon.py" --port "$port" --password-file "$RCON_PASSWORD_FILE" "$@"
}

if [[ ${#SEND[@]} -gt 0 || $CONSOLE -eq 1 ]]; then
  if [[ $SERVER -eq 1 || $HEADLESS -eq 1 || -n "$WORLD" || ${#RUN[@]} -gt 0 || $DRY_RUN -eq 1 ]]; then
    echo "--send and --console talk to a game that is already running; they can't be combined with launch options" >&2
    exit 2
  fi
  if [[ ! -s "$RCON_PASSWORD_FILE" ]]; then
    echo "No RCON password yet ($RCON_PASSWORD_FILE): start the game with run-pack.sh first" >&2
    exit 1
  fi
  if ! port_open $SERVER_RCON_PORT; then
    echo "No running dedicated server found: start one with ./run-pack.sh --server" >&2
    exit 1
  fi
  PORT=$SERVER_RCON_PORT
  if [[ $CONSOLE -eq 1 ]]; then
    exec python3 -I "$PY_DIR/rcon.py" --port "$PORT" --password-file "$RCON_PASSWORD_FILE" --repl --label server
  fi
  exec python3 -I "$PY_DIR/rcon.py" --port "$PORT" --password-file "$RCON_PASSWORD_FILE" "${SEND[@]}"
fi

# ---- Launch ----

if [[ $SERVER -eq 1 && ${#EXTRA[@]} -gt 0 ]]; then
  echo "Extra game args after -- only apply to the client" >&2
  exit 2
fi

if [[ ! -d "$MC_INSTALL/versions" ]]; then
  echo "CurseForge install not found at $MC_INSTALL (set MC_INSTALL)" >&2
  exit 1
fi

if [[ -z "${JAVA:-}" ]]; then
  JAVA="$MC_INSTALL/java/java-runtime-gamma/Contents/Home/bin/java"
  [[ -x "$JAVA" ]] || JAVA="$(command -v java)"
fi

server_setup() {
  GAME_DIR="$GAME_DIR" JAVA="$JAVA" FORGE_VERSION="${FORGE_VERSION:-}" MEMORY_MB="${MEMORY_MB:-}" \
  WORLD="$WORLD" RCON_PORT="$SERVER_RCON_PORT" RCON_PASSWORD_FILE="$RCON_PASSWORD_FILE" ACCEPT_EULA="$ACCEPT_EULA" \
  python3 -I "$PY_DIR/server_setup.py" "$@"
}

if [[ $SERVER -eq 1 ]]; then
  RUN_DIR="$SERVER_DIR"
  PORT=$SERVER_RCON_PORT
  LOG="$SERVER_DIR/logs/latest.log"
  CRASH_DIR="$SERVER_DIR/crash-reports"
  OUT="$GAME_DIR/logs/run-pack-server.out"
  PID_FILE="$SERVER_DIR/.run-pack.pid"
  if [[ $DRY_RUN -eq 1 ]]; then
    echo "cd $(printf '%q' "$SERVER_DIR") && $(printf '%q' "$JAVA") @user_jvm_args.txt @$(server_setup argsfile) nogui"
    exit 0
  fi
  if { [[ -s "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; } || port_open $SERVER_RCON_PORT; then
    echo "The server is already running. Stop it first (./run-pack.sh --send stop)." >&2
    exit 1
  fi
  ensure_password
  mkdir -p "$SERVER_DIR"
  server_setup install >/dev/null
  ARGS_FILE="$(server_setup prepare)"
  CMD=("$JAVA" @user_jvm_args.txt "@$ARGS_FILE" nogui)
else
  RUN_DIR="$GAME_DIR"
  PORT=""
  LOG="$GAME_DIR/logs/latest.log"
  CRASH_DIR="$GAME_DIR/crash-reports"
  OUT="$GAME_DIR/logs/run-pack-headless.out"
  PID_FILE=""

  # Build the java command from the Forge + vanilla version JSONs.
  # Python prints one argument per NUL-terminated record.
  ARGS=()
  while IFS= read -r -d '' arg; do ARGS+=("$arg"); done < <(
    GAME_DIR="$GAME_DIR" MC_INSTALL="$MC_INSTALL" USERNAME="$USERNAME" \
    FORGE_VERSION="${FORGE_VERSION:-}" MEMORY_MB="${MEMORY_MB:-}" \
    python3 -I "$PY_DIR/launch_args.py"
  )

  if [[ ${#ARGS[@]} -eq 0 ]]; then
    echo "Failed to build the launch command" >&2
    exit 1
  fi

  [[ -n "$WORLD" ]] && EXTRA=(--quickPlaySingleplayer "$WORLD" ${EXTRA[@]+"${EXTRA[@]}"})
  CMD=("$JAVA" "${ARGS[@]}" ${EXTRA[@]+"${EXTRA[@]}"})

  if [[ $DRY_RUN -eq 1 ]]; then
    printf '%q ' "${CMD[@]}"; echo
    exit 0
  fi

  if pgrep -f -- "--gameDir $GAME_DIR" >/dev/null; then
    echo "This instance is already running (CurseForge or another run-pack.sh). Close it first." >&2
    exit 1
  fi
fi

# Ready pattern: what --headless waits for, and what --run waits for before sending.
if [[ -z "$UNTIL" ]]; then
  if [[ $SERVER -eq 1 ]]; then
    UNTIL='Done \('
  elif [[ -n "$WORLD" ]]; then
    UNTIL="$USERNAME joined the game"
  fi
fi

latest_crash() { ls -t "$CRASH_DIR" 2>/dev/null | head -1 || true; }
log_inode() { stat -f %i "$LOG" 2>/dev/null || echo none; }
# Only read latest.log once the game has rotated it, so the previous run's log can't match.
log_matches() { [[ "$(log_inode)" != "$OLD_INODE" ]] && grep -Eq -- "$1" "$LOG" 2>/dev/null; }

OLD_INODE="$(log_inode)"
OLD_CRASH="$(latest_crash)"

# Wait until the ready pattern is in the log and the command port answers, then send RUN.
run_when_ready() {
  local start=$SECONDS limit=${TIMEOUT:-0}
  [[ $limit -eq 0 ]] && limit=600
  while (( SECONDS - start < limit )); do
    if { [[ -z "$UNTIL" ]] || log_matches "$UNTIL"; } && port_open "$PORT"; then
      rcon "$PORT" "${RUN[@]}"
      return $?
    fi
    sleep 1
  done
  echo "--run: the game wasn't ready after ${limit}s, commands not sent" >&2
  return 1
}

cd "$RUN_DIR"
if [[ $SERVER -eq 1 ]]; then
  echo "Starting the dedicated server (offline mode, world '${WORLD:-world}') with $("$JAVA" -version 2>&1 | head -1)"
else
  echo "Launching $USERNAME (offline) with $("$JAVA" -version 2>&1 | head -1)"
fi

if [[ $HEADLESS -eq 0 ]]; then
  # Foreground: the server console (stdin) works as usual. --run is sent from a background helper.
  [[ ${#RUN[@]} -gt 0 ]] && run_when_ready &
  [[ -n "$PID_FILE" ]] && echo $$ >"$PID_FILE"   # exec keeps this pid
  exec "${CMD[@]}"
fi

# ---- Headless ----
# Server: a plain background process. Client: macOS can't run it without a window, so the window exists
# but the app is kept hidden. NSRunningApplication.hide needs no Accessibility permission. Showing the
# window makes GLFW activate the app again, so the app is re-hidden every second until the game is ready.

[[ -z "$TIMEOUT" ]] && { [[ -n "$UNTIL" ]] && TIMEOUT=600 || TIMEOUT=0; }
mkdir -p "$GAME_DIR/logs"
OUT_REL="${OUT#"$GAME_DIR"/}"
LOG_REL="${LOG#"$GAME_DIR"/}"

hide_app() {
  [[ $SERVER -eq 1 ]] && return 0
  osascript -l JavaScript -e 'ObjC.import("AppKit");
    function run(a) {
      var app = $.NSRunningApplication.runningApplicationWithProcessIdentifier(parseInt(a[0]));
      if (!app.isNil() && !app.isHidden) app.hide;
    }' "$1" >/dev/null 2>&1 || true
}

"${CMD[@]}" </dev/null >"$OUT" 2>&1 &
PID=$!
[[ -n "$PID_FILE" ]] && echo "$PID" >"$PID_FILE"
echo "Headless: pid $PID, output in $OUT_REL, game log in $LOG_REL"
[[ -n "$UNTIL" ]] && echo "Waiting for /$UNTIL/ (timeout ${TIMEOUT}s)"

wait_exit() {  # wait_exit SECS: true once the game is gone
  local i
  for i in $(seq 1 "$1"); do
    kill -0 "$PID" 2>/dev/null || return 0
    sleep 1
  done
  ! kill -0 "$PID" 2>/dev/null
}

stop_game() {
  kill -0 "$PID" 2>/dev/null || return 0
  echo "Stopping the game (pid $PID)"
  if [[ $SERVER -eq 1 ]] && port_open "$PORT" && rcon "$PORT" stop >/dev/null 2>&1 && wait_exit 60; then
    return 0
  fi
  # Client: there is no command listener to ask it to quit, so it gets SIGTERM. That can segfault in GLFW
  # teardown and make macOS show a "Java quit unexpectedly" dialog; the world is saved by the shutdown hook first.
  kill -TERM "$PID" 2>/dev/null || true   # the shutdown hook saves the world
  wait_exit 60 && return 0
  echo "Still running after 60s, killing it" >&2
  kill -KILL "$PID" 2>/dev/null || true
}

summary() {
  local crash
  crash="$(latest_crash)"
  if [[ -f "$LOG" && "$(log_inode)" != "$OLD_INODE" ]]; then
    echo "$LOG_REL: $(grep -c '/ERROR\]' "$LOG" || true) ERROR lines"
  fi
  if [[ -n "$crash" && "$crash" != "$OLD_CRASH" ]]; then
    echo "Crash report: ${CRASH_DIR#"$GAME_DIR"/}/$crash"
  fi
}

trap 'echo; stop_game; summary; exit 130' INT TERM

START=$SECONDS
STATUS=""
while :; do
  if ! kill -0 "$PID" 2>/dev/null; then
    wait "$PID" && code=0 || code=$?
    STATUS="exited"
    echo "Game exited with code $code"
    break
  fi
  hide_app "$PID"
  if [[ -n "$UNTIL" ]] && log_matches "$UNTIL"; then
    STATUS="ready"
    echo "Ready after $((SECONDS - START))s: $(grep -Em1 -- "$UNTIL" "$LOG")"
    break
  fi
  if [[ $TIMEOUT -gt 0 && $((SECONDS - START)) -ge $TIMEOUT ]]; then
    STATUS="timeout"
    echo "Timed out after ${TIMEOUT}s" >&2
    break
  fi
  sleep 1
done

RUN_RC=0
if [[ "$STATUS" == ready && ${#RUN[@]} -gt 0 ]]; then
  TIMEOUT=60 run_when_ready || RUN_RC=1
fi
trap - INT TERM

case "$STATUS" in
  ready)
    if [[ $KEEP_RUNNING -eq 1 ]]; then
      if [[ $SERVER -eq 1 ]]; then
        echo "Left running (pid $PID). Commands: ./run-pack.sh --console; stop it with: ./run-pack.sh --send stop"
      else
        echo "Left running, hidden (pid $PID). Click its Dock icon to show it; stop it with: kill $PID"
      fi
      summary
      exit $RUN_RC
    fi
    stop_game; summary; exit $RUN_RC ;;
  timeout)
    stop_game; summary; exit 124 ;;
  exited)
    summary
    # A clean exit with nothing to wait for is a success; otherwise the game died before it was ready.
    [[ -z "$UNTIL" && $code -eq 0 ]] && exit 0
    exit 1 ;;
esac
