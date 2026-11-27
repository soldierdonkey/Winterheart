package io.github.soldierdonkey2.winterheart_butchery.client;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import io.github.soldierdonkey2.winterheart_butchery.WinterheartButchery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.util.List;

/**
 * One carcass's workflow, as written to assets/.../jei/carcasses.json by python/butchery_jei. entity is null for a
 * carcass no mob drops; drained is null for one that is cut up without bleeding. repair and repaired are null unless
 * the carcass is rebuilt before it is cut up.
 */
public record Carcass(String id, String entity, @SerializedName("entity_nbt") String entityNbt, List<String> fresh,
                      List<String> drained, boolean bleeds, @SerializedName("bleed_seconds") int bleedSeconds,
                      boolean tray, Repair repair, List<String> repaired, List<List<String>> preview, List<Step> steps,
                      @SerializedName("hanging_differences") List<Difference> hangingDifferences) {

    /** The material right-clicked onto the carcass, `count` times, to turn it into the repaired one. */
    public record Repair(String item, int count) {}

    /** One butchering step: `clicks` right-clicks on the carcass, after which the step's drops are all out. */
    public record Step(String name, String tool, int clicks, List<Drop> drops) {}

    /** chance marks a drop that is one of several possible. */
    public record Drop(String item, int min, int max, boolean chance, boolean organ) {}

    /** An item whose total differs when the carcass is butchered on a hook instead of laid down. */
    public record Difference(String item, String laid, String hanging) {}

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
