package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sanity as the scripts see it: 0-100, where 100 is fully sane. The Sanity Dimension mod stores 0-1 the other way
 * round (0 = sane), in the player's `sanitydim:sanity` capability. Reads the capability's NBT; writes with the
 * mod's `/sanity set` command (which takes the same 0-100 scale).
 */
public final class Sanity {
    private Sanity() {}

    public static double get(ServerPlayer player) {
        CompoundTag caps = player.saveWithoutId(new CompoundTag()).getCompound("ForgeCaps");
        return 100.0 * (1.0 - caps.getCompound("sanitydim:sanity").getDouble("sanity.sanity"));
    }

    public static void set(ServerPlayer player, double sanity) {
        double value = Math.max(0, Math.min(100, sanity));
        Cmd.run(player.server, "sanity set " + player.getGameProfile().getName() + " " + value);
    }

    public static void add(ServerPlayer player, double delta) {
        set(player, get(player) + delta);
    }
}
