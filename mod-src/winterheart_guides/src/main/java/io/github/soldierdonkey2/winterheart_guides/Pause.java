package io.github.soldierdonkey2.winterheart_guides;

import io.github.soldierdonkey2.winterheart.api.Cmd;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;

/**
 * Pauses the server with Multiplayer Server Pause's /pause while a popup book is open. /pause only TOGGLES and
 * fails if every client is already on a pause screen (a book screen is one), so it is issued in the same tick the
 * books are opened. The mod's `forcePause` flag is read so a pause an op made by hand is never undone.
 */
public final class Pause {
    public static boolean ownsPause = false;

    private Pause() {}

    /** The mod's forcePause flag, or null if the mod or its field can't be reached. */
    public static Boolean readForcePause() {
        try {
            Class<?> cls = Class.forName("me.ichun.mods.serverpause.common.ServerPause");
            Object handler = cls.getField("eventHandlerServer").get(null);
            Field f = handler.getClass().getField("forcePause");
            return f.getBoolean(handler);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static void pause(MinecraftServer server) {
        if (!GuideManager.PAUSE_ON_UNLOCK || ownsPause) return;
        if (Boolean.TRUE.equals(readForcePause())) return;      // an op paused manually; leave it alone
        Cmd.run(server, "pause");
        ownsPause = !Boolean.FALSE.equals(readForcePause());
    }

    static void resume(MinecraftServer server) {
        if (!ownsPause) return;
        ownsPause = false;
        if (!Boolean.FALSE.equals(readForcePause())) Cmd.run(server, "pause");
    }
}
