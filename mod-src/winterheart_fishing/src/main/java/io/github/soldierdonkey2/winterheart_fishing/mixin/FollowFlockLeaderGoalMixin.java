package io.github.soldierdonkey2.winterheart_fishing.mixin;

import net.minecraft.world.entity.ai.goal.FollowFlockLeaderGoal;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingUtil;

@Mixin(FollowFlockLeaderGoal.class)
public abstract class FollowFlockLeaderGoalMixin {
    @Shadow @Final private AbstractSchoolingFish mob;

    @Inject(method = {"canUse", "canContinueToUse"}, at = @At("HEAD"), cancellable = true)
    private void winterheart$noSchoolingWhileLured(CallbackInfoReturnable<Boolean> cir) {
        if (FishingUtil.isLured(mob)) cir.setReturnValue(false);
    }
}
