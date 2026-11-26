package io.github.soldierdonkey2.winterheart_fishing.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AbstractFish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishAccess;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishState;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingUtil;

@Mixin(AbstractFish.class)
public abstract class AbstractFishMixin implements FishAccess {
    @Unique private final FishState winterheart$state = new FishState();

    @Override
    public FishState winterheart$state() {
        return winterheart$state;
    }

    /** travel() makes a fish without a target sink slowly; a lured fish counts as having one so it can rise to the bobber. */
    @Redirect(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/AbstractFish;getTarget()Lnet/minecraft/world/entity/LivingEntity;"))
    private LivingEntity winterheart$noSinking(AbstractFish fish) {
        return FishingUtil.isLured(fish) ? fish : fish.getTarget();
    }
}
