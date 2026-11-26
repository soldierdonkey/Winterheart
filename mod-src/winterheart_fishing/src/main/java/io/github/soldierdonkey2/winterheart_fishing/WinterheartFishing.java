package io.github.soldierdonkey2.winterheart_fishing;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import io.github.soldierdonkey2.winterheart_fishing.cast.CastCharging;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishEvents;

/**
 * Winterheart: Fishing: rods catch the fish actually swimming in the water. A Forge 1.20.1 port of Simple Fishing Overhaul
 * by pajic (MIT, see META-INF/LICENSE-simple-fishing-overhaul).
 */
@Mod(WinterheartFishing.MOD_ID)
public class WinterheartFishing {
    public static final String MOD_ID = "winterheart_fishing";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartFishing() {
        MinecraftForge.EVENT_BUS.register(FishEvents.class);
        MinecraftForge.EVENT_BUS.register(CastCharging.class);
        LOGGER.info("{} loaded", MOD_ID);
    }
}
