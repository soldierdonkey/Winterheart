"""Minimal RCON client for run-pack.sh (stdlib only). See docs/features/run-pack.md.

Talks to vanilla dedicated-server RCON (the client has no command listener in Winterheart).

  rcon.py --port P --password-file F CMD...    run each command, print its output
  rcon.py --port P --password-file F --repl    interactive prompt (Ctrl-D to quit)
  rcon.py --port P --probe                     exit 0 if something listens on the port

Replies are split into 4096-byte packets; a packet shorter than that ends the reply. The reply is not
terminated with a second "marker" request, because vanilla RCON reads one TCP segment per packet and
drops the connection if two packets arrive together.
"""
import argparse, socket, struct, sys

HOST = "127.0.0.1"
TYPE_RESPONSE, TYPE_EXEC, TYPE_AUTH = 0, 2, 3
CHUNK = 4096


class RconError(Exception):
    pass


class Rcon:
    def __init__(self, port, password, timeout):
        try:
            self.sock = socket.create_connection((HOST, port), timeout=5)
        except OSError as e:
            raise RconError(f"nothing listening on {HOST}:{port} ({e.strerror or e})")
        self.sock.settimeout(timeout)
        self.next_id = 1
        rid = self._send(TYPE_AUTH, password)
        pid, _, _ = self._recv()
        if pid == -1 or pid != rid:
            raise RconError("RCON login failed: wrong password")

    def _send(self, ptype, body):
        rid = self.next_id
        self.next_id += 1
        data = body.encode("utf-8")
        self.sock.sendall(struct.pack("<iii", len(data) + 10, rid, ptype) + data + b"\0\0")
        return rid

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            try:
                part = self.sock.recv(n - len(buf))
            except socket.timeout:
                raise RconError("timed out waiting for a reply")
            if not part:
                raise RconError("connection closed by the game")
            buf += part
        return buf

    def _recv(self):
        (length,) = struct.unpack("<i", self._read(4))
        if length < 10:
            raise RconError(f"bad packet length {length}")
        pid, ptype = struct.unpack("<ii", self._read(8))
        body = self._read(length - 8)[:-2]
        return pid, ptype, body

    def command(self, cmd):
        rid = self._send(TYPE_EXEC, cmd)
        out = b""
        while True:
            pid, _, body = self._recv()
            if pid == -1:
                raise RconError("RCON rejected the command: not logged in")
            if pid != rid:
                continue
            out += body
            if len(body) < CHUNK:
                return out.decode("utf-8", "replace")

    def close(self):
        self.sock.close()


def strip_slash(cmd):
    cmd = cmd.strip()
    return cmd[1:] if cmd.startswith("/") else cmd


def print_reply(text):
    text = text.rstrip("\n")
    if text:
        print(text)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, required=True)
    ap.add_argument("--password-file")
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("--repl", action="store_true")
    ap.add_argument("--probe", action="store_true")
    ap.add_argument("--label", default="")
    ap.add_argument("commands", nargs="*")
    a = ap.parse_args()

    if a.probe:
        try:
            socket.create_connection((HOST, a.port), timeout=1).close()
            return 0
        except OSError:
            return 1

    if not a.password_file:
        ap.error("--password-file is required")
    with open(a.password_file) as f:
        password = f.read().strip()

    try:
        rcon = Rcon(a.port, password, a.timeout)
    except RconError as e:
        print(f"rcon: {e}", file=sys.stderr)
        return 1

    try:
        if a.repl:
            try:
                import readline  # noqa: F401  (line editing + history for input())
            except ImportError:
                pass
            print(f"Connected to {a.label or HOST + ':' + str(a.port)}. Commands without the leading /. Ctrl-D to quit.")
            while True:
                try:
                    line = input("> ")
                except EOFError:
                    print()
                    return 0
                except KeyboardInterrupt:
                    print()
                    continue
                cmd = strip_slash(line)
                if not cmd:
                    continue
                print_reply(rcon.command(cmd))
                if cmd == "stop":
                    return 0
        else:
            for raw in a.commands:
                cmd = strip_slash(raw)
                print(f"> /{cmd}")
                print_reply(rcon.command(cmd))
        return 0
    except RconError as e:
        print(f"rcon: {e}", file=sys.stderr)
        return 1
    finally:
        rcon.close()


if __name__ == "__main__":
    sys.exit(main())
