package io.github.soldierdonkey2.winterheart.world;

import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;

/**
 * World-wide key/value state for the Winterheart mods (day counter, unlocked guides, placed structures, ...),
 * saved with the world as `data/winterheart_state.dat`. It replaces KubeJS's overworld `persistentData`, and keeps
 * the same key names: the first time a world loads, everything in KubeJS's `kubejs_persistent_data.nbt`
 * (`minecraft:overworld` compound) is copied in, so progress from before the switch carries over.
 *
 * Use {@link #tag(MinecraftServer)} to read and write, and {@link #save(MinecraftServer)} after a write.
 */
public final class WorldState extends SavedData {
    private static final String NAME = "winterheart_state";
    private static final String KUBEJS_FILE = "kubejs_persistent_data.nbt";

    private final CompoundTag data;

    private WorldState(CompoundTag data) {
        this.data = data;
    }

    private static WorldState load(CompoundTag tag) {
        return new WorldState(tag.getCompound("data"));
    }

    private static WorldState create(MinecraftServer server) {
        WorldState state = new WorldState(new CompoundTag());
        File file = server.getWorldPath(LevelResource.ROOT).resolve(KUBEJS_FILE).toFile();
        if (file.isFile()) {
            try {
                CompoundTag root = NbtIo.readCompressed(file);
                state.data.merge(root.getCompound("minecraft:overworld"));
                Winterheart.LOGGER.info("Imported {} world state keys from {}", state.data.size(), KUBEJS_FILE);
            } catch (IOException e) {
                Winterheart.LOGGER.error("Could not import {}; starting with empty world state", KUBEJS_FILE, e);
            }
        }
        state.setDirty();
        return state;
    }

    private static WorldState of(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldState::load, () -> create(server), NAME);
    }

    /** The world's state compound. Modify it, then call {@link #save(MinecraftServer)}. */
    public static CompoundTag tag(MinecraftServer server) {
        return of(server).data;
    }

    public static void save(MinecraftServer server) {
        of(server).setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("data", data);
        return tag;
    }
}
