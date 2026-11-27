package io.github.soldierdonkey2.winterheart_butchery.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
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
    private static final int STEP_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 12;
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
        this.contentHeight = contentHeight(carcass.steps().size());
        this.models = new CarcassModels(carcass);
        scrollTo(0);
    }

    static int contentHeight(int steps) {
        return HEADER_HEIGHT + steps * STEP_HEIGHT + FOOTER_HEIGHT;
    }

    /** Where every slot goes. A step with more than DROPS_PER_ROW drops is not supported yet (the cow has at most 6). */
    static List<SlotPlace> layout(Carcass carcass) {
        List<SlotPlace> places = new ArrayList<>();
        places.add(plain(RecipeIngredientRole.INPUT, 46, 12, List.of(CarcassCategory.item(carcass.fresh().block())),
                tip("fresh")));
        places.add(plain(RecipeIngredientRole.CATALYST, 46, 34,
                carcass.supports().stream().map(CarcassCategory::item).toList(), tip("support")));
        places.add(plain(RecipeIngredientRole.CATALYST, 86, 12,
                List.of(CarcassCategory.item(carcass.drained().block())), tip("drained")));
        places.add(plain(RecipeIngredientRole.CATALYST, 86, 34, Tools.cleavers(), tip("bleed_tool")));
        int y = HEADER_HEIGHT;
        for (Carcass.Step step : carcass.steps()) {
            boolean knife = step.tool().equals("skinning_knife");
            places.add(plain(RecipeIngredientRole.CATALYST, 2, y + 13,
                    knife ? Tools.skinningKnives() : Tools.cleavers(), tip(knife ? "skinning_knife" : "cleaver")));
            for (int i = 0; i < step.drops().size(); i++) {
                Carcass.Drop drop = step.drops().get(i);
                ItemStack stack = CarcassCategory.item(drop.item());
                stack.setCount(drop.min()); // JEI++ counts the guaranteed amount
                boolean ranged = drop.max() > drop.min();
                String range = ranged ? drop.min() + "-" + drop.max() : "";
                List<Component> tooltip = new ArrayList<>();
                if (ranged) {
                    tooltip.add(Component.translatable("jei.winterheart_butchery.range", drop.min(), drop.max()));
                }
                if (drop.organ()) {
                    tooltip.add(Component.translatable("jei.winterheart_butchery.organ"));
                }
                places.add(new SlotPlace(RecipeIngredientRole.OUTPUT,
                        DROPS_X + (i % DROPS_PER_ROW) * SLOT_SPACING, y + 13, List.of(stack), tooltip, range,
                        drop.organ()));
            }
            y += STEP_HEIGHT;
        }
        return places;
    }

    private static SlotPlace plain(RecipeIngredientRole role, int x, int y, List<ItemStack> stacks, Component tooltip) {
        return new SlotPlace(role, x, y, stacks, List.of(tooltip), "", false);
    }

    private static Component tip(String key) {
        return Component.translatable("jei.winterheart_butchery.tip." + key);
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
        for (int i = 0; i < carcass.steps().size(); i++) {
            graphics.fill(0, y, BAR_X - 2, y + 1, LINE);
            Component name = Component.translatable("jei.winterheart_butchery.step." + carcass.steps().get(i).name());
            graphics.drawString(font, Component.literal((i + 4) + ". ").append(name), 1, y + 3, TEXT, false);
            y += STEP_HEIGHT;
        }
        small(graphics, Component.translatable("jei.winterheart_butchery.legend"), 1, y + 3);
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

    /** Kill, hang and bleed, plus the rotating carcass that walks through the stages. */
    private void drawHeader(GuiGraphics graphics, int top) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("jei.winterheart_butchery.step.kill"), 1, top + 1, TEXT, false);
        models.drawEntity(graphics, 17, top + 46, 17);
        graphics.drawString(font, ">", 36, top + 16, TEXT, false);
        small(graphics, Component.translatable("jei.winterheart_butchery.step.hang"), 46, top + 4);
        graphics.drawString(font, ">", 71, top + 16, TEXT, false);
        small(graphics, carcass.bleedSeconds() > 0
                ? Component.translatable("jei.winterheart_butchery.step.bleed_timed", carcass.bleedSeconds())
                : Component.translatable("jei.winterheart_butchery.step.bleed"), 78, top + 4);

        // fresh, drained, then the carcass after each step that leaves one
        List<Carcass.Stage> stages = new ArrayList<>(List.of(carcass.fresh(), carcass.drained()));
        for (Carcass.Step step : carcass.steps()) {
            if (step.stateAfter() != null) {
                stages.add(new Carcass.Stage(carcass.drained().block(), step.stateAfter()));
            }
        }
        long now = net.minecraft.Util.getMillis();
        int index = (int) (now / PREVIEW_STAGE_MILLIS % stages.size());
        scissor(graphics, PREVIEW_X, top, PREVIEW_SIZE, HEADER_HEIGHT - 2);
        models.drawBlock(graphics, stages.get(index), PREVIEW_X + PREVIEW_SIZE / 2, top + 36, PREVIEW_SIZE,
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
