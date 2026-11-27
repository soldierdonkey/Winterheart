package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * One page per cleaning tool: wet it, then wipe up blood puddles. Nothing is made, so there is no output: the wet tool
 * is only shown.
 */
class CleaningCategory extends ProcessCategory<Processes.CleaningTool> {
    static final RecipeType<Processes.CleaningTool> TYPE = type("cleaning", Processes.CleaningTool.class);

    private static final int SLOT_Y = 44;

    private final Processes.Cleaning cleaning;

    CleaningCategory(IGuiHelper gui, Processes.Cleaning cleaning) {
        super(TYPE, gui.createDrawableItemStack(wet(cleaning.tools().get(cleaning.tools().size() - 1))), 96);
        this.cleaning = cleaning;
    }

    private static ItemStack wet(Processes.CleaningTool tool) {
        ItemStack stack = CarcassCategory.item(tool.item());
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putDouble(tool.tag(), tool.uses());
        }
        return stack;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.CleaningTool recipe, IFocusGroup focuses) {
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 6, SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.item())), text("tip.dry"));
        List<ItemStack> water = CarcassCategory.items(cleaning.water());
        tooltip(builder.addSlot(RecipeIngredientRole.CATALYST, 40, SLOT_Y).setStandardSlotBackground()
                .addItemStacks(water), text("tip.water"));
        tooltip(builder.addSlot(RecipeIngredientRole.RENDER_ONLY, 90, SLOT_Y).setStandardSlotBackground()
                .addItemStack(wet(recipe)), text("clean.uses", recipe.uses()));
        tooltip(builder.addSlot(RecipeIngredientRole.CATALYST, 140, 58).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(cleaning.puddle())), text("tip.puddle"));
    }

    @Override
    public void draw(Processes.CleaningTool recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        preview(graphics, List.of(cleaning.puddle()), 148, 32, 40);
        label(graphics, text("clean.wet"), 48, SLOT_Y - 11);
        label(graphics, text("clean.uses", recipe.uses()), 98, SLOT_Y - 11);
        label(graphics, text("clean.wipe"), 148, 8);
        arrow(graphics, 25, SLOT_Y + 4);
        arrow(graphics, 70, SLOT_Y + 4);
        arrow(graphics, 118, SLOT_Y + 4);
        label(graphics, text("clean.how"), WIDTH / 2, 85);
    }

    @Override
    public ResourceLocation getRegistryName(Processes.CleaningTool recipe) {
        return new ResourceLocation(TYPE.getUid().getNamespace(),
                "cleaning/" + new ResourceLocation(recipe.item()).getPath());
    }
}
