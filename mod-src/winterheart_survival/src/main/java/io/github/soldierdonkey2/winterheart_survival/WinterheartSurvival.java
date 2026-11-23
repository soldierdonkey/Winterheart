package io.github.soldierdonkey2.winterheart_survival;

import com.mojang.logging.LogUtils;
import io.github.soldierdonkey2.winterheart_survival.sanity.SanityDespawn;
import io.github.soldierdonkey2.winterheart_survival.sanity.SeppukuTrigger;
import io.github.soldierdonkey2.winterheart_survival.world.BeaconBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Winterheart survival mechanics: sanity effects, beacon base (more to come, see docs/todo/general-kubejs-removal.md). */
@Mod(WinterheartSurvival.MOD_ID)
public class WinterheartSurvival {
    public static final String MOD_ID = "winterheart_survival";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartSurvival() {
        MinecraftForge.EVENT_BUS.register(SanityDespawn.class);
        MinecraftForge.EVENT_BUS.register(SeppukuTrigger.class);
        MinecraftForge.EVENT_BUS.register(BeaconBase.class);
        LOGGER.info("{} loaded", MOD_ID);
    }
}
