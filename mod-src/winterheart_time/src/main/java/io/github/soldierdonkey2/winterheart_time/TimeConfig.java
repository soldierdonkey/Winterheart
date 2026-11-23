package io.github.soldierdonkey2.winterheart_time;

import com.google.gson.JsonArray;
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

/**
 * Tuning for the time system, read from data/winterheart/time/config.json (Paxi pack winterheart_time) and applied on
 * every /reload. Missing keys keep the defaults below.
 */
public final class TimeConfig {
    public static final ResourceLocation FILE = new ResourceLocation("winterheart", "time/config.json");

    public static volatile boolean debug = false;
    public static volatile int debugInterval = 200;
    public static volatile int eternalNightDay = 50;
    public static volatile int sleepCooldown = 200;

    public static volatile boolean wakeEnabled = true;
    public static volatile String recoveryEffect = "legendarysurvivaloverhaul:recovery";
    public static volatile int recoveryDuration = 700;
    public static volatile int recoveryAmplifier = 0;
    public static volatile String regenEffect = "minecraft:regeneration";
    public static volatile int regenAmplifier = 0;
    public static volatile double regenHealFraction = 0.5;
    public static volatile int regenTicksPerHp = 50;

    public static volatile boolean drainEnabled = true;
    public static volatile double drainBaseRate = 7.5;
    public static volatile double drainDoublingMinutes = 2;
    public static volatile double drainMaxRate = 50;

    public static volatile double[] daySpeed = {0.6, 2.8};
    public static volatile double[] nightSpeed = {1.2, 0.4};

    public static volatile double dawn = 0, noon = 6000, afternoon = 9000, dusk = 12542, night = 14000, midnight = 18000;

    public static volatile Map<String, String> retainedCaps = defaultCaps();
    public static volatile List<String> deathSplash = List.of("FOCUS", "SURVIVE", "OUTLAST", "PERSIST", "PROLIFERATE");

    private TimeConfig() {}

    private static Map<String, String> defaultCaps() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("body_damage_", "legendarysurvivaloverhaul:body_damage");
        m.put("temperature_", "legendarysurvivaloverhaul:temperature");
        m.put("thirst_", "legendarysurvivaloverhaul:thirst");
        m.put("health_", "legendarysurvivaloverhaul:health");
        m.put("wetness_", "legendarysurvivaloverhaul:wetness");
        m.put("food_", "legendarysurvivaloverhaul:food");
        m.put("radiation_", "nuclearcraft:radiation");
        m.put("gore_variables_", "gore_edition:player_variables");
        return m;
    }

    private static double[] pair(JsonObject o, String k, double[] def) {
        if (!o.has(k) || !o.get(k).isJsonArray() || o.getAsJsonArray(k).size() != 2) return def;
        JsonArray a = o.getAsJsonArray(k);
        return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
    }

    static void apply(JsonObject o) {
        if (o.has("debug")) debug = o.get("debug").getAsBoolean();
        if (o.has("debug_interval")) debugInterval = o.get("debug_interval").getAsInt();
        if (o.has("eternal_night_day")) eternalNightDay = o.get("eternal_night_day").getAsInt();
        if (o.has("sleep_cooldown")) sleepCooldown = o.get("sleep_cooldown").getAsInt();
        if (o.has("wake_up")) {
            JsonObject w = o.getAsJsonObject("wake_up");
            if (w.has("enabled")) wakeEnabled = w.get("enabled").getAsBoolean();
            if (w.has("recovery_effect")) recoveryEffect = w.get("recovery_effect").getAsString();
            if (w.has("recovery_duration")) recoveryDuration = w.get("recovery_duration").getAsInt();
            if (w.has("recovery_amplifier")) recoveryAmplifier = w.get("recovery_amplifier").getAsInt();
            if (w.has("regen_effect")) regenEffect = w.get("regen_effect").getAsString();
            if (w.has("regen_amplifier")) regenAmplifier = w.get("regen_amplifier").getAsInt();
            if (w.has("regen_heal_fraction")) regenHealFraction = w.get("regen_heal_fraction").getAsDouble();
            if (w.has("regen_ticks_per_hp")) regenTicksPerHp = w.get("regen_ticks_per_hp").getAsInt();
        }
        if (o.has("midnight_drain")) {
            JsonObject m = o.getAsJsonObject("midnight_drain");
            if (m.has("enabled")) drainEnabled = m.get("enabled").getAsBoolean();
            if (m.has("base_rate")) drainBaseRate = m.get("base_rate").getAsDouble();
            if (m.has("doubling_minutes")) drainDoublingMinutes = m.get("doubling_minutes").getAsDouble();
            if (m.has("max_rate")) drainMaxRate = m.get("max_rate").getAsDouble();
        }
        if (o.has("speed_intervals")) {
            JsonObject s = o.getAsJsonObject("speed_intervals");
            daySpeed = pair(s, "day", daySpeed);
            nightSpeed = pair(s, "night", nightSpeed);
        }
        if (o.has("phases")) {
            JsonObject p = o.getAsJsonObject("phases");
            if (p.has("DAWN")) dawn = p.get("DAWN").getAsDouble();
            if (p.has("NOON")) noon = p.get("NOON").getAsDouble();
            if (p.has("AFTERNOON")) afternoon = p.get("AFTERNOON").getAsDouble();
            if (p.has("DUSK")) dusk = p.get("DUSK").getAsDouble();
            if (p.has("NIGHT")) night = p.get("NIGHT").getAsDouble();
            if (p.has("MIDNIGHT")) midnight = p.get("MIDNIGHT").getAsDouble();
        }
        if (o.has("retained_caps")) {
            Map<String, String> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("retained_caps").entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            retainedCaps = m;
        }
        if (o.has("death_splash")) {
            List<String> l = new ArrayList<>();
            o.getAsJsonArray("death_splash").forEach(e -> l.add(e.getAsString()));
            if (!l.isEmpty()) deathSplash = l;
        }
    }

    /** Re-reads config.json on every datapack reload. */
    public static final class Loader extends SimplePreparableReloadListener<JsonObject> {
        @Override
        protected JsonObject prepare(ResourceManager manager, ProfilerFiller profiler) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(manager.getResourceOrThrow(FILE).open(), StandardCharsets.UTF_8))) {
                return JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                WinterheartTime.LOGGER.warn("[TimeSys] could not read {}: {} (keeping the current values)", FILE, e.toString());
                return null;
            }
        }

        @Override
        protected void apply(JsonObject json, ResourceManager manager, ProfilerFiller profiler) {
            if (json == null) return;
            try {
                TimeConfig.apply(json);
                WinterheartTime.LOGGER.info("[TimeSys] loaded {}", FILE);
            } catch (RuntimeException e) {
                WinterheartTime.LOGGER.error("[TimeSys] bad {}: {}", FILE, e.toString());
            }
        }
    }
}
