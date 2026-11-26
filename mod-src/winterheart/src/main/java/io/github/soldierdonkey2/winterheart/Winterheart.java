package io.github.soldierdonkey2.winterheart;

import com.mojang.logging.LogUtils;
import io.github.soldierdonkey2.winterheart.api.DumpIds;
import io.github.soldierdonkey2.winterheart.api.Scheduler;
import io.github.soldierdonkey2.winterheart.api.TestItems;
import io.github.soldierdonkey2.winterheart.api.TestMode;
import io.github.soldierdonkey2.winterheart.api.Ticks;
import io.github.soldierdonkey2.winterheart.brewing.BrewingChanges;
import io.github.soldierdonkey2.winterheart.compat.IdRemap;
import io.github.soldierdonkey2.winterheart.effect.ModEffects;
import io.github.soldierdonkey2.winterheart.item.ModItems;
import io.github.soldierdonkey2.winterheart.item.SmeltPopOut;
import io.github.soldierdonkey2.winterheart.recipe.ModRecipes;
import io.github.soldierdonkey2.winterheart.remote.DevRcon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Winterheart core: items, the limb poising effect, recipe types and the brewing changes the other
 * Winterheart mods and the Paxi datapacks build on.
 */
@Mod(Winterheart.MOD_ID)
public class Winterheart {
    public static final String MOD_ID = "winterheart";
    public static final Logger LOGGER = LogUtils.getLogger();
    static final String SERVER_MESSAGE =
            "Winterheart is a singleplayer-only modpack and can't run on a dedicated server. "
                    + "Play it in singleplayer from the Minecraft client.";

    public Winterheart() {
        // The loop, the endings and permadeath are built for one player (docs/plot/plot.md)
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            LOGGER.error(SERVER_MESSAGE);
            throw new IllegalStateException(SERVER_MESSAGE);
        }
        // KubeJS used to switch on Forge's milk fluid; worlds and mods still reference minecraft:milk
        net.minecraftforge.common.ForgeMod.enableMilkFluid();
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        BrewingChanges.register(modBus);

        MinecraftForge.EVENT_BUS.register(IdRemap.class);
        MinecraftForge.EVENT_BUS.register(SmeltPopOut.class);
        MinecraftForge.EVENT_BUS.register(Scheduler.class);
        MinecraftForge.EVENT_BUS.register(Ticks.class);
        MinecraftForge.EVENT_BUS.register(DumpIds.class);
        MinecraftForge.EVENT_BUS.register(TestMode.class);
        MinecraftForge.EVENT_BUS.register(TestItems.class);
        // run-pack's single-player command listener; off unless run-pack set WINTERHEART_RCON
        MinecraftForge.EVENT_BUS.addListener(DevRcon::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(DevRcon::onServerStopping);
        LOGGER.info("{} loaded", MOD_ID);
    }
}
