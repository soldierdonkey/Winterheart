package io.github.soldierdonkey2.winterheart_survival.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Right-clicking a beacon turns the 3x3 of blocks directly beneath it into iron blocks (a tier-1 pyramid base). */
public final class BeaconBase {
    private BeaconBase() {}

    /** Fills the 3x3 below the beacon at `beacon` with iron blocks. */
    public static void buildBase(Level level, BlockPos beacon) {
        BlockPos below = beacon.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(below.offset(dx, 0, dz), Blocks.IRON_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Level level = event.getLevel();
        BlockState state = level.getBlockState(event.getPos());
        if (!state.is(Blocks.BEACON)) return;

        if (!level.isClientSide) buildBase(level, event.getPos());
        // Stop the beacon screen from opening
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
