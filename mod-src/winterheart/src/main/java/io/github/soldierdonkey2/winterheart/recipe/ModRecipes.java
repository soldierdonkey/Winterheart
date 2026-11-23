package io.github.soldierdonkey2.winterheart.recipe;

import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Winterheart.MOD_ID);

    /** winterheart:shapeless_with_remainders: a shapeless recipe where chosen ingredients leave a given item behind. */
    public static final RegistryObject<RecipeSerializer<RemainderShapelessRecipe>> SHAPELESS_WITH_REMAINDERS =
            SERIALIZERS.register("shapeless_with_remainders", RemainderShapelessRecipe.Serializer::new);

    private ModRecipes() {}
}
