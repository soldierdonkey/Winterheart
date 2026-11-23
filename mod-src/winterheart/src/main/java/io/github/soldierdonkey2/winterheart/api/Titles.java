package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;

public final class Titles {
    private Titles() {}

    /**
     * A dark red action bar message (10 ticks fade in, 60 stay, 20 out). `msg` may contain § formatting codes,
     * which override the colour.
     */
    public static void show(ServerPlayer player, String msg, boolean bold, boolean italic) {
        if (player == null) return;
        MutableComponent text = Component.literal(msg).withStyle(ChatFormatting.DARK_RED);
        text = text.withStyle(s -> s.withBold(bold).withItalic(italic));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetActionBarTextPacket(text));
    }

    public static void show(ServerPlayer player, String msg) {
        show(player, msg, false, false);
    }
}
