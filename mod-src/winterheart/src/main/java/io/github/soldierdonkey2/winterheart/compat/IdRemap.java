package io.github.soldierdonkey2.winterheart.compat;

import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

/** Worlds made while these items were KubeJS items still reference them as kubejs:<name>. Point those at winterheart:<name>. */
public final class IdRemap {
    private static final String OLD_NAMESPACE = "kubejs";
    private static final Map<String, String> ITEMS = Map.of(
            "saltpeter", "saltpeter",
            "crude_nitrate_bottle", "crude_nitrate_bottle",
            "potassium_nitrate_bottle", "potassium_nitrate_bottle",
            "guide_book", "guide_book",
            "lost_page", "lost_page");

    private IdRemap() {}

    @SubscribeEvent
    public static void onMissingMappings(MissingMappingsEvent event) {
        for (MissingMappingsEvent.Mapping<Item> mapping : event.getMappings(ForgeRegistries.Keys.ITEMS, OLD_NAMESPACE)) {
            String target = ITEMS.get(mapping.getKey().getPath());
            if (target == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Winterheart.MOD_ID, target));
            if (item == null) continue;
            mapping.remap(item);
            Winterheart.LOGGER.info("Remapped item {} -> {}:{}", mapping.getKey(), Winterheart.MOD_ID, target);
        }
    }
}
