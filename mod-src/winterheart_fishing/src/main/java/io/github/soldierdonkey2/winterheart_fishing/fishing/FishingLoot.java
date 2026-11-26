package io.github.soldierdonkey2.winterheart_fishing.fishing;

import java.util.List;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.AbstractFish;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.WinterheartFishing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

final class FishingLoot {
    private FishingLoot() {}

    /** The fish's entry in catchItems, else what its loot table gives a player who kills it. */
    static List<ItemStack> fish(ServerLevel level, AbstractFish fish, Player player) {
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(fish.getType());
        String id = Config.get().catchItems.get(String.valueOf(type));
        if (id != null) {
            ResourceLocation key = ResourceLocation.tryParse(id);
            Item item = key != null && ForgeRegistries.ITEMS.containsKey(key) ? ForgeRegistries.ITEMS.getValue(key) : null;
            if (item != null) return List.of(new ItemStack(item));
            WinterheartFishing.LOGGER.warn("[Fishing] catchItems: unknown item {} for {}", id, type);
        }
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
