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

/** Registers the carcass, tanning, skull and acid categories. Does nothing when Butchery is not installed. */
@JeiPlugin
public class ButcheryJei implements IModPlugin {
    /** Read when JEI starts; null if Butchery is missing or the data can't be read. */
    private Processes processes;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(WinterheartButchery.MOD_ID, "jei");
    }

    private static boolean enabled() {
        return ModList.get().isLoaded("butchery");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!enabled()) {
            return;
        }
        var helpers = registration.getJeiHelpers();
        registration.addRecipeCategories(new CarcassCategory(helpers));
        processes = Processes.load();
        if (processes != null) {
            registration.addRecipeCategories(new TanningCategory(helpers.getGuiHelper()), new SkullCategory(helpers),
                    new AcidCategory(helpers.getGuiHelper(), processes.acid()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!enabled()) {
            return;
        }
        List<Carcass> animals = Carcass.load();
        registration.addRecipes(CarcassCategory.TYPE, animals);
        WinterheartButchery.LOGGER.info("[Butchery] JEI: added {} carcass page(s)", animals.size());
        if (processes != null) {
            var skulls = processes.skullRecipes();
            var acid = processes.acidRecipes();
            registration.addRecipes(TanningCategory.TYPE, List.of(processes.tanning()));
            registration.addRecipes(SkullCategory.TYPE, skulls);
            registration.addRecipes(AcidCategory.TYPE, acid);
            WinterheartButchery.LOGGER.info("[Butchery] JEI: added tanning, {} skull page(s), {} acid page(s)",
                    skulls.size(), acid.size());
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!enabled()) {
            return;
        }
        for (List<ItemStack> tools : List.of(Tools.cleavers(), Tools.skinningKnives(), Tools.hacksaws())) {
            tools.forEach(tool -> registration.addRecipeCatalyst(tool, CarcassCategory.TYPE));
        }
        for (ItemStack tool : Tools.bossTools()) {
            if (Tools.cleavers().stream().noneMatch(cleaver -> cleaver.is(tool.getItem()))) {
                registration.addRecipeCatalyst(tool, CarcassCategory.TYPE);
            }
        }
        if (processes != null) {
            registration.addRecipeCatalyst(CarcassCategory.item(processes.tanning().rack()), TanningCategory.TYPE);
            Tools.hammers().forEach(tool -> registration.addRecipeCatalyst(tool, SkullCategory.TYPE));
            registration.addRecipeCatalyst(CarcassCategory.item(processes.acid().bottle()), AcidCategory.TYPE);
        }
    }
}
