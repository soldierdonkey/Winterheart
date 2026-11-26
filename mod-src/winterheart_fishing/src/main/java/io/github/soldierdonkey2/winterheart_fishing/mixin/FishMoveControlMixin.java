package io.github.soldierdonkey2.winterheart_fishing.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.AbstractFish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingUtil;

/** Pushes a lured fish horizontally toward where it is heading, so it reaches the bobber in time. */
@Mixin(targets = "net.minecraft.world.entity.animal.AbstractFish$FishMoveControl")
public abstract class FishMoveControlMixin extends MoveControl {
    private FishMoveControlMixin(Mob mob) {
        super(mob);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void winterheart$hurry(CallbackInfo ci) {
        if (!(mob instanceof AbstractFish fish) || !FishingUtil.isLured(fish)) return;
        if (operation != MoveControl.Operation.MOVE_TO || fish.getNavigation().isDone()) return;
        double dx = wantedX - fish.getX();
        double dy = wantedY - fish.getY();
        double dz = wantedZ - fish.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) return;
        double push = fish.getSpeed() * 0.01 / length;
        fish.setDeltaMovement(fish.getDeltaMovement().add(dx * push, 0, dz * push));
    }
}
