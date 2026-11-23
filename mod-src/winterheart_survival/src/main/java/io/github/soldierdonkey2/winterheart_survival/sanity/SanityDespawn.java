package io.github.soldierdonkey2.winterheart_survival.sanity;

import io.github.soldierdonkey2.winterheart.api.Sanity;
import io.github.soldierdonkey2.winterheart_survival.WinterheartSurvival;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;

/** Despawns sanity-dimension stalkers once every player is above the sanity threshold (0-100). */
public final class SanityDespawn {
    private static final double THRESHOLD = 75;
    private static final Set<ResourceLocation> MOBS = Set.of(
            new ResourceLocation("sanitydim", "rotting_stalker"),
            new ResourceLocation("sanitydim", "sneaking_terror"));

    private SanityDespawn() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        run(server);
    }

    /** One check: despawn the stalkers if every player is above the threshold. Returns how many were despawned. */
    public static int run(MinecraftServer server) {

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return 0;
        for (ServerPlayer p : players) {
            if (Sanity.get(p) <= THRESHOLD) return 0;
        }
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (MOBS.contains(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()))) {
                    entity.discard();
                    count++;
                }
            }
        }
        if (count > 0) WinterheartSurvival.LOGGER.info("All players are above {} sanity: despawned {} stalkers", THRESHOLD, count);
        return count;
    }
}
