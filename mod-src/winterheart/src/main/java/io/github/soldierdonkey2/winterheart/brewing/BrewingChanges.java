package io.github.soldierdonkey2.winterheart.brewing;

import io.github.soldierdonkey2.winterheart.Winterheart;
import io.github.soldierdonkey2.winterheart.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Switches off every brewing recipe (vanilla potions, splash and lingering, and the ones other mods add) and adds
 * the one recipe this pack uses: crude nitrate solution + charred planks -> potassium nitrate solution.
 *
 * Forge has no API to remove brewing recipes, so this empties BrewingRecipeRegistry's private list by reflection.
 * It runs on FMLLoadCompleteEvent so that recipes other mods add during setup are removed too.
 */
public final class BrewingChanges {
    private static final ResourceLocation CHARRED_PLANKS = new ResourceLocation("immersive_weathering", "charred_planks");

    private BrewingChanges() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(BrewingChanges::onLoadComplete);
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(BrewingChanges::apply);
    }

    @SuppressWarnings("unchecked")
    private static void apply() {
        try {
            Field field = BrewingRecipeRegistry.class.getDeclaredField("recipes");
            field.setAccessible(true);
            List<IBrewingRecipe> recipes = (List<IBrewingRecipe>) field.get(null);
            int removed = recipes.size();
            recipes.clear();
            Winterheart.LOGGER.info("Removed {} brewing recipes", removed);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Winterheart.LOGGER.error("Could not clear the brewing recipes; potions keep working as in vanilla", e);
            return;
        }

        Item planks = ForgeRegistries.ITEMS.getValue(CHARRED_PLANKS);
        if (planks == null || planks == Items.AIR) {
            Winterheart.LOGGER.warn("{} not found; crude nitrate solution can't be brewed", CHARRED_PLANKS);
            return;
        }
        BrewingRecipeRegistry.addRecipe(new BrewingRecipe(
                Ingredient.of(ModItems.CRUDE_NITRATE_BOTTLE.get()),
                Ingredient.of(planks),
                new ItemStack(ModItems.POTASSIUM_NITRATE_BOTTLE.get())));
    }
}
