package io.github.soldierdonkey2.winterheart_fishing.fishing;

import javax.annotation.Nullable;
import net.minecraft.world.entity.projectile.FishingHook;

/** What one fish knows about fishing. Added to AbstractFish by AbstractFishMixin; not saved. */
public final class FishState {
    /** The bobber this fish is interested in. */
    @Nullable public FishingHook hook;
    public boolean caught;
    public long interestExpiry;
    public long boredUntil;
}
