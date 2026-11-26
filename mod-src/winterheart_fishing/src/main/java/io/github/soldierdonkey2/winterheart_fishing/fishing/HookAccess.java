package io.github.soldierdonkey2.winterheart_fishing.fishing;

import net.minecraft.world.entity.projectile.FishingHook;

public interface HookAccess {
    FishingSession winterheart$session();

    static FishingSession session(FishingHook hook) {
        return ((HookAccess) hook).winterheart$session();
    }
}
