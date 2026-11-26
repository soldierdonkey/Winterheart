package io.github.soldierdonkey2.winterheart_fishing.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FishingHook.class)
public interface FishingHookAccessor {
    @Accessor("lureSpeed")
    int winterheart$getLureSpeed();

    @Accessor("nibble")
    int winterheart$getNibble();

    @Accessor("nibble")
    void winterheart$setNibble(int nibble);

    @Accessor("timeUntilLured")
    void winterheart$setTimeUntilLured(int ticks);

    @Accessor("timeUntilHooked")
    int winterheart$getTimeUntilHooked();

    @Accessor("timeUntilHooked")
    void winterheart$setTimeUntilHooked(int ticks);
}
