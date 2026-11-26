package io.github.soldierdonkey2.winterheart_fishing.cast;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import io.github.soldierdonkey2.winterheart_fishing.Config;

/** Hold right-click to charge a cast: releasing (or a full charge) throws the bobber, farther the longer it was held. */
public final class CastCharging {
    public static final int CHARGE_TICKS = 60;

    private CastCharging() {}

    public static boolean isCharging(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof FishingRodItem;
    }

    /** 0 to 1 while charging. */
    public static float charge(Player player, float partialTick) {
        return Mth.clamp((CHARGE_TICKS + 1 - player.getUseItemRemainingTicks() + partialTick) / CHARGE_TICKS, 0, 1);
    }

    public static Vec3 scale(Vec3 velocity, Player player) {
        Config.Values config = Config.get();
        if (!config.castCharging || !isCharging(player)) return velocity;
        return velocity.scale(Mth.lerp(charge(player, 0), config.minCastDistanceMultiplier, config.maxCastDistanceMultiplier));
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!Config.get().castCharging || !(event.getItemStack().getItem() instanceof FishingRodItem)) return;
        if (player.fishing != null && !player.fishing.isRemoved()) return;     // reeling in stays instant
        player.startUsingItem(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
    }

    @SubscribeEvent
    public static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (Config.get().castCharging && event.getEntity() instanceof Player
                && event.getItem().getItem() instanceof FishingRodItem) {
            event.setDuration(CHARGE_TICKS + 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && isCharging(event.player) && event.player.getUseItemRemainingTicks() <= 1) {
            event.player.releaseUsingItem();
        }
    }

    @SubscribeEvent
    public static void onRelease(LivingEntityUseItemEvent.Stop event) {
        if (!Config.get().castCharging || !(event.getEntity() instanceof Player player)) return;
        ItemStack rod = event.getItem();
        if (!(rod.getItem() instanceof FishingRodItem) || (player.fishing != null && !player.fishing.isRemoved())) return;
        InteractionHand hand = player.getUsedItemHand();
        // Called directly, so it doesn't fire RightClickItem again; the player still counts as charging, which scales the cast
        rod.getItem().use(player.level(), player, hand);
        player.swing(hand, true);
        player.getCooldowns().addCooldown(rod.getItem(), 30);
    }
}
