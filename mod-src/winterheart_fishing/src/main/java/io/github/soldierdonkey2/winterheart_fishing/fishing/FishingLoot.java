package io.github.soldierdonkey2.winterheart_fishing.fishing;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

final class FishingLoot {
    private FishingLoot() {}

    /** What the fish would drop if the player killed it. */
    static List<ItemStack> fish(ServerLevel level, AbstractFish fish, Player player) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, fish)
                .withParameter(LootContextParams.ORIGIN, fish.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, fish.damageSources().playerAttack(player))
                .withParameter(LootContextParams.KILLER_ENTITY, player)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .create(LootContextParamSets.ENTITY);
        return level.getServer().getLootData().getLootTable(fish.getLootTable()).getRandomItems(params);
    }

    /** Vanilla's junk and treasure weights without the fish entry, for water with no fish. */
    static List<ItemStack> junkOrTreasure(ServerLevel level, FishingHook hook, Player player) {
        float luck = player.getLuck();
        int junk = weight(90, -2, luck);
        int treasure = hook.isOpenWaterFishing() ? weight(10, 2, luck) : 0;
        if (junk + treasure <= 0) return List.of();
        ResourceLocation table = hook.level().getRandom().nextInt(junk + treasure) < junk
                ? BuiltInLootTables.FISHING_JUNK : BuiltInLootTables.FISHING_TREASURE;
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withParameter(LootContextParams.KILLER_ENTITY, player)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);
        return level.getServer().getLootData().getLootTable(table).getRandomItems(params);
    }

    private static int weight(int weight, int quality, float luck) {
        return Math.max(Mth.floor(weight + quality * luck), 0);
    }
}
