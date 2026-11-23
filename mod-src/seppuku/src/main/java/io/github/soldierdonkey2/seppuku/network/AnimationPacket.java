package io.github.soldierdonkey2.seppuku.network;

import io.github.soldierdonkey2.seppuku.client.SeppukuClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Tells clients to play {@code animation} on the player with {@code entityId}; a null animation stops it. */
public record AnimationPacket(int entityId, @Nullable ResourceLocation animation) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNullable(animation, FriendlyByteBuf::writeResourceLocation);
    }

    public static AnimationPacket decode(FriendlyByteBuf buf) {
        return new AnimationPacket(buf.readVarInt(), buf.readNullable(FriendlyByteBuf::readResourceLocation));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SeppukuClient.handle(this));
    }
}
