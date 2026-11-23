package io.github.soldierdonkey2.winterheart;

import com.mojang.logging.LogUtils;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Winterheart core: items, the limb poising effect, recipe types and the brewing changes the other
 * Winterheart mods and the Paxi datapacks build on.
 */
@Mod(Winterheart.MOD_ID)
public class Winterheart {
    public static final String MOD_ID = "winterheart";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Winterheart() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModEffects.EFFECTS.register(modBus);
        ModRecipes.SERIALIZERS.register(modBus);
        BrewingChanges.register(modBus);

        MinecraftForge.EVENT_BUS.register(IdRemap.class);
        MinecraftForge.EVENT_BUS.register(SmeltPopOut.class);
        MinecraftForge.EVENT_BUS.register(Scheduler.class);
        MinecraftForge.EVENT_BUS.register(Ticks.class);
        MinecraftForge.EVENT_BUS.register(TestMode.class);
        MinecraftForge.EVENT_BUS.register(TestItems.class);
        LOGGER.info("{} loaded", MOD_ID);
    }
}
