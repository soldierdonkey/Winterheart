package io.github.soldierdonkey2.winterheart_fishing.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.cast.CastCharging;

/** Third person: both arms raise the rod while a cast charges. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void winterheart$castPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float headYaw,
                                      float headPitch, CallbackInfo ci) {
        if (!Config.get().thirdPersonAnimation || !(entity instanceof Player player) || !CastCharging.isCharging(player)) return;
        boolean right = (player.getUsedItemHand() == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
        float lift = CastCharging.charge(player, 0);
        rightArm.yRot = -0.1F + head.yRot - (right ? 0 : 0.4F);
        leftArm.yRot = 0.1F + head.yRot + (right ? 0.4F : 0);
        rightArm.xRot = (float) (-Math.PI / 2) + head.xRot - lift;
        leftArm.xRot = (float) (-Math.PI / 2) + head.xRot - lift;
    }
}
