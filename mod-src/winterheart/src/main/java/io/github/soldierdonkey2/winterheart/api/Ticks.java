package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** A counter of overworld ticks since the game started (not saved). What the scripts called global.currentTick. */
public final class Ticks {
    private static long current = 0;

    private Ticks() {}

    public static long current() { return current; }

    public static boolean everySecond() { return current % 20 == 0; }

    public static boolean everyTenSeconds() { return current % 200 == 0; }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.START && !event.level.isClientSide && event.level.dimension() == Level.OVERWORLD) current++;
    }
}
