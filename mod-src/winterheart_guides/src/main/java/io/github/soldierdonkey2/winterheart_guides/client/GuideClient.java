package io.github.soldierdonkey2.winterheart_guides.client;

import io.github.soldierdonkey2.winterheart_guides.net.GuideNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Tells the server when the popup book has been closed, so it can undo /pause. After the server's open packet we
 * wait for the book screen to appear and disappear, then answer. Gives up after 5 s if the book never shows up,
 * so the server can't stay paused.
 */
public final class GuideClient {
    private static final int WATCH_TIMEOUT_TICKS = 100;
    private static boolean watching, sawBook;
    private static int waited;

    private GuideClient() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.addListener(GuideClient::onClientTick);
    }

    public static void startWatching() {
        io.github.soldierdonkey2.winterheart_guides.WinterheartGuides.LOGGER.info("[Guides] client: popup opened, watching for the book to close");
        watching = true;
        sawBook = false;
        waited = 0;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !watching) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof BookViewScreen) { sawBook = true; return; }
        waited++;
        if (sawBook || waited > WATCH_TIMEOUT_TICKS) {
            watching = false;
            io.github.soldierdonkey2.winterheart_guides.WinterheartGuides.LOGGER.info("[Guides] client: book closed (saw it: {}, waited {} ticks), telling the server", sawBook, waited);
            GuideNet.CHANNEL.sendToServer(new GuideNet.ClosedPacket());
        }
    }
}
