package io.github.soldierdonkey2.winterheart_fishing.fishing.goal;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishAccess;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishState;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingUtil;

/** Keeps a biting fish thrashing just under the bobber. */
public class GetCaughtGoal extends Goal {
    private final AbstractFish fish;

    public GetCaughtGoal(AbstractFish fish) {
        this.fish = fish;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        FishState state = FishAccess.state(fish);
        return state.caught && FishingUtil.isActive(state.hook);
    }

    @Override
    public void start() {
        fish.getNavigation().stop();
    }

    @Override
    public void tick() {
        FishingHook hook = FishAccess.state(fish).hook;
        if (!FishingUtil.isActive(hook)) return;
        Vec3 pin = new Vec3(hook.getX(), hook.getY() - fish.getBbHeight() - 0.05, hook.getZ());
        fish.setDeltaMovement(pin.subtract(fish.position()).scale(0.3));
        float yRot = fish.getYRot() + (fish.getRandom().nextFloat() - 0.5F) * 60.0F;
        fish.setYRot(yRot);
        fish.yBodyRot = yRot;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }
}
