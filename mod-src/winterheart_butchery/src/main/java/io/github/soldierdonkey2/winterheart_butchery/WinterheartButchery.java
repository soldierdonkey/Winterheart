package io.github.soldierdonkey2.winterheart_butchery;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Winterheart: Butchery: shows Butchery's carcass workflows in JEI. Everything is in the client JEI plugin. */
@Mod(WinterheartButchery.MOD_ID)
public class WinterheartButchery {
    public static final String MOD_ID = "winterheart_butchery";
    public static final Logger LOGGER = LogUtils.getLogger();
}
