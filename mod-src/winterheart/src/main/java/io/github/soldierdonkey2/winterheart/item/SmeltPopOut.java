package io.github.soldierdonkey2.winterheart.item;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Smelting potassium nitrate solution leaves the glass bottle behind: when a player takes saltpeter out of a
 * furnace, the bottles pop out of the furnace (or go to the player's inventory if the furnace can't be found).
 */
public final class SmeltPopOut {
    private SmeltPopOut() {}

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent event) {
        ItemStack smelted = event.getSmelting();
        if (!smelted.is(ModItems.SALTPETER.get())) return;

        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide) return;

        ItemStack bottles = new ItemStack(Items.GLASS_BOTTLE, smelted.getCount());

        // The furnace the player has open is the one they are looking at
        HitResult hit = player.pick(6.0D, 1.0F, false);
        if (hit instanceof BlockHitResult blockHit
                && level.getBlockEntity(blockHit.getBlockPos()) instanceof AbstractFurnaceBlockEntity) {
            Vec3 c = Vec3.atCenterOf(blockHit.getBlockPos());
            ItemEntity entity = new ItemEntity(level, c.x, c.y, c.z, bottles,
                    (level.random.nextDouble() - 0.5) * 0.1, 0.25, (level.random.nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(entity);
        } else {
            player.getInventory().placeItemBackInInventory(bottles);
        }
    }
}
