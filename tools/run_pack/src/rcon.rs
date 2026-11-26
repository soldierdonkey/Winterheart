//! Minimal RCON client for the dedicated server (the client has no command listener in Winterheart).
//!
//! Replies are split into 4096-byte packets; a packet shorter than that ends the reply. The reply is not
//! terminated with a second "marker" request, because vanilla RCON reads one TCP segment per packet and
//! drops the connection if two packets arrive together.

use std::io::{self, BufRead, Read, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::Duration;

const HOST: [u8; 4] = [127, 0, 0, 1];
const TYPE_EXEC: i32 = 2;
const TYPE_AUTH: i32 = 3;
const CHUNK: usize = 4096;

fn addr(port: u16) -> SocketAddr {
    SocketAddr::from((HOST, port))
}

/// True if something accepts connections on 127.0.0.1:port.
pub fn port_open(port: u16) -> bool {
    TcpStream::connect_timeout(&addr(port), Duration::from_secs(1)).is_ok()
}

pub struct Rcon {
    sock: TcpStream,
    next_id: i32,
}

impl Rcon {
    pub fn connect(port: u16, password: &str, timeout: Duration) -> Result<Rcon, String> {
        let sock = TcpStream::connect_timeout(&addr(port), Duration::from_secs(5))
            .map_err(|e| format!("nothing listening on 127.0.0.1:{port} ({e})"))?;
        sock.set_read_timeout(Some(timeout)).ok();
        let mut r = Rcon { sock, next_id: 1 };
        let rid = r.send(TYPE_AUTH, password)?;
        let (pid, _) = r.recv()?;
        if pid == -1 || pid != rid {
            return Err("RCON login failed: wrong password".into());
        }
        Ok(r)
    }

    fn send(&mut self, ptype: i32, body: &str) -> Result<i32, String> {
        let rid = self.next_id;
        self.next_id += 1;
        let data = body.as_bytes();
        let mut pkt = Vec::with_capacity(data.len() + 14);
        pkt.extend_from_slice(&(data.len() as i32 + 10).to_le_bytes());
        pkt.extend_from_slice(&rid.to_le_bytes());
        pkt.extend_from_slice(&ptype.to_le_bytes());
        pkt.extend_from_slice(data);
        pkt.extend_from_slice(&[0, 0]);
        self.sock.write_all(&pkt).map_err(|e| format!("send failed ({e})"))?;
        Ok(rid)
    }

    fn read(&mut self, n: usize) -> Result<Vec<u8>, String> {
        let mut buf = vec![0; n];
        self.sock.read_exact(&mut buf).map_err(|e| match e.kind() {
            io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut => "timed out waiting for a reply".to_string(),
            io::ErrorKind::UnexpectedEof => "connection closed by the game".to_string(),
            _ => format!("read failed ({e})"),
        })?;
        Ok(buf)
    }

    fn recv(&mut self) -> Result<(i32, Vec<u8>), String> {
        let len = i32::from_le_bytes(self.read(4)?.try_into().unwrap());
        if len < 10 {
            return Err(format!("bad packet length {len}"));
        }
        let head = self.read(8)?;
        let pid = i32::from_le_bytes(head[0..4].try_into().unwrap());
        let mut body = self.read(len as usize - 8)?;
        body.truncate(body.len() - 2);
        Ok((pid, body))
    }

    pub fn command(&mut self, cmd: &str) -> Result<String, String> {
        let rid = self.send(TYPE_EXEC, cmd)?;
        let mut out = Vec::new();
        loop {
            let (pid, body) = self.recv()?;
            if pid == -1 {
                return Err("RCON rejected the command: not logged in".into());
            }
            if pid != rid {
                continue;
            }
            let last = body.len() < CHUNK;
            out.extend(body);
            if last {
                return Ok(String::from_utf8_lossy(&out).into_owned());
            }
        }
    }
}

fn strip_slash(cmd: &str) -> &str {
    let cmd = cmd.trim();
    cmd.strip_prefix('/').unwrap_or(cmd)
}

fn print_reply(text: &str) {
    let text = text.trim_end_matches('\n');
    if !text.is_empty() {
        println!("{text}");
    }
}

fn open(port: u16, password: &str) -> Result<Rcon, i32> {
    Rcon::connect(port, password, Duration::from_secs(60)).map_err(|e| {
        eprintln!("rcon: {e}");
        1
    })
}

/// Run each command and print its output. Returns the exit code.
pub fn run_commands(port: u16, password: &str, cmds: &[String], quiet: bool) -> i32 {
    let mut rcon = match open(port, password) {
        Ok(r) => r,
        Err(c) => return c,
    };
    for raw in cmds {
        let cmd = strip_slash(raw);
        match rcon.command(cmd) {
            Ok(reply) if !quiet => {
                println!("> /{cmd}");
                print_reply(&reply);
            }
            Ok(_) => {}
            Err(e) => {
                if !quiet {
                    eprintln!("rcon: {e}");
                }
                return 1;
            }
        }
    }
    0
}

/// Interactive prompt; Ctrl-D quits, Ctrl-C clears the line.
pub fn repl(port: u16, password: &str, label: &str) -> i32 {
    let mut rcon = match open(port, password) {
        Ok(r) => r,
        Err(c) => return c,
    };
    // Ctrl-C would otherwise kill the prompt; the terminal discards the typed line anyway.
    ctrlc::set_handler(|| {
        print!("\n> ");
        io::stdout().flush().ok();
    })
    .ok();
    println!("Connected to {label}. Commands without the leading /. Ctrl-D to quit.");
    let stdin = io::stdin();
    let mut line = String::new();
    loop {
        print!("> ");
        io::stdout().flush().ok();
        line.clear();
        match stdin.lock().read_line(&mut line) {
            Ok(0) | Err(_) => {
                println!();
                return 0;
            }
            Ok(_) => {}
        }
        let cmd = strip_slash(&line);
        if cmd.is_empty() {
            continue;
        }
        match rcon.command(cmd) {
            Ok(reply) => print_reply(&reply),
            Err(e) => {
                eprintln!("rcon: {e}");
                return 1;
            }
        }
        if cmd == "stop" {
            return 0;
        }
    }
}
