package io.github.soldierdonkey2.winterheart_spawning;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** The Winterheart scripted mob spawner. Tuning: data/winterheart/spawning/config.json. */
@Mod(WinterheartSpawning.MOD_ID)
public class WinterheartSpawning {
    public static final String MOD_ID = "winterheart_spawning";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartSpawning() {
        MinecraftForge.EVENT_BUS.register(Spawner.class);
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new SpawnConfig.Loader()));
        LOGGER.info("{} loaded", MOD_ID);
    }
}
