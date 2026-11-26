package io.github.soldierdonkey2.winterheart_fishing.fishing;

import net.minecraft.world.entity.animal.AbstractFish;

public interface FishAccess {
    FishState winterheart$state();

    static FishState state(AbstractFish fish) {
        return ((FishAccess) fish).winterheart$state();
    }
}
