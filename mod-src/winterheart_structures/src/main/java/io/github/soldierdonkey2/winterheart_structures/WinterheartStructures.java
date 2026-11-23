package io.github.soldierdonkey2.winterheart_structures;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Places the Winterheart hand-built structures (watchtower, shipwreck, beacon) once per world at fixed spots. */
@Mod(WinterheartStructures.MOD_ID)
public class WinterheartStructures {
    public static final String MOD_ID = "winterheart_structures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartStructures() {
        MinecraftForge.EVENT_BUS.register(FixedStructures.class);
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new StructureConfig.Loader()));
        LOGGER.info("{} loaded", MOD_ID);
    }
}
