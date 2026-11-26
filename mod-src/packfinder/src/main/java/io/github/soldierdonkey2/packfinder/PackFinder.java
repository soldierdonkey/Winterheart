package io.github.soldierdonkey2.packfinder;

import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * PackFinder: force-loads every pack in the instance's resourcepacks/ and datapacks/ folders, in the order given by
 * config/packfinder/.
 */
@Mod(PackFinder.MOD_ID)
public class PackFinder {
    public static final String MOD_ID = "packfinder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public PackFinder() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PackFinder::addPackFinders);
    }

    private static void addPackFinders(AddPackFindersEvent event) {
        PackType type = event.getPackType();
        boolean client = type == PackType.CLIENT_RESOURCES;
        Path folder = FMLPaths.GAMEDIR.get().resolve(client ? "resourcepacks" : "datapacks");
        Path order = FMLPaths.CONFIGDIR.get().resolve(MOD_ID)
                .resolve(client ? "resourcepack_order.json" : "datapack_order.json");
        // Resource packs take vanilla's own "file/" id, so ours replaces vanilla's optional entry instead of listing the
        // pack twice. Datapacks use the bare name, which can't collide with a world's "file/" datapacks.
        event.addRepositorySource(new FolderPacks(folder, order, type, client ? "file/" : ""));
    }
}
