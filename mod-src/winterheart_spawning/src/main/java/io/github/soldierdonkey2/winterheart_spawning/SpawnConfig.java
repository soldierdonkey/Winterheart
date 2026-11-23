package io.github.soldierdonkey2.winterheart_spawning;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The spawner's tuning, from data/winterheart/spawning/config.json (Paxi pack winterheart_spawning), re-read on /reload. */
public final class SpawnConfig {
    public static final ResourceLocation FILE = new ResourceLocation("winterheart", "spawning/config.json");

    /** A mob the spawner can place: its registry id and the footprint it needs (1 or 2 wide, `height` tall). */
    public record Template(String key, String id, String name, int width, int height, boolean baby) {}

    public static volatile boolean debugVerbose = true, auditLog = true;
    public static volatile int surfaceY = 60, despawnRadius = 80, maxSpawnLight = 7;
    public static volatile int cap1 = 0, cap11 = 6, cap21 = 12, cap31 = 20, cap41 = 32;
    public static volatile int quota1 = 80, quota11 = 20, quota21 = 10;
    public static volatile List<String> hostileSurface = List.of(), hostileCave = List.of(), livestockPool = List.of();
    public static volatile Map<String, Template> mobs = Map.of(), livestock = Map.of();
    public static volatile List<Integer> wendigoDays = List.of(20, 30, 35, 40, 43, 46, 50);
    public static volatile List<String> wendigoTypes = List.of("antlers:wendigo", "antlers:wendigomad", "antlers:wendigo_flute");
    public static volatile List<String> blockedSpawnTypes = List.of("natural", "chunk_generation", "structure", "spawner", "patrol", "reinforcement");

    private SpawnConfig() {}

    private static List<String> strings(JsonObject o, String k, List<String> def) {
        if (!o.has(k)) return def;
        List<String> l = new ArrayList<>();
        o.getAsJsonArray(k).forEach(e -> l.add(e.getAsString()));
        return l;
    }

    private static Map<String, Template> templates(JsonObject o, String k) {
        Map<String, Template> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(k).entrySet()) {
            JsonObject t = e.getValue().getAsJsonObject();
            m.put(e.getKey(), new Template(e.getKey(), t.get("id").getAsString(), t.get("name").getAsString(), t.get("width").getAsInt(), t.get("height").getAsInt(), t.has("baby") && t.get("baby").getAsBoolean()));
        }
        return m;
    }

    static void apply(JsonObject o) {
        if (o.has("debug_verbose")) debugVerbose = o.get("debug_verbose").getAsBoolean();
        if (o.has("audit_log")) auditLog = o.get("audit_log").getAsBoolean();
        if (o.has("surface_y")) surfaceY = o.get("surface_y").getAsInt();
        if (o.has("despawn_radius")) despawnRadius = o.get("despawn_radius").getAsInt();
        if (o.has("max_spawn_light")) maxSpawnLight = o.get("max_spawn_light").getAsInt();
        if (o.has("hostile_caps")) {
            JsonObject c = o.getAsJsonObject("hostile_caps");
            cap1 = c.get("days_1_10").getAsInt(); cap11 = c.get("days_11_20").getAsInt(); cap21 = c.get("days_21_30").getAsInt();
            cap31 = c.get("days_31_40").getAsInt(); cap41 = c.get("days_41_50").getAsInt();
        }
        if (o.has("livestock_quotas")) {
            JsonObject q = o.getAsJsonObject("livestock_quotas");
            quota1 = q.get("days_1_10").getAsInt(); quota11 = q.get("days_11_20").getAsInt(); quota21 = q.get("days_21_30").getAsInt();
        }
        hostileSurface = strings(o, "hostile_pool_surface", hostileSurface);
        hostileCave = strings(o, "hostile_pool_cave", hostileCave);
        livestockPool = strings(o, "livestock_pool", livestockPool);
        if (o.has("mob_templates")) mobs = templates(o, "mob_templates");
        if (o.has("livestock_templates")) livestock = templates(o, "livestock_templates");
        if (o.has("wendigo")) {
            JsonObject w = o.getAsJsonObject("wendigo");
            if (w.has("days")) { List<Integer> d = new ArrayList<>(); w.getAsJsonArray("days").forEach(e -> d.add(e.getAsInt())); wendigoDays = d; }
            wendigoTypes = strings(w, "types", wendigoTypes);
            blockedSpawnTypes = strings(w, "blocked_spawn_types", blockedSpawnTypes);
        }
    }

    public static int hostileCap(int day) {
        return day >= 41 ? cap41 : day >= 31 ? cap31 : day >= 21 ? cap21 : day >= 11 ? cap11 : cap1;
    }

    public static int livestockQuota(int day) {
        return day >= 31 ? 0 : day >= 21 ? quota21 : day >= 11 ? quota11 : quota1;
    }

    public static final class Loader extends SimplePreparableReloadListener<JsonObject> {
        @Override
        protected JsonObject prepare(ResourceManager manager, ProfilerFiller profiler) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(manager.getResourceOrThrow(FILE).open(), StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                WinterheartSpawning.LOGGER.warn("[Spawner] could not read {}: {} (keeping the current values)", FILE, e.toString());
                return null;
            }
        }

        @Override
        protected void apply(JsonObject json, ResourceManager manager, ProfilerFiller profiler) {
            if (json == null) return;
            try { SpawnConfig.apply(json); WinterheartSpawning.LOGGER.info("[Spawner] loaded {}", FILE); }
            catch (RuntimeException e) { WinterheartSpawning.LOGGER.error("[Spawner] bad {}: {}", FILE, e.toString()); }
        }
    }
}
