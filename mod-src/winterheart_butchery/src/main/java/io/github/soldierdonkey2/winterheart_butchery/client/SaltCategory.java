package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** One page: fill a basin with water and leave it in the sun. The water bucket is the only input. */
class SaltCategory extends ProcessCategory<Processes.Salt> {
    static final RecipeType<Processes.Salt> TYPE = type("salt", Processes.Salt.class);

    private static final int SLOT_Y = 28;
    private static final long STAGE_MILLIS = 1000;

    SaltCategory(IGuiHelper gui, Processes.Salt salt) {
        super(TYPE, gui.createDrawableItemStack(CarcassCategory.item(salt.basin())), 96);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.Salt recipe, IFocusGroup focuses) {
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 14, SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.water())), text("tip.salt_water"));
        tooltip(builder.addSlot(RecipeIngredientRole.OUTPUT, 140, SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.result()).copyWithCount(recipe.count())),
                text("tip.salt_take"));
        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 4).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.basin()));
    }

    /** The basin walks from full of water to full of salt. */
    @Override
    public void draw(Processes.Salt recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        int made = (int) (Util.getMillis() / STAGE_MILLIS % recipe.stages().size());
        previewWatery(graphics, recipe.basin(), recipe.stages().get(made), WIDTH / 2, 30, 44);
        label(graphics, text("salt.made", made, recipe.count()), WIDTH / 2, 58);
        arrow(graphics, 44, SLOT_Y + 4);
        arrow(graphics, 120, SLOT_Y + 4);
        label(graphics, text("salt.needs"), WIDTH / 2, 74);
        int minutes = recipe.count() * recipe.intervalSeconds() / 60;
        label(graphics, text("salt.time", recipe.intervalSeconds(), minutes), WIDTH / 2, 85);
    }

    @Override
    public ResourceLocation getRegistryName(Processes.Salt recipe) {
        return new ResourceLocation(TYPE.getUid().getNamespace(), "salt/basin");
    }
}
