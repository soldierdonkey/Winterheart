package io.github.soldierdonkey2.winterheart_fishing.fishing.goal;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.phys.Vec3;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishAccess;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishState;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingUtil;

/** Swims an interested fish to the bobber: up to it when it is the approaching one, otherwise somewhere below it. */
public class GetLuredGoal extends Goal {
    private final AbstractFish fish;
    private int repathDelay;

    public GetLuredGoal(AbstractFish fish) {
        this.fish = fish;
    }

    @Override
    public boolean canUse() {
        FishState state = FishAccess.state(fish);
        return !state.caught && FishingUtil.isActive(state.hook);
    }

    @Override
    public void start() {
        repathDelay = 0;
    }

    @Override
    public void tick() {
        if (--repathDelay > 0) return;
        repathDelay = adjustedTickDelay(10);
        FishingHook hook = FishAccess.state(fish).hook;
        if (!FishingUtil.isActive(hook)) return;
        if (FishingUtil.isTargetOf(fish, hook)) {
            move(FishingUtil.approachTarget(hook), 1);
        } else if (fish.getNavigation().isDone()) {
            double radius = Config.get().catchRadius;
            move(FishingUtil.waterTargetBelow(hook, radius - 1), fish.distanceToSqr(hook) < radius * radius ? 0.5 : 1);
        }
    }

    private void move(Vec3 target, double speed) {
        fish.getNavigation().moveTo(target.x, target.y, target.z, speed);
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
