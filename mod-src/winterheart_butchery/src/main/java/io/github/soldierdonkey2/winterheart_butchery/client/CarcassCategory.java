package io.github.soldierdonkey2.winterheart_butchery.client;

import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One page per animal: kill, hang and bleed, then every butchering step with its tool and drops.
 *
 * Slot roles decide what JEI++ puts in a recipe tree: the fresh carcass is the only INPUT, every drop is an OUTPUT,
 * and tools, supports and the drained carcass are never counted.
 */
public class CarcassCategory implements IRecipeCategory<Carcass> {
    static final RecipeType<Carcass> TYPE = RecipeType.create(WinterheartButchery.MOD_ID, "carcass", Carcass.class);

    private final IDrawable icon;

    private final CountlessItemRenderer rangedItems;

    CarcassCategory(IJeiHelpers helpers) {
        IGuiHelper gui = helpers.getGuiHelper();
        this.rangedItems = new CountlessItemRenderer(
                helpers.getIngredientManager().getIngredientRenderer(VanillaTypes.ITEM_STACK));
        Item cleaver = BuiltInRegistries.ITEM.get(new ResourceLocation("butchery", "iron_cleaver"));
        this.icon = gui.createDrawableItemStack(new ItemStack(cleaver));
    }

    @Override
    public RecipeType<Carcass> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.winterheart_butchery.carcass");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return CarcassWidget.WIDTH;
    }

    /**
     * The height of the viewport: the whole page if JEI's recipe screen has room for it, otherwise what fits.
     * JEI's screen is (window height - 58, or - 76 with the centred search bar), at least 175 and at most its
     * "Recipe GUI Height" setting (default 350); 48 of that is its header and borders. The larger margin is assumed.
     */
    @Override
    public int getHeight() {
        int screen = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int room = Mth.clamp(screen - 76, 175, 350) - 48;
        // All pages share the category height: enough for a five-step animal. Longer pages scroll.
        return Math.min(CarcassWidget.TYPICAL_HEIGHT, room);
    }

    @Override
    public ResourceLocation getRegistryName(Carcass carcass) {
        return new ResourceLocation(WinterheartButchery.MOD_ID, "carcass/" + carcass.id());
    }

    /** Slots are named by their index, and positioned by the widget (content coordinates, before scrolling). */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Carcass carcass, IFocusGroup focuses) {
        List<CarcassWidget.SlotPlace> places = CarcassWidget.layout(carcass);
        for (int i = 0; i < places.size(); i++) {
            CarcassWidget.SlotPlace place = places.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(place.role(), place.x(), place.y())
                    .setSlotName(Integer.toString(i))
                    .setStandardSlotBackground()
                    .addItemStacks(place.stacks());
            if (!place.range().isEmpty()) {
                slot.setCustomRenderer(VanillaTypes.ITEM_STACK, rangedItems);
            }
            if (!place.tooltip().isEmpty()) {
                slot.addRichTooltipCallback((view, tooltip) -> place.tooltip().forEach(tooltip::add));
            }
        }
        // R on any of its carcasses opens this page too. These are invisible because a visible output would show up in
        // JEI++ as a leftover; U already works through the input and catalyst slots.
        IIngredientAcceptor<?> lookups = builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT);
        lookups.addItemStacks(items(carcass.fresh()));
        if (carcass.drained() != null) {
            lookups.addItemStacks(items(carcass.drained()));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Carcass carcass, IFocusGroup focuses) {
        List<CarcassWidget.SlotPlace> places = CarcassWidget.layout(carcass);
        List<IRecipeSlotDrawable> slots = new ArrayList<>(places.size());
        for (int i = 0; i < places.size(); i++) {
            String name = Integer.toString(i);
            slots.add(builder.getRecipeSlots().getSlots().stream()
                    .filter(slot -> slot.getSlotName().map(name::equals).orElse(false))
                    .findFirst().orElseThrow());
        }
        CarcassWidget widget = new CarcassWidget(carcass, places, slots, getHeight());
        builder.addSlottedWidget(widget, slots);
        builder.addInputHandler(widget);
    }

    /** The items with these IDs; unknown IDs and air are left out. */
    static List<ItemStack> items(List<String> ids) {
        return ids.stream().map(CarcassCategory::item).filter(stack -> !stack.isEmpty()).toList();
    }

    static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(id)));
    }
}
