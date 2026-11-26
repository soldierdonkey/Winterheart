package io.github.soldierdonkey2.winterheart_fishing.fishing;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import io.github.soldierdonkey2.winterheart_fishing.Config;

public final class FishingUtil {
    private FishingUtil() {}

    /** Living, unprotected fish with a loot table, within the radius around the bobber and down to the depth below it. */
    public static List<AbstractFish> eligibleFish(FishingHook hook, double radius, double depth) {
        if (!(hook.level() instanceof ServerLevel level)) return List.of();
        AABB area = new AABB(hook.blockPosition()).inflate(radius, 0, radius).expandTowards(0, -depth, 0);
        return level.getEntitiesOfClass(AbstractFish.class, area, fish -> fish.isAlive() && !isProtected(fish)
                && level.getServer().getLootData().getLootTable(fish.getLootTable()) != LootTable.EMPTY);
    }

    private static boolean isProtected(AbstractFish fish) {
        return fish.hasCustomName() || (Config.get().protectReleasedFish && fish.fromBucket());
    }

    public static boolean isActive(@Nullable FishingHook hook) {
        return hook != null && !hook.isRemoved();
    }

    public static boolean isLured(AbstractFish fish) {
        return isActive(FishAccess.state(fish).hook);
    }

    public static boolean isTargetOf(AbstractFish fish, @Nullable FishingHook hook) {
        return isActive(hook) && HookAccess.session(hook).target() == fish;
    }

    public static Vec3 approachTarget(FishingHook hook) {
        return waterTargetBelow(hook, 0.5);
    }

    /** The highest-reaching water position at most the given depth below the bobber. */
    public static Vec3 waterTargetBelow(FishingHook hook, double depth) {
        Level level = hook.level();
        int hookY = hook.blockPosition().getY();
        BlockPos.MutableBlockPos pos = BlockPos.containing(hook.getX(), hook.getY() - Math.max(depth, 0), hook.getZ()).mutable();
        while (pos.getY() < hookY && !level.getFluidState(pos).is(FluidTags.WATER)) pos.move(Direction.UP);
        return new Vec3(hook.getX(), pos.getY() + 0.5, hook.getZ());
    }
}
