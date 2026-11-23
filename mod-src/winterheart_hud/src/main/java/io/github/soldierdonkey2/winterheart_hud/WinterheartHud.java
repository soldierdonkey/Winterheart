package io.github.soldierdonkey2.winterheart_hud;

import com.mojang.logging.LogUtils;
import io.github.soldierdonkey2.winterheart_hud.client.HudClient;
import io.github.soldierdonkey2.winterheart_hud.net.HudNet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** The Winterheart survival HUD (top right): a snowy spruce panel with day, time, weather and temperature. */
@Mod(WinterheartHud.MOD_ID)
public class WinterheartHud {
    public static final String MOD_ID = "winterheart_hud";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartHud() {
        HudNet.register();
        MinecraftForge.EVENT_BUS.register(HudServer.class);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> HudClient::init);
        LOGGER.info("{} loaded", MOD_ID);
    }
}
