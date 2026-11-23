package io.github.soldierdonkey2.winterheart_time;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** The Winterheart custom day/night cycle (days, sleeping, eternal night, the death loop). Tuning: data/winterheart/time/config.json. */
@Mod(WinterheartTime.MOD_ID)
public class WinterheartTime {
    public static final String MOD_ID = "winterheart_time";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartTime() {
        MinecraftForge.EVENT_BUS.register(TimeSystem.class);
        MinecraftForge.EVENT_BUS.register(TimeCommands.class);
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(new TimeConfig.Loader()));
        LOGGER.info("{} loaded", MOD_ID);
    }
}
