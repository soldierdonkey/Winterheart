package io.github.soldierdonkey2.winterheart_butchery.client;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Tanning, skull crushing and acid, as written to assets/.../jei/processes.json by python/butchery_jei. */
record Processes(Tanning tanning, List<SkullGroup> skulls, Acid acid) {

    /** A skin is the item and the skin rack `blockstate` that shows it. */
    record Tanning(String rack, @SerializedName("salted_state") int saltedState,
                   @SerializedName("wet_state") int wetState, @SerializedName("done_state") int doneState,
                   String salt, @SerializedName("salt_tags") List<String> saltTags, List<String> wetters, String result,
                   List<List<String>> skins, int seconds) {}

    /** Heads (blocks in `tags` but not in `exclude`) that break into the same drops. */
    record SkullGroup(String id, List<String> tags, List<String> exclude, List<Carcass.Drop> drops) {}

    record Acid(int seconds, String bottle, String returned, List<AcidResult> results) {}

    /** Acid turns any block in `tags` into `result`. */
    record AcidResult(String result, List<String> tags) {}

    /** A skull group with the head items it covers in this game. */
    record SkullRecipe(String id, List<ItemStack> heads, List<Carcass.Drop> drops) {}

    /** An acid result with the carcass items that turn into it in this game. */
    record AcidRecipe(ItemStack result, List<ItemStack> carcasses) {}

    private static final ResourceLocation DATA = new ResourceLocation(WinterheartButchery.MOD_ID, "jei/processes.json");

    static Processes load() {
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(DATA)) {
            return new Gson().fromJson(reader, Processes.class);
        } catch (Exception e) {
            WinterheartButchery.LOGGER.error("[Butchery] could not read {}; no process pages in JEI", DATA, e);
            return null;
        }
    }

    /** Each head goes to the first group that matches it. Groups with no heads are left out. */
    List<SkullRecipe> skullRecipes() {
        Set<Block> taken = new LinkedHashSet<>();
        List<SkullRecipe> recipes = new ArrayList<>();
        for (SkullGroup group : skulls) {
            Set<Block> blocks = blocks(group.tags());
            blocks.removeAll(blocks(group.exclude()));
            blocks.removeAll(taken);
            taken.addAll(blocks);
            List<ItemStack> heads = items(blocks);
            if (!heads.isEmpty()) {
                recipes.add(new SkullRecipe(group.id(), heads, group.drops()));
            }
        }
        return recipes;
    }

    /** Results whose tags hold nothing are left out: Butchery names some block tags that don't exist. */
    List<AcidRecipe> acidRecipes() {
        List<AcidRecipe> recipes = new ArrayList<>();
        for (AcidResult entry : acid.results()) {
            ItemStack result = CarcassCategory.item(entry.result());
            List<ItemStack> carcasses = items(blocks(entry.tags()));
            if (!result.isEmpty() && !carcasses.isEmpty()) {
                recipes.add(new AcidRecipe(result, carcasses));
            }
        }
        return recipes;
    }

    private static Set<Block> blocks(List<String> tags) {
        Set<Block> blocks = new LinkedHashSet<>();
        for (String id : tags) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, new ResourceLocation(id));
            BuiltInRegistries.BLOCK.getTagOrEmpty(tag).forEach(holder -> blocks.add(holder.value()));
        }
        return blocks;
    }

    /** The blocks' items, once each (a wall skull and its floor skull share one). */
    private static List<ItemStack> items(Set<Block> blocks) {
        Set<Item> items = new LinkedHashSet<>();
        blocks.forEach(block -> items.add(block.asItem()));
        items.remove(Items.AIR);
        return items.stream().map(ItemStack::new).toList();
    }
}
