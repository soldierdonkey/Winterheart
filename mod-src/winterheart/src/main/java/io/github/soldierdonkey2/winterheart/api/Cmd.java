package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.server.MinecraftServer;

/** Runs commands as the server, silently (what KubeJS's server.runCommandSilent did). */
public final class Cmd {
    private Cmd() {}

    public static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** Runs a command as the server, silently, and returns its result (0 when it failed or didn't parse). */
    public static int result(MinecraftServer server, String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSuppressedOutput());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return 0;
        }
    }
}
