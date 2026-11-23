package io.github.soldierdonkey2.seppuku;

import com.mojang.logging.LogUtils;
import io.github.soldierdonkey2.seppuku.client.SeppukuClient;
import io.github.soldierdonkey2.seppuku.network.SeppukuNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Seppuku.MOD_ID)
public class Seppuku {
    public static final String MOD_ID = "seppuku";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The bundled animation, loaded from assets/seppuku/player_animation/seppuku.json. */
    public static final ResourceLocation DEFAULT_ANIMATION = new ResourceLocation(MOD_ID, "seppuku");

    public Seppuku() {
        SeppukuNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(SeppukuCommand::register);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SeppukuClient.init(FMLJavaModLoadingContext.get().getModEventBus()));
    }
}
