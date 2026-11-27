package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One page per group of heads that break into the same drops when hit with a hammer. */
class SkullCategory extends ProcessCategory<Processes.SkullRecipe> {
    static final RecipeType<Processes.SkullRecipe> TYPE = type("skull", Processes.SkullRecipe.class);

    private static final int SLOT_Y = 52;

    private final CountlessItemRenderer rangedItems;

    SkullCategory(IJeiHelpers helpers) {
        super(TYPE, helpers.getGuiHelper().createDrawableItemStack(CarcassCategory.item("butchery:iron_hammer")), 82);
        this.rangedItems = new CountlessItemRenderer(
                helpers.getIngredientManager().getIngredientRenderer(VanillaTypes.ITEM_STACK));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.SkullRecipe recipe, IFocusGroup focuses) {
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, 4, SLOT_Y).setSlotName("head")
                .setStandardSlotBackground().addItemStacks(recipe.heads()), text("tip.head"));
        tooltip(builder.addSlot(RecipeIngredientRole.CATALYST, 26, SLOT_Y).setStandardSlotBackground()
                .addItemStacks(Tools.hammers()), text("tip.hammer"));
        int x = 64;
        for (Carcass.Drop drop : recipe.drops()) {
            ItemStack stack = CarcassCategory.item(drop.item());
            if (stack.isEmpty()) {
                continue;
            }
            stack.setCount(Math.max(1, drop.min())); // JEI++ counts the guaranteed amount
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x, SLOT_Y)
                    .setStandardSlotBackground().addItemStack(stack);
            if (drop.max() > drop.min()) {
                slot.setCustomRenderer(VanillaTypes.ITEM_STACK, rangedItems)
                        .setOverlay(range(drop.min() + "-" + drop.max()), 0, 0);
                tooltip(slot, drop.chance() ? text("chance") : text("range", drop.min(), drop.max()));
            }
            x += 20;
        }
    }

    /** A range written where the stack count would be, at half size. */
    private static IDrawable range(String range) {
        return new IDrawable() {
            @Override
            public int getWidth() {
                return 16;
            }

            @Override
            public int getHeight() {
                return 16;
            }

            @Override
            public void draw(GuiGraphics graphics, int x, int y) {
                var font = Minecraft.getInstance().font;
                graphics.pose().pushPose();
                graphics.pose().translate(x + 16, y + 12, 0);
                graphics.pose().scale(0.5F, 0.5F, 1);
                graphics.drawString(font, range, -font.width(range), 0, 0xFFFFFFFF, true);
                graphics.pose().popPose();
            }
        };
    }

    @Override
    public void draw(Processes.SkullRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        preview(graphics, shown(slots, "head"), 0, WIDTH / 2, 24, 40);
        arrow(graphics, 49, SLOT_Y + 4);
        label(graphics, text("skull.how"), WIDTH / 2, 73);
    }

    @Override
    public ResourceLocation getRegistryName(Processes.SkullRecipe recipe) {
        return new ResourceLocation(TYPE.getUid().getNamespace(), "skull/" + recipe.id());
    }
}
