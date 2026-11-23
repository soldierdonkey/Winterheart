package io.github.soldierdonkey2.winterheart_guides;

import com.mojang.logging.LogUtils;
import io.github.soldierdonkey2.winterheart_guides.client.GuideClient;
import io.github.soldierdonkey2.winterheart_guides.net.GuideNet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Trigger-based guide books and lost journal pages. Data: config/paxi/datapacks/winterheart_guides (edited with tools/guide_editor). */
@Mod(WinterheartGuides.MOD_ID)
public class WinterheartGuides {
    public static final String MOD_ID = "winterheart_guides";
    public static final Logger LOGGER = LogUtils.getLogger();

    public WinterheartGuides() {
        GuideNet.register();
        MinecraftForge.EVENT_BUS.register(EventHooks.class);
        MinecraftForge.EVENT_BUS.addListener(GuideCommands::register);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> GuideClient::init);
        LOGGER.info("{} loaded", MOD_ID);
    }
}
