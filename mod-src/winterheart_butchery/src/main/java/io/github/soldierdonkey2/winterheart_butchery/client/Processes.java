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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every process but butchering, as written to assets/.../jei/processes.json by python/butchery_jei. */
record Processes(Tanning tanning, List<SkullGroup> skulls, Acid acid, Salt salt, Blood blood,
                 List<Bonemeal> bonemeals, Cleaning cleaning) {

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

    /** `stages` are the basin `blockstate`s from full of water to only salt left. */
    record Salt(String basin, String water, String result, int count,
                @SerializedName("interval_seconds") int intervalSeconds, List<Integer> stages) {}

    /** `mb` is what an ordinary carcass bleeds; `smallDepth` how far down a small one finds a grate. */
    record Blood(String grate, int depth, @SerializedName("small_depth") int smallDepth, int mb,
                 @SerializedName("bottle_mb") int bottleMb, List<BloodType> types) {}

    record BloodType(String id, String bottle, String bucket) {}

    /** `before` and `after` are block states, bottom first, as in the /setblock command. */
    record Bonemeal(String id, String item, int chance, List<String> before,
                    @SerializedName("before_item") String beforeItem, List<String> after,
                    @SerializedName("after_item") String afterItem) {}

    record Cleaning(String puddle, List<String> water, List<CleaningTool> tools) {}

    /** `tag` is the item NBT number holding the cleans left. */
    record CleaningTool(String item, String tag, int uses) {}

    /**
     * One kind of blood into one container. Small carcasses bleed too little to count on, so they are listed apart.
     */
    record BloodRecipe(String id, boolean bucket, ItemStack container, ItemStack result, List<ItemStack> carcasses,
                       Map<Item, List<String>> previews, List<ItemStack> small) {}

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

    /** A bottle and a bucket page for each kind of blood: one carcass fills `count` containers. */
    List<BloodRecipe> bloodRecipes(List<Carcass> animals) {
        List<BloodRecipe> recipes = new ArrayList<>();
        for (BloodType type : blood.types()) {
            Map<Item, List<String>> previews = new LinkedHashMap<>();
            Set<Item> small = new LinkedHashSet<>();
            for (Carcass animal : animals) {
                String kind = animal.blood() == null ? "blood" : animal.blood();
                if (!animal.bleeds() || !kind.equals(type.id())) {
                    continue;
                }
                for (ItemStack stack : CarcassCategory.items(animal.fresh())) {
                    if (animal.bloodMb() == null) {
                        previews.put(stack.getItem(), animal.preview().get(0));
                    } else {
                        small.add(stack.getItem());
                    }
                }
            }
            if (previews.isEmpty()) {
                continue;
            }
            List<ItemStack> carcasses = previews.keySet().stream().map(ItemStack::new).toList();
            List<ItemStack> little = small.stream().map(ItemStack::new).toList();
            for (boolean bucket : new boolean[] {false, true}) {
                int count = bucket ? 1 : blood.mb() / blood.bottleMb();
                ItemStack container = new ItemStack(bucket ? Items.BUCKET : Items.GLASS_BOTTLE, count);
                ItemStack result = CarcassCategory.item(bucket ? type.bucket() : type.bottle()).copyWithCount(count);
                recipes.add(new BloodRecipe(type.id(), bucket, container, result, carcasses, previews, little));
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
