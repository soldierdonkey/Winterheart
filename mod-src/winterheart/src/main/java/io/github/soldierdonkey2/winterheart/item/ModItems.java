package io.github.soldierdonkey2.winterheart.item;

import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Winterheart.MOD_ID);

    /** Metals and parts that get a part item (winterheart:<metal>_<part>). Recipes: python/recipes/gen_recipes.py. */
    public static final String[] METALS = {"copper", "iron", "gold", "diamond"};
    public static final String[] PARTS = {"plate", "rod", "ring", "gear", "bolt", "screw"};

    public static final Map<String, RegistryObject<Item>> METAL_PARTS = new LinkedHashMap<>();

    static {
        for (String metal : METALS) {
            for (String part : PARTS) {
                String id = metal + "_" + part;
                METAL_PARTS.put(id, ITEMS.register(id, () -> new Item(new Item.Properties())));
            }
        }
    }

    // Saltpeter chain: rich soil -> crude nitrate -> (brewing, charred planks) -> potassium nitrate -> (furnace) -> saltpeter
    public static final RegistryObject<Item> SALTPETER = ITEMS.register("saltpeter",
            () -> new TooltipItem(new Item.Properties()));
    public static final RegistryObject<Item> CRUDE_NITRATE_BOTTLE = ITEMS.register("crude_nitrate_bottle",
            () -> new TooltipItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> POTASSIUM_NITRATE_BOTTLE = ITEMS.register("potassium_nitrate_bottle",
            () -> new TooltipItem(new Item.Properties().stacksTo(16)));

    // Guides. Every lost page is this one item and differs only by NBT (lostpage: "<id>"); guides.json defines them.
    public static final RegistryObject<Item> GUIDE_BOOK = ITEMS.register("guide_book",
            () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LOST_PAGE = ITEMS.register("lost_page",
            () -> new Item(new Item.Properties()));

    private ModItems() {}
}
