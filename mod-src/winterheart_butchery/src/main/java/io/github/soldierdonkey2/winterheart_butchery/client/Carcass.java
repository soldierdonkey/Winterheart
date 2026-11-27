package io.github.soldierdonkey2.winterheart_butchery.client;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.util.List;

/** One animal's workflow, as written to assets/.../jei/carcasses.json by python/butchery_jei. */
public record Carcass(String id, String entity, Stage fresh, Stage drained,
                      @SerializedName("bleed_seconds") int bleedSeconds, List<String> supports, List<Step> steps) {

    /** A hanging carcass block and the value of its `blockstate` property. */
    public record Stage(String block, int state) {}

    /** One right-click on the drained carcass. stateAfter is null when the carcass is used up. */
    public record Step(String name, String tool, @SerializedName("state_after") Integer stateAfter, List<Drop> drops) {}

    public record Drop(String item, int min, int max, boolean organ) {}

    private record File(List<Carcass> animals) {}

    private static final ResourceLocation DATA = new ResourceLocation(WinterheartButchery.MOD_ID, "jei/carcasses.json");

    static List<Carcass> load() {
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(DATA)) {
            return new Gson().fromJson(reader, File.class).animals();
        } catch (Exception e) {
            WinterheartButchery.LOGGER.error("[Butchery] could not read {}; no carcass pages in JEI", DATA, e);
            return List.of();
        }
    }
}
