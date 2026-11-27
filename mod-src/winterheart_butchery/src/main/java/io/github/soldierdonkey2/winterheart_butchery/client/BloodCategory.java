package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * One page per kind of blood and container: bleed a carcass over a blood grate and take the blood out. The carcass and
 * the containers are inputs, so JEI++ counts one carcass for every grateful of blood.
 */
class BloodCategory extends ProcessCategory<Processes.BloodRecipe> {
    static final RecipeType<Processes.BloodRecipe> TYPE = type("blood", Processes.BloodRecipe.class);

    private static final int SLOT_Y = 34;
    private static final int NOTE_Y = 89;

    private final Processes.Blood blood;

    BloodCategory(IGuiHelper gui, Processes.Blood blood) {
        super(TYPE, gui.createDrawableItemStack(CarcassCategory.item(blood.grate())), 100);
        this.blood = blood;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.BloodRecipe recipe, IFocusGroup focuses) {
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 56, SLOT_Y).setSlotName("carcass")
                .setStandardSlotBackground().addItemStacks(recipe.carcasses()), text("tip.bleed_carcass"));
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 80, SLOT_Y).setStandardSlotBackground()
                .addItemStack(recipe.container()), text(recipe.bucket() ? "tip.bucket" : "tip.bottle",
                blood.bottleMb()));
        tooltip(builder.addSlot(RecipeIngredientRole.OUTPUT, 140, SLOT_Y).setStandardSlotBackground()
                .addItemStack(recipe.result()), text("tip.blood_out"));
        builder.addSlot(RecipeIngredientRole.CATALYST, 126, 4).setStandardSlotBackground()
                .addItemStacks(Tools.cleavers()).addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(text("tip.bleed_cleaver")));
        builder.addSlot(RecipeIngredientRole.CATALYST, 148, 4).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(blood.grate())).addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(text("blood.grate", blood.depth()));
                    tooltip.add(text("tip.phantom", blood.smallDepth()));
                });
    }

    /** The carcass the slot is on hangs above the grate. */
    @Override
    public void draw(Processes.BloodRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        slots.findSlotByName("carcass").flatMap(slot -> slot.getDisplayedItemStack()).ifPresent(shown -> {
            List<String> stage = recipe.previews().get(shown.getItem());
            if (stage != null) {
                preview(graphics, stage.get(0), Integer.parseInt(stage.get(1)), 26, 20, 38);
            }
        });
        preview(graphics, blood.grate(), 0, 26, 58, 30);
        arrow(graphics, 115, SLOT_Y + 4);
        label(graphics, text("blood.mb", blood.mb()), 114, SLOT_Y + 20);
        label(graphics, text("blood.how"), WIDTH / 2, 78);
        label(graphics, note(recipe), WIDTH / 2, NOTE_Y);
    }

    private Component note(Processes.BloodRecipe recipe) {
        return recipe.small().isEmpty() ? text("blood.grate", blood.depth()) : text("blood.small");
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Processes.BloodRecipe recipe, IRecipeSlotsView slots,
                           double mouseX, double mouseY) {
        if (recipe.small().isEmpty() || mouseY < NOTE_Y - 2 || mouseY > NOTE_Y + 8) {
            return;
        }
        tooltip.add(text("blood.small.tip", blood.depth(), blood.smallDepth()));
        // Every cat colour has the same item name.
        recipe.small().stream().map(carcass -> carcass.getHoverName().getString()).distinct()
                .forEach(name -> tooltip.add(Component.literal(name)));
    }

    @Override
    public ResourceLocation getRegistryName(Processes.BloodRecipe recipe) {
        return new ResourceLocation(TYPE.getUid().getNamespace(),
                "blood/" + recipe.id() + (recipe.bucket() ? "_bucket" : "_bottle"));
    }
}
