package io.github.soldierdonkey2.winterheart.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Legendary Survival Overhaul body temperature, 0-40 (realistic degrees), from the `legendarysurvivaloverhaul:temperature` capability. */
public final class Temperature {
    private Temperature() {}

    private static CompoundTag cap(ServerPlayer player) {
        return player.saveWithoutId(new CompoundTag()).getCompound("ForgeCaps").getCompound("legendarysurvivaloverhaul:temperature");
    }

    public static double get(ServerPlayer player) {
        return cap(player).getDouble("temperature");
    }

    /** The temperature the player is heading towards. */
    public static double getAmbient(ServerPlayer player) {
        return cap(player).getDouble("targettemperature");
    }

    public static void set(ServerPlayer player, double temperature) {
        long value = Math.max(0, Math.min(40, Math.round(temperature)));
        Cmd.run(player.server, "execute as " + player.getGameProfile().getName() + " run temperature set " + value);
    }

    public static void add(ServerPlayer player, double delta) {
        set(player, get(player) + delta);
    }
}
