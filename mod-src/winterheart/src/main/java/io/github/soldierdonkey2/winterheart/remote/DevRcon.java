package io.github.soldierdonkey2.winterheart.remote;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.ClientCommandSourceStack;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Lets {@code run-pack.sh --send/--console/--run} run commands in a single-player world from the shell.
 * Ported from Soulrend's soulrend_dev_utils. See docs/features/run-pack.md.
 * <p>
 * Only active when the game was started with env {@code WINTERHEART_RCON=<port>:<password>} (set by run-pack.sh),
 * and only while an integrated server is running, so CurseForge launches open no port. Client only: the core
 * mod refuses to load on a dedicated server.
 * <p>
 * Commands whose root is a client command run on the client thread; everything else
 * runs on the integrated server thread at permission level 4, so it works with cheats off.
 * <p>
 * {@code quit} is handled here instead: it closes the whole client through {@code Minecraft.stop()}, the same
 * path as the window's close button, so run-pack.sh doesn't have to signal the JVM (a SIGTERM during GLFW
 * teardown can segfault and raise macOS's crash dialog).
 */
public final class DevRcon {
    public static final String ENV = "WINTERHEART_RCON";
    private static final long TIMEOUT_SECONDS = 30;

    private static RconListener listener;

    private DevRcon() {}

    public static synchronized void onServerStarted(ServerStartedEvent event) {
        String spec = System.getenv(ENV);
        if (spec == null || spec.isBlank() || listener != null) return;
        int colon = spec.indexOf(':');
        if (colon <= 0 || colon == spec.length() - 1) {
            Winterheart.LOGGER.error("{} must be <port>:<password>, RCON listener not started", ENV);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(spec.substring(0, colon));
        } catch (NumberFormatException e) {
            Winterheart.LOGGER.error("{} has a bad port, RCON listener not started", ENV);
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            listener = RconListener.start(port, spec.substring(colon + 1), command -> run(server, command));
            Winterheart.LOGGER.info("Dev RCON listening on 127.0.0.1:{}", port);
        } catch (Exception e) {
            Winterheart.LOGGER.error("Dev RCON failed to listen on 127.0.0.1:{}", port, e);
        }
    }

    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        if (listener != null) {
            listener.close();
            listener = null;
            Winterheart.LOGGER.info("Dev RCON stopped");
        }
    }

    /** Runs one command and returns its output. Called on an RCON client thread. */
    private static String run(MinecraftServer server, String raw) {
        String command = raw.strip();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isEmpty()) return "";
        Winterheart.LOGGER.info("[Dev RCON] /{}", command);

        String root = command.split(" ", 2)[0];
        if (command.equals("quit") && ClientCommandHandler.getDispatcher().getRoot().getChild(root) == null) {
            // Reply first, then stop on the client thread; the connection closes as the game exits.
            Thread stopper = new Thread(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().stop());
            }, "Dev RCON quit");
            stopper.setDaemon(true);
            stopper.start();
            return "Closing the game\n";
        }
        boolean client = ClientCommandHandler.getDispatcher().getRoot().getChild(root) != null;
        Capture capture = new Capture(client);
        String finalCommand = command;

        CompletableFuture<Void> done;
        if (client) {
            done = Minecraft.getInstance().submit(() -> runClient(finalCommand, capture));
        } else {
            done = server.submit(() -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture), finalCommand);
            });
        }
        try {
            done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            capture.append("(still running after " + TIMEOUT_SECONDS + "s, see logs/latest.log)");
        } catch (Exception e) {
            capture.append("Error executing /" + command + ": " + e);
            Winterheart.LOGGER.error("[Dev RCON] /{} failed", command, e);
        }
        return capture.text();
    }

    private static void runClient(String command, Capture capture) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            capture.append("No player loaded, can't run client command /" + command);
            return;
        }
        CaptureSourceStack source = new CaptureSourceStack(capture, player);
        try {
            ClientCommandHandler.getDispatcher().execute(new StringReader(command), source);
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal(e.getMessage()));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e));
            Winterheart.LOGGER.error("[Dev RCON] /{} failed", command, e);
        }
    }

    /**
     * Collects a command's output for the RCON reply. Everything is also logged; client command output is
     * echoed to chat as well, as if typed. Messages sent after the reply (async commands) only reach log/chat.
     */
    private static final class Capture implements CommandSource {
        private final StringBuffer buffer = new StringBuffer();
        private final boolean echoToChat;

        Capture(boolean echoToChat) {
            this.echoToChat = echoToChat;
        }

        void append(String line) {
            buffer.append(line).append('\n');
            Winterheart.LOGGER.info("[Dev RCON] {}", line);
        }

        String text() {
            return buffer.toString();
        }

        @Override
        public void sendSystemMessage(Component message) {
            append(message.getString());
            if (echoToChat) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) player.sendSystemMessage(message);
            }
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    /** Forge's client source sends success straight to chat; route it through the capture instead. */
    private static final class CaptureSourceStack extends ClientCommandSourceStack {
        private final Capture capture;

        CaptureSourceStack(Capture capture, LocalPlayer player) {
            super(capture, player.position(), player.getRotationVector(), 4, "Rcon", Component.literal("Rcon"), player);
            this.capture = capture;
        }

        @Override
        public void sendSuccess(Supplier<Component> message, boolean sendToAdmins) {
            capture.sendSystemMessage(message.get());
        }
    }
}
