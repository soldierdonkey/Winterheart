package io.github.soldierdonkey2.winterheart_butchery.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * JEI's item renderer without the stack count, for drops labelled with a range: the count (the minimum, kept for JEI++)
 * would be drawn underneath the range.
 */
final class CountlessItemRenderer implements IIngredientRenderer<ItemStack> {
    private final IIngredientRenderer<ItemStack> items;

    CountlessItemRenderer(IIngredientRenderer<ItemStack> items) {
        this.items = items;
    }

    @Override
    public void render(GuiGraphics graphics, ItemStack stack) {
        RenderSystem.enableDepthTest();
        graphics.renderFakeItem(stack, 0, 0);
        graphics.renderItemDecorations(getFontRenderer(Minecraft.getInstance(), stack), stack, 0, 0, "");
        RenderSystem.disableBlend();
    }

    @Override
    @SuppressWarnings("removal")
    public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
        return items.getTooltip(stack, flag);
    }

    @Override
    public Font getFontRenderer(Minecraft minecraft, ItemStack stack) {
        return items.getFontRenderer(minecraft, stack);
    }
}
