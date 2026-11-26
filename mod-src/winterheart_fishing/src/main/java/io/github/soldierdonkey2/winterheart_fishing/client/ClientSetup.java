package io.github.soldierdonkey2.winterheart_fishing.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.WinterheartFishing;
import io.github.soldierdonkey2.winterheart_fishing.cast.CastCharging;

/** The casting bar, drawn where the experience bar is. */
@Mod.EventBusSubscriber(modid = WinterheartFishing.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private static final ResourceLocation BACKGROUND = texture("cast_bar_background");
    private static final ResourceLocation PROGRESS = texture("cast_bar_progress");

    private ClientSetup() {}

    private static ResourceLocation texture(String name) {
        return new ResourceLocation(WinterheartFishing.MOD_ID, "textures/gui/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.EXPERIENCE_BAR.id(), "cast_bar", (gui, graphics, partialTick, width, height) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (!Config.get().castBar || minecraft.options.hideGui || minecraft.player == null
                    || !CastCharging.isCharging(minecraft.player)) return;
            int left = width / 2 - 91;
            int top = height - 29;
            graphics.blit(BACKGROUND, left, top, 0, 0, 182, 5, 182, 5);
            int progress = (int) (CastCharging.charge(minecraft.player, partialTick) * 182);
            if (progress > 0) graphics.blit(PROGRESS, left, top, 0, 0, progress, 5, 182, 5);
        });
    }
}
