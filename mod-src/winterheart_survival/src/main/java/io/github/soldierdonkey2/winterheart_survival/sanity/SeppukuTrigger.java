package io.github.soldierdonkey2.winterheart_survival.sanity;

import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.api.Sanity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Plays the seppuku animation (the Seppuku mod's /seppuku command) once when a player's sanity drops to the
 * threshold. A player can trigger it again after recovering above it. Checked every 10 seconds.
 */
public final class SeppukuTrigger {
    private static final double THRESHOLD = 10;
    private static final int PROBE_INTERVAL = 200;
    private static final Set<String> TRIGGERED = new HashSet<>();

    private SeppukuTrigger() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % PROBE_INTERVAL != 0) return;
        run(server);
    }

    /** One check of every player's sanity. */
    public static void run(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getGameProfile().getName();
            if (Sanity.get(player) <= THRESHOLD) {
                if (TRIGGERED.add(name)) Cmd.run(server, "seppuku play " + name);
            } else {
                TRIGGERED.remove(name);
            }
        }
    }
}
