package io.github.soldierdonkey2.winterheart_butchery.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** What the process pages share: a title, an icon, labels and block previews. */
abstract class ProcessCategory<T> implements IRecipeCategory<T> {
    static final int WIDTH = 170;
    static final int TEXT = 0xFF404040;

    private final RecipeType<T> type;
    private final Component title;
    private final IDrawable icon;
    private final int height;
    private final CarcassModels models = new CarcassModels(null);

    ProcessCategory(RecipeType<T> type, IDrawable icon, int height) {
        this.type = type;
        this.title = Component.translatable("jei.winterheart_butchery." + type.getUid().getPath());
        this.icon = icon;
        this.height = height;
    }

    static <T> RecipeType<T> type(String path, Class<? extends T> recipeClass) {
        return RecipeType.create(WinterheartButchery.MOD_ID, path, recipeClass);
    }

    @Override
    public RecipeType<T> getRecipeType() {
        return type;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return height;
    }

    static MutableComponent text(String key, Object... args) {
        return Component.translatable("jei.winterheart_butchery." + key, args);
    }

    static IRecipeSlotBuilder tooltip(IRecipeSlotBuilder slot, Component line) {
        return slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(line));
    }

    /** Small text centred on x. */
    static void label(GuiGraphics graphics, Component line, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.75F, 0.75F, 1);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, line, -font.width(line) / 2, 0, TEXT, false);
        graphics.pose().popPose();
    }

    static void arrow(GuiGraphics graphics, int x, int y) {
        graphics.drawString(Minecraft.getInstance().font, ">", x, y, TEXT, false);
    }

    /** Draws a block facing the viewer, centred on (x, y). */
    void preview(GuiGraphics graphics, String block, int state, int x, int y, int size) {
        models.drawBlock(graphics, List.of(block, Integer.toString(state)), x, y, size);
    }

    /** Like preview, for a block with water in it. */
    void previewWatery(GuiGraphics graphics, String block, int state, int x, int y, int size) {
        models.drawHoldingWater(graphics, List.of(block, Integer.toString(state)), x, y, size);
    }

    /** Like preview, brightened. */
    void previewBright(GuiGraphics graphics, String block, int x, int y, int size, float brightness) {
        models.drawBrightened(graphics, List.of(parse(block)), x, y, size, brightness);
    }

    /** Draws block states written as in /setblock, stacked bottom first. */
    void preview(GuiGraphics graphics, List<String> states, int x, int y, int size) {
        models.drawColumn(graphics, states.stream().map(ProcessCategory::parse).toList(), x, y, size);
    }

    private static BlockState parse(String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    /** The ID of the item the named slot is showing right now (slots with several items cycle). */
    static String shown(IRecipeSlotsView slots, String name) {
        ItemStack stack = slots.findSlotByName(name).flatMap(slot -> slot.getDisplayedItemStack())
                .orElse(ItemStack.EMPTY);
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
