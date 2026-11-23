package io.github.soldierdonkey2.seppuku.network;

import io.github.soldierdonkey2.seppuku.Seppuku;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SeppukuNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Seppuku.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private SeppukuNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(AnimationPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnimationPacket::encode)
                .decoder(AnimationPacket::decode)
                .consumerMainThread(AnimationPacket::handle)
                .add();
    }
}
