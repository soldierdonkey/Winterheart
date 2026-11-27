package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * One page per special bone meal, showing the block before and after. Only a guaranteed change is an output; a chance
 * one is findable with R but is not something JEI++ can plan around.
 */
class BonemealCategory extends ProcessCategory<Processes.Bonemeal> {
    static final RecipeType<Processes.Bonemeal> TYPE = type("bonemeal", Processes.Bonemeal.class);

    private static final int SLOT_Y = 66;

    BonemealCategory(IGuiHelper gui, Processes.Bonemeal first) {
        super(TYPE, gui.createDrawableItemStack(CarcassCategory.item(first.item())), 100);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.Bonemeal recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 77, 6).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.item()));
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 27, SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.beforeItem())), text("tip.bonemeal." + recipe.id()));
        boolean certain = recipe.chance() >= 100;
        tooltip(builder.addSlot(certain ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.RENDER_ONLY, 127, SLOT_Y)
                .setStandardSlotBackground().addItemStack(CarcassCategory.item(recipe.afterItem())),
                text("bonemeal.chance", recipe.chance()));
        if (!certain) {
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                    .addItemStack(CarcassCategory.item(recipe.afterItem()));
        }
    }

    @Override
    public void draw(Processes.Bonemeal recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        preview(graphics, recipe.before(), 33, 36, 38);
        preview(graphics, recipe.after(), 137, 36, 38);
        arrow(graphics, 82, 32);
        label(graphics, text("bonemeal.chance", recipe.chance()), 85, 44);
        label(graphics, text("bonemeal." + recipe.id()), WIDTH / 2, 88);
    }

    @Override
    public ResourceLocation getRegistryName(Processes.Bonemeal recipe) {
        return new ResourceLocation(TYPE.getUid().getNamespace(), "bonemeal/" + recipe.id());
    }
}
