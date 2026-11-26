package io.github.soldierdonkey2.winterheart_fishing.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.cast.CastCharging;

/** First person: the rod tips back while a cast charges. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void winterheart$castAnimation(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                           float swing, ItemStack stack, float equip, PoseStack pose, MultiBufferSource buffers,
                                           int light, CallbackInfo ci) {
        if (!Config.get().firstPersonAnimation || !CastCharging.isCharging(player) || player.getUsedItemHand() != hand) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * -0.2785682F, 0.18344387F, 0.15731531F);
        pose.mulPose(Axis.XP.rotationDegrees(-13.935F));
        pose.mulPose(Axis.ZP.rotationDegrees(side * -9.785F));
        float power = CastCharging.charge(player, partialTick) * CastCharging.CHARGE_TICKS / 20F;
        pose.mulPose(Axis.XP.rotationDegrees(power * 20F));
        pose.translate(0, 0, power * 0.04F);
    }
}
