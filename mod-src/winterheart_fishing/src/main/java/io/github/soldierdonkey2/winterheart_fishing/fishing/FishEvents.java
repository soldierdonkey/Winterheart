package io.github.soldierdonkey2.winterheart_fishing.fishing;

import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.fishing.goal.GetCaughtGoal;
import io.github.soldierdonkey2.winterheart_fishing.fishing.goal.GetLuredGoal;

public final class FishEvents {
    private FishEvents() {}

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new Config.Loader());
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractFish fish)) return;
        // A fish that rejoins a level is the same object, so don't add the goals twice
        if (fish.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof GetLuredGoal)) return;
        fish.goalSelector.addGoal(-1, new GetCaughtGoal(fish));
        fish.goalSelector.addGoal(3, new GetLuredGoal(fish));
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (Config.get().fishDropNoLoot && event.getEntity() instanceof AbstractFish) event.setCanceled(true);
    }
}
