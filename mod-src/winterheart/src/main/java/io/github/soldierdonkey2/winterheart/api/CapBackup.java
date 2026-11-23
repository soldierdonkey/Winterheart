package io.github.soldierdonkey2.winterheart.api;

import io.github.soldierdonkey2.winterheart.world.WorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Saves a player's mod capability data (their `ForgeCaps.<capId>` compound) into the world state and restores it later,
 * so it survives death. The key is `<prefix><player uuid>_<capId>`. Restoring reloads the player from their NBT with
 * the saved compound merged into that capability.
 */
public final class CapBackup {
    private CapBackup() {}

    private static String key(ServerPlayer player, String capId, String prefix) {
        return prefix + player.getUUID() + "_" + capId;
    }

    public static boolean save(ServerPlayer player, String capId, String prefix) {
        CompoundTag caps = player.saveWithoutId(new CompoundTag()).getCompound("ForgeCaps");
        if (!caps.contains(capId, 10)) return false;
        WorldState.tag(player.server).put(key(player, capId, prefix), caps.getCompound(capId).copy());
        WorldState.save(player.server);
        return true;
    }

    public static boolean load(ServerPlayer player, String capId, String prefix) {
        CompoundTag world = WorldState.tag(player.server);
        String key = key(player, capId, prefix);
        if (!world.contains(key, 10)) return false;
        CompoundTag nbt = player.saveWithoutId(new CompoundTag());
        CompoundTag caps = nbt.getCompound("ForgeCaps");
        CompoundTag cap = caps.getCompound(capId);
        cap.merge(world.getCompound(key));
        caps.put(capId, cap);
        nbt.put("ForgeCaps", caps);
        player.load(nbt);
        return true;
    }
}
