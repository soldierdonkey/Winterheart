package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.server.MinecraftServer;

/** Runs commands as the server, silently (what KubeJS's server.runCommandSilent did). */
public final class Cmd {
    private Cmd() {}

    public static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
