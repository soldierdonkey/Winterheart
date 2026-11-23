package io.github.soldierdonkey2.winterheart.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A shapeless recipe plus a "remainders" object mapping an ingredient item to the item left in its slot:
 *
 * <pre>
 * { "type": "winterheart:shapeless_with_remainders", "ingredients": [...], "result": {...},
 *   "remainders": { "farmersdelight:rich_soil": "minecraft:dirt" } }
 * </pre>
 *
 * A remainder only replaces the item's own crafting remainder when that is empty.
 */
public class RemainderShapelessRecipe extends ShapelessRecipe {
    private final Map<ResourceLocation, ResourceLocation> remainders;

    public RemainderShapelessRecipe(ShapelessRecipe base, Map<ResourceLocation, ResourceLocation> remainders) {
        super(base.getId(), base.getGroup(), base.category(), base.getResultItem(RegistryAccess.EMPTY), base.getIngredients());
        this.remainders = remainders;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SHAPELESS_WITH_REMAINDERS.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> left = super.getRemainingItems(container);
        for (int slot = 0; slot < left.size(); slot++) {
            ItemStack in = container.getItem(slot);
            if (in.isEmpty() || !left.get(slot).isEmpty()) continue;
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(in.getItem());
            ResourceLocation target = key == null ? null : remainders.get(key);
            if (target == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(target);
            if (item != null) left.set(slot, new ItemStack(item));
        }
        return left;
    }

    public static final class Serializer implements RecipeSerializer<RemainderShapelessRecipe> {
        @Override
        public RemainderShapelessRecipe fromJson(ResourceLocation id, JsonObject json) {
            ShapelessRecipe base = SHAPELESS_RECIPE.fromJson(id, json);
            Map<ResourceLocation, ResourceLocation> remainders = new LinkedHashMap<>();
            if (json.has("remainders")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : GsonHelper.getAsJsonObject(json, "remainders").entrySet()) {
                    remainders.put(new ResourceLocation(e.getKey()), new ResourceLocation(GsonHelper.convertToString(e.getValue(), e.getKey())));
                }
            }
            return new RemainderShapelessRecipe(base, remainders);
        }

        @Override
        public RemainderShapelessRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            ShapelessRecipe base = SHAPELESS_RECIPE.fromNetwork(id, buf);
            Map<ResourceLocation, ResourceLocation> remainders = new LinkedHashMap<>();
            for (int i = buf.readVarInt(); i > 0; i--) {
                remainders.put(buf.readResourceLocation(), buf.readResourceLocation());
            }
            return new RemainderShapelessRecipe(base, remainders);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, RemainderShapelessRecipe recipe) {
            SHAPELESS_RECIPE.toNetwork(buf, recipe);
            buf.writeVarInt(recipe.remainders.size());
            recipe.remainders.forEach((from, to) -> {
                buf.writeResourceLocation(from);
                buf.writeResourceLocation(to);
            });
        }
    }
}
