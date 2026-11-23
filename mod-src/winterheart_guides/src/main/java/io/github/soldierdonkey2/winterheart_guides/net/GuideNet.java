package io.github.soldierdonkey2.winterheart_guides.net;

import io.github.soldierdonkey2.winterheart_guides.GuideManager;
import io.github.soldierdonkey2.winterheart_guides.client.GuideClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * server -> client: OpenPacket, start watching for the popup book to close.
 * client -> server: ClosedPacket, the book was closed.
 */
public final class GuideNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("winterheart_guides", "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static final class OpenPacket {}
    public static final class ClosedPacket {}

    private GuideNet() {}

    public static void register() {
        CHANNEL.registerMessage(0, OpenPacket.class, (m, b) -> {}, b -> new OpenPacket(), GuideNet::handleOpen);
        CHANNEL.registerMessage(1, ClosedPacket.class, (m, b) -> {}, b -> new ClosedPacket(), GuideNet::handleClosed);
    }

    private static void handleOpen(OpenPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> GuideClient::startWatching));
        ctx.get().setPacketHandled(true);
    }

    private static void handleClosed(ClosedPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> GuideManager.onBookClosed(ctx.get().getSender()));
        ctx.get().setPacketHandled(true);
    }
}
