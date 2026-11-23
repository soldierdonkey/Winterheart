package io.github.soldierdonkey2.winterheart_guides;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** What the other Winterheart mods call. */
public final class GuideApi {
    private GuideApi() {}

    /** Unlocks a guide (popup + pause for everyone unless silent). Returns true if it was newly unlocked. */
    public static boolean unlock(MinecraftServer server, String id, boolean silent) {
        return GuideManager.unlock(server, id, silent, null);
    }

    /** Called by the time system when a phase begins. Matches {"event": "time_phase", "match": "<phase>"}. */
    public static void firePhase(MinecraftServer server, int day, String phase) {
        GuideManager.dispatchCore("time_phase", null, server, () -> {
            GuideManager.Payload p = new GuideManager.Payload();
            p.id = phase;
            p.day = day;
            return p;
        });
    }

    /** Called by proximity effects once a rule is confirmed for a player. Matches {"event": "proximity", "match": "<rule id>"}. */
    public static void fireProximity(ServerPlayer player, String ruleId) {
        GuideManager.dispatch("proximity", player, () -> {
            GuideManager.Payload p = new GuideManager.Payload();
            p.id = ruleId;
            return p;
        });
    }

    /** A custom event; matches {"event": "custom", "match": "<name>"}. */
    public static void fire(ServerPlayer player, String name) {
        GuideManager.dispatch("custom", player, () -> {
            GuideManager.Payload p = new GuideManager.Payload();
            p.id = name;
            return p;
        });
    }
}
