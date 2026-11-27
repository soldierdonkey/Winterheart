package io.github.soldierdonkey2.winterheart_butchery.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The scrolling page of one carcass. JEI's own scroll widgets only take a uniform slot grid or plain text, so this
 * draws the labels, models and slots itself and moves the slots as it scrolls. The widget covers the whole category.
 */
final class CarcassWidget implements ISlottedRecipeWidget, IJeiInputHandler {
    static final int WIDTH = 170;

    private static final int HEADER_HEIGHT = 74;
    private static final int STEP_LABEL_HEIGHT = 14;
    private static final int ROW_HEIGHT = 18;
    private static final int LINE_HEIGHT = 10;
    /** A five-step animal with two footer lines. */
    static final int TYPICAL_HEIGHT = HEADER_HEIGHT + 5 * (STEP_LABEL_HEIGHT + ROW_HEIGHT) + 2 * LINE_HEIGHT + 2;
    private static final int DROPS_X = 26;
    private static final int DROPS_PER_ROW = 6;
    private static final int SLOT_SPACING = 20;
    private static final int BAR_WIDTH = 4;
    private static final int BAR_X = WIDTH - BAR_WIDTH;
    private static final int SCROLL_STEP = 12;
    private static final int PREVIEW_X = 116;
    private static final int PREVIEW_SIZE = 50;
    private static final long PREVIEW_STAGE_MILLIS = 1500;

    private static final int TEXT = 0xFF404040;
    private static final int LINE = 0xFF9A9A9A;
    private static final int ORGAN = 0x66B0303A;

    /** A slot at content coordinates, with what it shows. */
    record SlotPlace(RecipeIngredientRole role, int x, int y, List<ItemStack> stacks,
                     List<Component> tooltip, String range, boolean organ) {}

    private final Carcass carcass;
    private final List<SlotPlace> places;
    private final List<IRecipeSlotDrawable> slots;
    private final int viewHeight;
    private final int contentHeight;
    private final CarcassModels models;
    private double scroll;
    private boolean dragging;

    CarcassWidget(Carcass carcass, List<SlotPlace> places, List<IRecipeSlotDrawable> slots, int viewHeight) {
        this.carcass = carcass;
        this.places = places;
        this.slots = slots;
        this.viewHeight = viewHeight;
        this.contentHeight = footerTop(carcass) + footer(carcass).size() * LINE_HEIGHT + 2;
        this.models = new CarcassModels(carcass);
        scrollTo(0);
    }

    private static int stepHeight(Carcass.Step step) {
        int rows = Math.max(1, Mth.positiveCeilDiv(step.drops().size(), DROPS_PER_ROW));
        return STEP_LABEL_HEIGHT + rows * ROW_HEIGHT;
    }

    private static int footerTop(Carcass carcass) {
        return HEADER_HEIGHT + carcass.steps().stream().mapToInt(CarcassWidget::stepHeight).sum();
    }

    /** The notes under the last step. */
    private static List<Component> footer(Carcass carcass) {
        List<Component> lines = new ArrayList<>();
        if (carcass.steps().stream().anyMatch(step -> step.drops().stream().anyMatch(Carcass.Drop::organ))) {
            lines.add(text("legend"));
        }
        if (carcass.tray()) {
            lines.add(text("tray"));
        }
        if (differs(carcass)) {
            lines.add(text("hanging_differs").withStyle(ChatFormatting.DARK_RED));
        }
        return lines;
    }

    private static boolean differs(Carcass carcass) {
        return carcass.hangingDifferences() != null && !carcass.hangingDifferences().isEmpty();
    }

    /** Where every slot goes. */
    static List<SlotPlace> layout(Carcass carcass) {
        List<SlotPlace> places = new ArrayList<>();
        places.add(plain(RecipeIngredientRole.INPUT, 46, 12, CarcassCategory.items(carcass.fresh()),
                text(carcass.entity() == null ? "tip.carcass" : "tip.fresh")));
        if (carcass.bleeds()) {
            places.add(plain(RecipeIngredientRole.CATALYST, 46, 34,
                    CarcassCategory.items(List.of("butchery:hook", "butchery:rope")), text("tip.support")));
            places.add(plain(RecipeIngredientRole.CATALYST, 86, 12, CarcassCategory.items(carcass.drained()),
                    text("tip.drained")));
            places.add(plain(RecipeIngredientRole.CATALYST, 86, 34, Tools.cleavers(), text("tip.bleed_tool")));
        }
        if (carcass.repair() != null) {
            ItemStack material = CarcassCategory.item(carcass.repair().item());
            material.setCount(carcass.repair().count());
            places.add(plain(RecipeIngredientRole.INPUT, 46, 34, List.of(material),
                    Component.translatable("jei.winterheart_butchery.tip.repair", carcass.repair().count())));
            places.add(plain(RecipeIngredientRole.CATALYST, 86, 12, CarcassCategory.items(carcass.repaired()),
                    text("tip.repaired")));
        }
        int y = HEADER_HEIGHT;
        for (Carcass.Step step : carcass.steps()) {
            List<ItemStack> tools = switch (step.tool()) {
                case "skinning_knife" -> Tools.skinningKnives();
                case "boss" -> Tools.bossTools();
                case "hacksaw" -> Tools.hacksaws();
                default -> Tools.cleavers();
            };
            List<Component> toolTip = new ArrayList<>(List.of(text("tip." + step.tool())));
            if (step.clicks() > 1) {
                toolTip.add(Component.translatable("jei.winterheart_butchery.tip.clicks", step.clicks()));
            }
            places.add(new SlotPlace(RecipeIngredientRole.CATALYST, 2, y + 13, tools, toolTip, "", false));
            int shown = 0;
            for (Carcass.Drop drop : step.drops()) {
                ItemStack stack = CarcassCategory.item(drop.item());
                if (stack.isEmpty()) {
                    continue;
                }
                stack.setCount(Math.max(1, drop.min())); // JEI++ counts the guaranteed amount
                boolean ranged = drop.max() > drop.min();
                String range = ranged ? drop.min() + "-" + drop.max() : "";
                List<Component> tooltip = new ArrayList<>();
                if (drop.chance()) {
                    tooltip.add(text("chance"));
                } else if (ranged) {
                    tooltip.add(Component.translatable("jei.winterheart_butchery.range", drop.min(), drop.max()));
                }
                if (drop.organ()) {
                    tooltip.add(text("organ"));
                }
                places.add(new SlotPlace(RecipeIngredientRole.OUTPUT,
                        DROPS_X + (shown % DROPS_PER_ROW) * SLOT_SPACING,
                        y + 13 + (shown / DROPS_PER_ROW) * ROW_HEIGHT, List.of(stack), tooltip, range, drop.organ()));
                shown++;
            }
            y += stepHeight(step);
        }
        return places;
    }

    private static SlotPlace plain(RecipeIngredientRole role, int x, int y, List<ItemStack> stacks, Component tooltip) {
        return new SlotPlace(role, x, y, stacks, List.of(tooltip), "", false);
    }

    private static MutableComponent text(String key) {
        return Component.translatable("jei.winterheart_butchery." + key);
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - viewHeight);
    }

    private void scrollTo(double value) {
        scroll = Mth.clamp(value, 0, maxScroll());
        for (int i = 0; i < slots.size(); i++) {
            slots.get(i).setPosition(places.get(i).x(), places.get(i).y() - (int) scroll);
        }
    }

    @Override
    public ScreenPosition getPosition() {
        return new ScreenPosition(0, 0);
    }

    @Override
    public ScreenRectangle getScreenRectangle() {
        return new ScreenRectangle(0, 0, WIDTH, viewHeight);
    }

    @Override
    public ScreenRectangle getArea() {
        return getScreenRectangle();
    }

    private boolean inView(double x, double y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < viewHeight;
    }

    @Override
    public void drawWidget(GuiGraphics graphics, double mouseX, double mouseY) {
        int offset = (int) scroll;
        scissor(graphics, 0, 0, WIDTH, viewHeight);
        drawHeader(graphics, -offset);
        Font font = Minecraft.getInstance().font;
        int y = HEADER_HEIGHT - offset;
        int number = firstStepNumber();
        int cuts = 0;
        for (Carcass.Step step : carcass.steps()) {
            graphics.fill(0, y, BAR_X - 2, y + 1, LINE);
            MutableComponent name = step.name().equals("cut")
                    ? Component.translatable("jei.winterheart_butchery.step.cut", ++cuts)
                    : text("step." + step.name());
            graphics.drawString(font, Component.literal(number++ + ". ").append(name), 1, y + 3, TEXT, false);
            y += stepHeight(step);
        }
        for (Component line : footer(carcass)) {
            small(graphics, line, 1, y + 3);
            y += LINE_HEIGHT;
        }
        for (int i = 0; i < slots.size(); i++) {
            SlotPlace place = places.get(i);
            int slotY = place.y() - offset;
            if (slotY + 17 < 0 || slotY - 1 >= viewHeight) {
                continue;
            }
            slots.get(i).draw(graphics);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200); // above the item
            if (place.organ()) {
                graphics.fill(place.x(), slotY, place.x() + 16, slotY + 16, ORGAN);
            }
            if (!place.range().isEmpty()) {
                graphics.pose().translate(place.x() + 16, slotY + 12, 0);
                graphics.pose().scale(0.5F, 0.5F, 1);
                graphics.drawString(font, place.range(), -font.width(place.range()), 0, 0xFFFFFFFF, true);
            }
            graphics.pose().popPose();
        }
        graphics.disableScissor();
        drawScrollBar(graphics);
    }

    /** The number of the first butchering step, after whichever of kill, repair, hang and bleed apply. */
    private int firstStepNumber() {
        return 1 + (carcass.entity() == null ? 0 : 1) + (carcass.repair() == null ? 0 : 1) + (carcass.bleeds() ? 2 : 0);
    }

    /** Kill, hang and bleed, plus the rotating carcass that walks through the stages. */
    private void drawHeader(GuiGraphics graphics, int top) {
        Font font = Minecraft.getInstance().font;
        int number = 1;
        if (carcass.entity() != null) {
            graphics.drawString(font, Component.literal(number++ + ". ").append(text("step.kill")), 1, top + 1, TEXT,
                    false);
            scissor(graphics, 0, Math.max(0, top + 11), 36, Math.max(0, top + 54 - Math.max(0, top + 11)));
            models.drawEntity(graphics, 17, top + 50, 32, 36);
            graphics.disableScissor();
            graphics.drawString(font, ">", 36, top + 16, TEXT, false);
        }
        if (carcass.bleeds()) {
            small(graphics, Component.literal(number++ + ". ").append(text("step.hang")), 46, top + 4);
            graphics.drawString(font, ">", 71, top + 16, TEXT, false);
            MutableComponent bleed = carcass.bleedSeconds() > 0
                    ? Component.translatable("jei.winterheart_butchery.step.bleed_timed", carcass.bleedSeconds())
                    : text("step.bleed");
            small(graphics, Component.literal(number + ". ").append(bleed), 78, top + 4);
        }

        if (carcass.repair() != null) {
            small(graphics, Component.literal(number + ". ").append(text("step.repair")), 46, top + 4);
            graphics.drawString(font, ">", 71, top + 16, TEXT, false);
        }

        long now = net.minecraft.Util.getMillis();
        int index = (int) (now / PREVIEW_STAGE_MILLIS % carcass.preview().size());
        scissor(graphics, PREVIEW_X, top, PREVIEW_SIZE, HEADER_HEIGHT - 2);
        models.drawBlock(graphics, carcass.preview().get(index), PREVIEW_X + PREVIEW_SIZE / 2, top + 36, PREVIEW_SIZE,
                now % 12000 / 12000F * 360);
        graphics.disableScissor();
    }

    private void small(GuiGraphics graphics, Component text, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.75F, 0.75F, 1);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, TEXT, false);
        graphics.pose().popPose();
    }

    /** Scissor boxes are in screen coordinates, so map a widget rectangle through the current pose. */
    private void scissor(GuiGraphics graphics, int x, int y, int width, int height) {
        Vector3f from = graphics.pose().last().pose().transformPosition(new Vector3f(x, y, 0));
        Vector3f to = graphics.pose().last().pose().transformPosition(new Vector3f(x + width, y + height, 0));
        graphics.enableScissor(Math.round(from.x), Math.round(from.y), Math.round(to.x), Math.round(to.y));
    }

    private int thumbHeight() {
        return Math.max(15, viewHeight * viewHeight / contentHeight);
    }

    private void drawScrollBar(GuiGraphics graphics) {
        if (maxScroll() == 0) {
            return;
        }
        int thumbY = (int) ((viewHeight - thumbHeight()) * scroll / maxScroll());
        graphics.fill(BAR_X, 0, WIDTH, viewHeight, 0xFF555555);
        graphics.fill(BAR_X, thumbY, WIDTH, thumbY + thumbHeight(), 0xFFE0E0E0);
    }

    @Override
    public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double mouseX, double mouseY) {
        if (!inView(mouseX, mouseY)) {
            return Optional.empty();
        }
        return slots.stream().filter(slot -> slot.isMouseOver(mouseX, mouseY)).findFirst()
                .map(slot -> new RecipeSlotUnderMouse(slot, getPosition()));
    }

    /** Explains the footer's warning that hanging this carcass changes its drops. */
    @Override
    public void getTooltip(ITooltipBuilder tooltip, double mouseX, double mouseY) {
        if (!differs(carcass)) {
            return;
        }
        int line = footerTop(carcass) + (footer(carcass).size() - 1) * LINE_HEIGHT - (int) scroll;
        if (!inView(mouseX, mouseY) || mouseY < line || mouseY >= line + LINE_HEIGHT + 2) {
            return;
        }
        tooltip.add(text("hanging_totals"));
        for (Carcass.Difference difference : carcass.hangingDifferences()) {
            tooltip.add(Component.translatable("jei.winterheart_butchery.difference",
                    CarcassCategory.item(difference.item()).getHoverName(), difference.laid(), difference.hanging()));
        }
    }

    /** Consumes the wheel whenever the page can scroll, so reaching the end doesn't flip JEI's page. */
    @Override
    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll() == 0) {
            return false;
        }
        scrollTo(scroll - delta * SCROLL_STEP);
        return true;
    }

    /** Clicking the scroll bar jumps there. */
    @Override
    public boolean handleInput(double mouseX, double mouseY, IJeiUserInput input) {
        InputConstants.Key key = input.getKey();
        dragging = maxScroll() > 0 && key.getType() == InputConstants.Type.MOUSE && key.getValue() == 0
                && mouseX >= BAR_X - 2;
        if (dragging && !input.isSimulate()) {
            jumpTo(mouseY);
        }
        return dragging;
    }

    @Override
    public boolean handleMouseDragged(double mouseX, double mouseY, InputConstants.Key key, double dragX, double dragY) {
        if (dragging) {
            jumpTo(mouseY);
        }
        return dragging;
    }

    private void jumpTo(double mouseY) {
        scrollTo((mouseY - thumbHeight() / 2.0) / Math.max(1, viewHeight - thumbHeight()) * maxScroll());
    }
}
