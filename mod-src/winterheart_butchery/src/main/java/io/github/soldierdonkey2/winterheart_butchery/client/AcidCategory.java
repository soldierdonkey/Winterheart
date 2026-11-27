package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * One page per skeleton that acid leaves, with the carcasses that turn into it. The returned glass bottle is shown but
 * is not an output, so JEI++ never offers acid as a way to get bottles.
 */
class AcidCategory extends ProcessCategory<Processes.AcidRecipe> {
    static final RecipeType<Processes.AcidRecipe> TYPE = type("acid", Processes.AcidRecipe.class);

    private static final int SLOT_Y = 66;

    private final Processes.Acid acid;

    AcidCategory(IGuiHelper gui, Processes.Acid acid) {
        super(TYPE, gui.createDrawableItemStack(CarcassCategory.item(acid.bottle())), 96);
        this.acid = acid;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.AcidRecipe recipe, IFocusGroup focuses) {
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 18, SLOT_Y).setSlotName("carcass")
                .setStandardSlotBackground().addItemStacks(recipe.carcasses()), text("tip.uncut"));
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 44, SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(acid.bottle())), text("tip.acid"));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 110, SLOT_Y).setStandardSlotBackground()
                .addItemStack(recipe.result());
        tooltip(builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 136, SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(acid.returned())), text("tip.returned"));
    }

    @Override
    public void draw(Processes.AcidRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        preview(graphics, shown(slots, "carcass"), 0, 38, 32, 52);
        preview(graphics, BuiltInRegistries.ITEM.getKey(recipe.result().getItem()).toString(), 0, 132, 32, 52);
        arrow(graphics, 82, 28);
        label(graphics, text("acid.wait", acid.seconds()), 85, 40);
        arrow(graphics, 82, SLOT_Y + 4);
        label(graphics, text("acid.how"), WIDTH / 2, 87);
    }

    @Override
    public ResourceLocation getRegistryName(Processes.AcidRecipe recipe) {
        ResourceLocation result = BuiltInRegistries.ITEM.getKey(recipe.result().getItem());
        return new ResourceLocation(TYPE.getUid().getNamespace(), "acid/" + result.getPath());
    }
}
