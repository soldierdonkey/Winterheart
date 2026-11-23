package io.github.soldierdonkey2.winterheart_guides.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.soldierdonkey2.winterheart.item.ModItems;
import io.github.soldierdonkey2.winterheart_guides.Books;
import io.github.soldierdonkey2.winterheart_guides.GuideData;
import io.github.soldierdonkey2.winterheart_guides.GuideManager;
import io.github.soldierdonkey2.winterheart_guides.WinterheartGuides;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Lists every guide book and lost page from guides.json in JEI, each with its own name, tooltip and texture. All
 * books share the item winterheart:guide_book and differ only by NBT, so JEI treats the `guidebook` / `lostpage`
 * tags as part of the item's identity. Runs on the client and reads guides.json from the local instance; JEI builds
 * its list when you join a world, so after editing books, rejoin. JEI++'s `nbtGroupingEnabled` must be off.
 */
@JeiPlugin
public class GuidesJei implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("winterheart_guides", "jei");
    }

    private static IIngredientSubtypeInterpreter<ItemStack> byTag(String key) {
        return (stack, ctx) -> {
            CompoundTag tag = stack.getTag();
            return tag != null && tag.contains(key) ? tag.getString(key) : IIngredientSubtypeInterpreter.NONE;
        };
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(ModItems.GUIDE_BOOK.get(), byTag("guidebook"));
        registration.registerSubtypeInterpreter(ModItems.LOST_PAGE.get(), byTag("lostpage"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        GuideData data;
        try {
            data = GuideData.parse(Files.readString(GuideManager.DATA_FILE));
        } catch (Exception e) {
            WinterheartGuides.LOGGER.error("[Guides] JEI: could not read {}; guide books will not be listed", GuideManager.DATA_FILE);
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        data.books.forEach(b -> { if (b.name() != null) stacks.add(Books.makeBookItem(b)); });
        data.pages.forEach(p -> { if (p.name() != null) stacks.add(Books.makeLostPageItem(p)); });
        runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stacks);
        WinterheartGuides.LOGGER.info("[Guides] JEI: added {} guide book(s) and {} lost page(s)", data.books.size(), data.pages.size());
    }
}
