package io.github.soldierdonkey2.winterheart_butchery.client;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One page for every skin: place it on a skin rack, salt it, wet it, wait. The skin and salt are inputs; the rack and
 * the sponge or rag are never counted.
 */
class TanningCategory extends ProcessCategory<Processes.Tanning> {
    static final RecipeType<Processes.Tanning> TYPE = type("tanning", Processes.Tanning.class);

    private static final int SLOT_Y = 70;
    private static final int[] SLOT_X = {8, 52, 96, 146};
    private static final long STAGE_MILLIS = 1500;

    TanningCategory(IGuiHelper gui) {
        super(TYPE, gui.createDrawableItemStack(CarcassCategory.item("butchery:skin_rack")), 96);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Processes.Tanning recipe, IFocusGroup focuses) {
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, SLOT_X[0], SLOT_Y).setSlotName("skin")
                .setStandardSlotBackground()
                .addItemStacks(CarcassCategory.items(recipe.skins().stream().map(skin -> skin.get(0)).toList())),
                text("tip.skin"));
        tooltip(builder.addSlot(RecipeIngredientRole.INPUT, SLOT_X[1], SLOT_Y).setStandardSlotBackground()
                .addItemStacks(salt(recipe)), text("tip.salt"));
        tooltip(builder.addSlot(RecipeIngredientRole.CATALYST, SLOT_X[2], SLOT_Y).setStandardSlotBackground()
                .addItemStacks(CarcassCategory.items(recipe.wetters())), text("tip.wetter"));
        tooltip(builder.addSlot(RecipeIngredientRole.OUTPUT, SLOT_X[3], SLOT_Y).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.result())), text("tip.leather"));
        builder.addSlot(RecipeIngredientRole.CATALYST, 4, 4).setStandardSlotBackground()
                .addItemStack(CarcassCategory.item(recipe.rack()));
    }

    private static List<ItemStack> salt(Processes.Tanning recipe) {
        Set<Item> items = new LinkedHashSet<>();
        ItemStack own = CarcassCategory.item(recipe.salt());
        if (!own.isEmpty()) {
            items.add(own.getItem());
        }
        for (String id : recipe.saltTags()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(id));
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> items.add(holder.value()));
        }
        return items.stream().map(ItemStack::new).toList();
    }

    /** The rack preview walks through its four looks, showing whichever skin the slot is on. */
    @Override
    public void draw(Processes.Tanning recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX,
                     double mouseY) {
        String skin = shown(slots, "skin");
        int placed = recipe.skins().stream().filter(entry -> entry.get(0).equals(skin))
                .map(entry -> Integer.parseInt(entry.get(1))).findFirst().orElse(0);
        int[] stages = {placed, recipe.saltedState(), recipe.wetState(), recipe.doneState()};
        int stage = (int) (net.minecraft.Util.getMillis() / STAGE_MILLIS % stages.length);
        preview(graphics, recipe.rack(), stages[stage], WIDTH / 2, 28, 52);

        label(graphics, text("tan.place"), SLOT_X[0] + 8, SLOT_Y - 11);
        label(graphics, text("tan.salt"), SLOT_X[1] + 8, SLOT_Y - 11);
        label(graphics, text("tan.wet"), SLOT_X[2] + 8, SLOT_Y - 11);
        label(graphics, text("tan.wait", recipe.seconds()), SLOT_X[3] - 14, SLOT_Y + 18);
        arrow(graphics, 34, SLOT_Y + 4);
        arrow(graphics, 78, SLOT_Y + 4);
        arrow(graphics, 126, SLOT_Y + 4);
    }

    @Override
    public ResourceLocation getRegistryName(Processes.Tanning recipe) {
        return new ResourceLocation(TYPE.getUid().getNamespace(), "tanning/leather");
    }
}
