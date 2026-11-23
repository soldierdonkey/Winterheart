package io.github.soldierdonkey2.winterheart_survival;

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
import java.util.List;
import java.util.function.Consumer;

/** A JSON file from a datapack, re-read on every /reload. Getters take a default for keys that are missing. */
public final class JsonConfig {
    private final ResourceLocation file;
    private volatile JsonObject json = new JsonObject();
    private final Consumer<JsonConfig> onLoad;

    public JsonConfig(ResourceLocation file, Consumer<JsonConfig> onLoad) {
        this.file = file;
        this.onLoad = onLoad;
    }

    public JsonObject root() { return json; }

    public double d(String key, double def) { JsonElement e = json.get(key); return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def; }
    public int i(String key, int def) { return (int) d(key, def); }
    public boolean b(String key, boolean def) { JsonElement e = json.get(key); return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def; }
    public String s(String key, String def) { JsonElement e = json.get(key); return e != null && e.isJsonPrimitive() ? e.getAsString() : def; }

    public List<String> strings(String key, List<String> def) {
        JsonElement e = json.get(key);
        if (e == null || !e.isJsonArray()) return def;
        List<String> out = new ArrayList<>();
        for (JsonElement x : (JsonArray) e) out.add(x.getAsString());
        return out;
    }

    public SimplePreparableReloadListener<JsonObject> listener() {
        return new SimplePreparableReloadListener<>() {
            @Override
            protected JsonObject prepare(ResourceManager manager, ProfilerFiller profiler) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(manager.getResourceOrThrow(file).open(), StandardCharsets.UTF_8))) {
                    return JsonParser.parseReader(r).getAsJsonObject();
                } catch (Exception e) {
                    WinterheartSurvival.LOGGER.warn("could not read {}: {} (keeping the current values)", file, e.toString());
                    return null;
                }
            }

            @Override
            protected void apply(JsonObject parsed, ResourceManager manager, ProfilerFiller profiler) {
                if (parsed == null) return;
                json = parsed;
                try { if (onLoad != null) onLoad.accept(JsonConfig.this); WinterheartSurvival.LOGGER.info("loaded {}", file); }
                catch (RuntimeException e) { WinterheartSurvival.LOGGER.error("bad {}: {}", file, e.toString()); }
            }
        };
    }
}
