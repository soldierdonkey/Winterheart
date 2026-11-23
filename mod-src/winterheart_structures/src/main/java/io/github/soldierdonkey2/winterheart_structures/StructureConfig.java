package io.github.soldierdonkey2.winterheart_structures;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where each fixed structure goes (data/winterheart/fixed_structures/placements.json, written by tools/worldpainter) and
 * how each template sits in the world (profiles.json). Both are re-read on /reload.
 */
public final class StructureConfig {
    public static final ResourceLocation PLACEMENTS = new ResourceLocation("winterheart", "fixed_structures/placements.json");
    public static final ResourceLocation PROFILES = new ResourceLocation("winterheart", "fixed_structures/profiles.json");
    public static final List<String> ROTATIONS = List.of("none", "clockwise_90", "180", "counterclockwise_90");

    public static final class Profile {
        public String anchor = "surface", foundation = "fill", groundBlock, foundationBlock;
        public int groundLayers = 0, foundationDepth = 4, waterline = 0, groundDepth = 0;
        public boolean clear = true;
        public int[] center;

        void apply(JsonObject o) {
            if (o.has("anchor")) anchor = o.get("anchor").getAsString();
            if (o.has("foundation")) foundation = o.get("foundation").getAsString();
            if (o.has("groundBlock")) groundBlock = o.get("groundBlock").getAsString();
            if (o.has("foundationBlock")) foundationBlock = o.get("foundationBlock").getAsString();
            if (o.has("groundLayers")) groundLayers = o.get("groundLayers").getAsInt();
            if (o.has("foundationDepth")) foundationDepth = o.get("foundationDepth").getAsInt();
            if (o.has("waterline")) waterline = o.get("waterline").getAsInt();
            if (o.has("groundDepth")) groundDepth = o.get("groundDepth").getAsInt();
            if (o.has("clear")) clear = o.get("clear").getAsBoolean();
            if (o.has("center")) center = new int[]{o.getAsJsonArray("center").get(0).getAsInt(), o.getAsJsonArray("center").get(1).getAsInt()};
        }
    }

    /** One structure to place: the template, the target X/Z (footprint centre), a rotation and its profile. */
    public static final class Placement {
        public final String template, rotation;
        public final int x, z;
        public final Profile profile;
        public int[] size;     // template size, filled in once the template is loaded

        Placement(String template, int x, int z, String rotation, Profile profile) {
            this.template = template; this.x = x; this.z = z; this.rotation = rotation; this.profile = profile;
        }

        public String key() { return "fixed_structure_placed:" + template + "@" + x + "," + z; }
    }

    public static volatile List<Placement> placements = List.of();
    public static volatile Map<String, int[]> legacyPlaced = Map.of();

    private StructureConfig() {}

    private record Loaded(JsonObject placements, JsonObject profiles) {}

    static void apply(JsonObject placementsJson, JsonObject profilesJson) {
        Profile base = new Profile();
        if (profilesJson.has("default")) base.apply(profilesJson.getAsJsonObject("default"));
        JsonObject templates = profilesJson.has("templates") ? profilesJson.getAsJsonObject("templates") : new JsonObject();

        List<Placement> list = new ArrayList<>();
        for (JsonElement e : placementsJson.getAsJsonArray("structures")) {
            JsonObject s = e.getAsJsonObject();
            String template = s.get("template").getAsString();
            String rotation = s.has("rotation") && !s.get("rotation").isJsonNull() ? s.get("rotation").getAsString() : "none";
            if (!ROTATIONS.contains(rotation)) {
                WinterheartStructures.LOGGER.error("[FixedStructures] {}: unknown rotation {}", template, rotation);
                continue;
            }
            Profile p = new Profile();
            p.apply(profilesJson.has("default") ? profilesJson.getAsJsonObject("default") : new JsonObject());
            if (templates.has(template)) p.apply(templates.getAsJsonObject(template));
            list.add(new Placement(template, s.get("x").getAsInt(), s.get("z").getAsInt(), rotation, p));
        }
        Map<String, int[]> legacy = new HashMap<>();
        if (profilesJson.has("legacy_placed")) {
            for (Map.Entry<String, JsonElement> e : profilesJson.getAsJsonObject("legacy_placed").entrySet()) {
                legacy.put(e.getKey(), new int[]{e.getValue().getAsJsonArray().get(0).getAsInt(), e.getValue().getAsJsonArray().get(1).getAsInt()});
            }
        }
        placements = list;
        legacyPlaced = legacy;
    }

    private static JsonObject read(ResourceManager m, ResourceLocation file) throws Exception {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(m.getResourceOrThrow(file).open(), StandardCharsets.UTF_8))) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    public static final class Loader extends SimplePreparableReloadListener<Loaded> {
        @Override
        protected Loaded prepare(ResourceManager manager, ProfilerFiller profiler) {
            try {
                return new Loaded(read(manager, PLACEMENTS), read(manager, PROFILES));
            } catch (Exception e) {
                WinterheartStructures.LOGGER.warn("[FixedStructures] could not read placements/profiles: {} (keeping the current ones)", e.toString());
                return null;
            }
        }

        @Override
        protected void apply(Loaded loaded, ResourceManager manager, ProfilerFiller profiler) {
            if (loaded == null) return;
            try {
                StructureConfig.apply(loaded.placements(), loaded.profiles());
                WinterheartStructures.LOGGER.info("[FixedStructures] loaded {} placement(s)", placements.size());
            } catch (RuntimeException e) {
                WinterheartStructures.LOGGER.error("[FixedStructures] bad placements/profiles: {}", e.toString());
            }
        }
    }
}
