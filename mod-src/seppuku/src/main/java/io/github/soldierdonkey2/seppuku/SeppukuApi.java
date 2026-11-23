package io.github.soldierdonkey2.seppuku;

import io.github.soldierdonkey2.seppuku.network.AnimationPacket;
import io.github.soldierdonkey2.seppuku.network.SeppukuNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side entry points. The /seppuku command goes through here, and scripts
 * (e.g. KubeJS via Java.loadClass) can call it directly.
 */
public final class SeppukuApi {
    private SeppukuApi() {}

    /** Plays the bundled seppuku animation on the player, for them and everyone tracking them. */
    public static void play(ServerPlayer player) {
        play(player, Seppuku.DEFAULT_ANIMATION);
    }

    /** Plays any PlayerAnimator animation by id (namespace:name, where name is the animation's "name" field). */
    public static void play(ServerPlayer player, ResourceLocation animation) {
        send(player, new AnimationPacket(player.getId(), animation));
    }

    /** Fades the player back out of whatever this mod is playing on them. */
    public static void stop(ServerPlayer player) {
        send(player, new AnimationPacket(player.getId(), null));
    }

    private static void send(ServerPlayer player, AnimationPacket packet) {
        SeppukuNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), packet);
    }
}
