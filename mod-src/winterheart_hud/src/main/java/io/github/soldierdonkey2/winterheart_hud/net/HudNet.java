package io.github.soldierdonkey2.winterheart_hud.net;

import io.github.soldierdonkey2.winterheart_hud.client.HudClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** server -> client: the panel's title, weather key and lines ("style|text"). */
public final class HudNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("winterheart_hud", "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public record HudPacket(String title, String weather, List<String> lines) {}

    private HudNet() {}

    public static void register() {
        CHANNEL.registerMessage(0, HudPacket.class, HudNet::encode, HudNet::decode, HudNet::handle);
    }

    private static void encode(HudPacket p, FriendlyByteBuf b) {
        b.writeUtf(p.title());
        b.writeUtf(p.weather());
        b.writeVarInt(p.lines().size());
        p.lines().forEach(l -> b.writeUtf(l, 1024));
    }

    private static HudPacket decode(FriendlyByteBuf b) {
        String title = b.readUtf(), weather = b.readUtf();
        int n = b.readVarInt();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < n; i++) lines.add(b.readUtf(1024));
        return new HudPacket(title, weather, lines);
    }

    private static void handle(HudPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HudClient.receive(p)));
        ctx.get().setPacketHandled(true);
    }
}
