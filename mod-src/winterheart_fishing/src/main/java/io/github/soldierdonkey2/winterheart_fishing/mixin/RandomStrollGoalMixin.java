package io.github.soldierdonkey2.winterheart_fishing.mixin;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.animal.AbstractFish;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingUtil;

@Mixin(RandomStrollGoal.class)
public abstract class RandomStrollGoalMixin {
    @Shadow @Final protected PathfinderMob mob;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void winterheart$noStrollingWhileLured(CallbackInfoReturnable<Boolean> cir) {
        if (mob instanceof AbstractFish fish && FishingUtil.isLured(fish)) cir.setReturnValue(false);
    }
}
