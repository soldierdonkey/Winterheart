package io.github.soldierdonkey2.winterheart_butchery.client;

import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/** Registers the carcass category. Does nothing when Butchery is not installed. */
@JeiPlugin
public class ButcheryJei implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(WinterheartButchery.MOD_ID, "jei");
    }

    private static boolean enabled() {
        return ModList.get().isLoaded("butchery");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (enabled()) {
            registration.addRecipeCategories(new CarcassCategory(registration.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (enabled()) {
            List<Carcass> animals = Carcass.load();
            registration.addRecipes(CarcassCategory.TYPE, animals);
            WinterheartButchery.LOGGER.info("[Butchery] JEI: added {} carcass page(s)", animals.size());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (enabled()) {
            for (ItemStack tool : Tools.cleavers()) {
                registration.addRecipeCatalyst(tool, CarcassCategory.TYPE);
            }
            for (ItemStack tool : Tools.skinningKnives()) {
                registration.addRecipeCatalyst(tool, CarcassCategory.TYPE);
            }
        }
    }
}
