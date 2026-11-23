package io.github.soldierdonkey2.seppuku.client;

import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import io.github.soldierdonkey2.seppuku.Seppuku;
import io.github.soldierdonkey2.seppuku.network.AnimationPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public final class SeppukuClient {
    /** Our animation layer on every player. High priority so it wins over other PlayerAnimator users. */
    private static final ResourceLocation LAYER = new ResourceLocation(Seppuku.MOD_ID, "animation");
    private static final int LAYER_PRIORITY = 1000;
    private static final int FADE_TICKS = 6;

    private SeppukuClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(SeppukuClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, LAYER_PRIORITY, player -> new ModifierLayer<>());
    }

    public static void handle(AnimationPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.entityId()) instanceof AbstractClientPlayer player)) {
            return;
        }
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer == null) {
            return;
        }
        AbstractFadeModifier fade = AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE);
        if (packet.animation() == null) {
            layer.replaceAnimationWithFade(fade, null);
            return;
        }

        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(packet.animation());
        if (animation == null) {
            Seppuku.LOGGER.warn("Unknown player animation {}", packet.animation());
            return;
        }
        KeyframeAnimationPlayer animationPlayer = new KeyframeAnimationPlayer(animation);
        // First Person Model already renders the animated body in first person. Without it,
        // let PlayerAnimator draw the third-person model from the camera instead.
        if (!ModList.get().isLoaded("firstperson")) {
            animationPlayer.setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                    .setFirstPersonConfiguration(new FirstPersonConfiguration(true, true, true, true));
        }
        layer.replaceAnimationWithFade(fade, animationPlayer);
    }
}
