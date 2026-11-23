package io.github.soldierdonkey2.winterheart_survival;

import com.mojang.logging.LogUtils;
import io.github.soldierdonkey2.winterheart_survival.limb.LimbPoising;
import io.github.soldierdonkey2.winterheart_survival.proximity.Proximity;
import io.github.soldierdonkey2.winterheart_survival.sanity.SanityDespawn;
import io.github.soldierdonkey2.winterheart_survival.sanity.SeppukuTrigger;
import io.github.soldierdonkey2.winterheart_survival.world.BeaconBase;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Winterheart survival mechanics: sanity effects, beacon base, limb poising and proximity effects. */
@Mod(WinterheartSurvival.MOD_ID)
public class WinterheartSurvival {
    public static final String MOD_ID = "winterheart_survival";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartSurvival() {
        MinecraftForge.EVENT_BUS.register(SanityDespawn.class);
        MinecraftForge.EVENT_BUS.register(SeppukuTrigger.class);
        MinecraftForge.EVENT_BUS.register(BeaconBase.class);
        MinecraftForge.EVENT_BUS.register(LimbPoising.class);
        MinecraftForge.EVENT_BUS.register(Proximity.class);
        LimbPoising.init();
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(LimbPoising.CONFIG.listener());
            e.addListener(Proximity.CONFIG.listener());
        });
        LOGGER.info("{} loaded", MOD_ID);
    }
}
