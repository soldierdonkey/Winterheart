package io.github.soldierdonkey2.winterheart_butchery.client;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The items Butchery accepts at each step, read from its tool tags. */
final class Tools {
    private Tools() {}

    static List<ItemStack> cleavers() {
        return stacks(tagged("cleaver"));
    }

    /** Butchery treats an item in both tool tags as a cleaver, so it can never skin. */
    static List<ItemStack> skinningKnives() {
        Set<Item> knives = tagged("skinning_knives");
        knives.removeAll(tagged("cleaver"));
        return stacks(knives);
    }

    private static Set<Item> tagged(String path) {
        Set<Item> items = new LinkedHashSet<>();
        for (String namespace : new String[]{"forge", "c"}) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(namespace, path));
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(holder -> items.add(holder.value()));
        }
        return items;
    }

    private static List<ItemStack> stacks(Set<Item> items) {
        return items.stream().map(ItemStack::new).toList();
    }
}
