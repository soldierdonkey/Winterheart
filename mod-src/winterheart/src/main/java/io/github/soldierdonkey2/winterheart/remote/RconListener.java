package io.github.soldierdonkey2.winterheart.remote;

import io.github.soldierdonkey2.winterheart.Winterheart;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * A minimal Source RCON server on 127.0.0.1, speaking the same protocol as vanilla's dedicated-server RCON,
 * so run-pack (tools/run_pack/src/rcon.rs) can use the same client code. Replies are split into 4096-byte packets like vanilla:
 * a reply packet shorter than 4096 bytes is the last one.
 */
final class RconListener {
    private static final int TYPE_RESPONSE = 0;
    private static final int TYPE_EXEC = 2;
    private static final int TYPE_AUTH_RESPONSE = 2;
    private static final int TYPE_AUTH = 3;
    private static final int MAX_PACKET = 4096 + 14;
    private static final int CHUNK = 4096;

    private final ServerSocket socket;
    private final byte[] password;
    private final Function<String, String> executor;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;

    private RconListener(ServerSocket socket, String password, Function<String, String> executor) {
        this.socket = socket;
        this.password = password.getBytes(StandardCharsets.UTF_8);
        this.executor = executor;
    }

    static RconListener start(int port, String password, Function<String, String> executor) throws IOException {
        // Explicit IPv4: getLoopbackAddress() gives ::1 on macOS, and run-pack connects to 127.0.0.1.
        ServerSocket socket = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
        RconListener listener = new RconListener(socket, password, executor);
        Thread thread = new Thread(listener::acceptLoop, "Winterheart Dev RCON Listener");
        thread.setDaemon(true);
        thread.start();
        return listener;
    }

    void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        for (Socket client : clients) {
            try {
                client.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = socket.accept();
                clients.add(client);
                Thread thread = new Thread(() -> handle(client), "Winterheart Dev RCON Client");
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                if (running) Winterheart.LOGGER.warn("Dev RCON accept failed", e);
            }
        }
    }

    private void handle(Socket client) {
        boolean authed = false;
        try (client) {
            DataInputStream in = new DataInputStream(client.getInputStream());
            OutputStream out = client.getOutputStream();
            while (running) {
                int length = Integer.reverseBytes(in.readInt());
                if (length < 10 || length > MAX_PACKET) return;
                byte[] packet = new byte[length];
                in.readFully(packet);
                ByteBuffer buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);
                int id = buf.getInt();
                int type = buf.getInt();
                byte[] body = Arrays.copyOfRange(packet, 8, length - 2);

                if (type == TYPE_AUTH) {
                    authed = body.length > 0 && MessageDigest.isEqual(body, password);
                    send(out, authed ? id : -1, TYPE_AUTH_RESPONSE, new byte[0]);
                    if (!authed) Winterheart.LOGGER.warn("Dev RCON: wrong password from {}", client.getInetAddress());
                } else if (type == TYPE_EXEC && authed) {
                    String reply = executor.apply(new String(body, StandardCharsets.UTF_8));
                    sendReply(out, id, reply.getBytes(StandardCharsets.UTF_8));
                } else if (type == TYPE_EXEC) {
                    send(out, -1, TYPE_AUTH_RESPONSE, new byte[0]);
                } else {
                    sendReply(out, id, ("Unknown request " + Integer.toHexString(type)).getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (EOFException | SocketException ignored) {
            // client disconnected, or listener closed
        } catch (IOException e) {
            Winterheart.LOGGER.warn("Dev RCON client error", e);
        } finally {
            clients.remove(client);
        }
    }

    private static void sendReply(OutputStream out, int id, byte[] reply) throws IOException {
        int start = 0;
        do {
            int len = Math.min(CHUNK, reply.length - start);
            send(out, id, TYPE_RESPONSE, Arrays.copyOfRange(reply, start, start + len));
            start += len;
        } while (start < reply.length);
        // Vanilla quirk this mirrors: a reply that is an exact multiple of 4096 bytes ends with a full packet,
        // so the client would wait for more. Send an empty packet to end it.
        if (reply.length > 0 && reply.length % CHUNK == 0) send(out, id, TYPE_RESPONSE, new byte[0]);
    }

    private static void send(OutputStream out, int id, int type, byte[] body) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(14 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(10 + body.length).putInt(id).putInt(type).put(body).put((byte) 0).put((byte) 0);
        out.write(buf.array());
        out.flush();
    }
}
