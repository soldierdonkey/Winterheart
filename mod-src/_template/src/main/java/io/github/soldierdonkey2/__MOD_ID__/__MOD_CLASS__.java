package io.github.soldierdonkey2.__MOD_ID__;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * __MOD_NAME__: TODO one line on what this mod does.
 *
 * Usual wiring pattern:
 *   - DeferredRegister<Item> ITEMS, registered on FMLJavaModLoadingContext.get().getModEventBus()
 *   - gameplay handlers as static @SubscribeEvent classes, registered with MinecraftForge.EVENT_BUS.register(Handler.class)
 *   - JSON config via an AddReloadListenerEvent listener, so /reload picks up datapack overrides
 */
@Mod(__MOD_CLASS__.MOD_ID)
public class __MOD_CLASS__ {
    public static final String MOD_ID = "__MOD_ID__";
    public static final Logger LOGGER = LogUtils.getLogger();

    public __MOD_CLASS__() {
        LOGGER.info("{} loaded", MOD_ID);
    }
}
