package io.github.soldierdonkey2.winterheart_survival.proximity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.api.Rand;
import io.github.soldierdonkey2.winterheart.api.Sanity;
import io.github.soldierdonkey2.winterheart.api.Scheduler;
import io.github.soldierdonkey2.winterheart.api.Temperature;
import io.github.soldierdonkey2.winterheart.api.Titles;
import io.github.soldierdonkey2.winterheart_guides.GuideApi;
import io.github.soldierdonkey2.winterheart_survival.JsonConfig;
import io.github.soldierdonkey2.winterheart_survival.WinterheartSurvival;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Proximity effects. Every 5 seconds the server scans a sphere around each player for blocks matching the rules in
 * data/winterheart/proximity/rules.json. A rule seen on two scans in a row fires: `GuideApi.fireProximity`, then its
 * one-time `on_once` actions (until the player leaves its range) and its repeating `on_repeat` actions. Scan results
 * are kept in the player's persistent data (`sweep_last_blocks`, `sweep_triggered_once`).
 */
public final class Proximity {
    private static final String KEY_LAST = "sweep_last_blocks", KEY_ONCE = "sweep_triggered_once";
    private static final int SCAN_INTERVAL = 100;

    private record Phase(int minDelay, int maxDelay, List<JsonObject> actions) {}
    private record Rule(String id, List<String> overtakenBy, String tag, List<String> blocks, boolean level, Phase onOnce, Phase onRepeat) {}

    private static volatile List<Rule> rules = List.of();
    private static volatile int radius = 5;
    private static volatile boolean debug = false;

    public static final JsonConfig CONFIG = new JsonConfig(new ResourceLocation("winterheart", "proximity/rules.json"), Proximity::load);

    private Proximity() {}

    private static List<String> strings(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        if (o.has(k)) for (JsonElement e : o.getAsJsonArray(k)) out.add(e.getAsString());
        return out;
    }

    private static Phase phase(JsonObject o, String k) {
        if (!o.has(k)) return null;
        JsonObject p = o.getAsJsonObject(k);
        int min = 0, max = 0;
        if (p.has("delay")) { JsonArray d = p.getAsJsonArray("delay"); min = d.get(0).getAsInt(); max = d.get(1).getAsInt(); }
        List<JsonObject> actions = new ArrayList<>();
        for (JsonElement e : p.getAsJsonArray("actions")) actions.add(e.getAsJsonObject());
        return new Phase(min, max, actions);
    }

    private static void load(JsonConfig c) {
        List<Rule> out = new ArrayList<>();
        for (JsonElement e : c.root().getAsJsonArray("rules")) {
            JsonObject r = e.getAsJsonObject();
            out.add(new Rule(r.get("id").getAsString(), strings(r, "overtaken_by"), r.has("tag") ? r.get("tag").getAsString() : null,
                    strings(r, "blocks"), r.has("level") && r.get("level").getAsBoolean(), phase(r, "on_once"), phase(r, "on_repeat")));
        }
        radius = c.i("radius", 5);
        debug = c.b("debug", false);
        rules = out;
    }

    private static boolean hasTag(BlockState s, String tag) {
        try { return s.is(TagKey.create(Registries.BLOCK, new ResourceLocation(tag.startsWith("#") ? tag.substring(1) : tag))); }
        catch (RuntimeException e) { return false; }
    }

    private static boolean matches(BlockState s, String id, Rule rule) {
        if (rule.tag != null && hasTag(s, rule.tag)) return true;
        for (String target : rule.blocks) {
            if (target.startsWith("#") ? hasTag(s, target) : target.equals(id)) return true;
        }
        return false;
    }

    private static boolean overtaken(Rule rule, List<String> current) {
        for (String o : rule.overtakenBy) if (current.contains(o)) return true;
        return false;
    }

    /** Rule ids present within the radius of the player (a sphere), at most one hit per rule. */
    public static List<String> scan(ServerPlayer player) {
        List<Rule> rs = rules;
        Level level = player.level();
        int px = (int) Math.floor(player.getX()), py = (int) Math.floor(player.getY()), pz = (int) Math.floor(player.getZ());
        int radiusSq = radius * radius;
        List<String> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int dxy = dx * dx + dy * dy;
                if (dxy > radiusSq) continue;
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dxy + dz * dz > radiusSq) continue;
                    BlockState s = level.getBlockState(pos.set(px + dx, py + dy, pz + dz));
                    if (s.isAir()) continue;
                    String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(s.getBlock()));
                    for (Rule rule : rs) {
                        if (found.contains(rule.id) || !matches(s, id, rule)) continue;
                        if (rule.level && dy < -1) continue;
                        found.add(rule.id);
                        if (found.size() >= rs.size()) return found;
                    }
                }
            }
        }
        return found;
    }

    private static List<String> readList(CompoundTag tag, String key) {
        List<String> out = new ArrayList<>();
        ListTag l = tag.getList(key, 8);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    private static void writeList(CompoundTag tag, String key, List<String> values) {
        ListTag l = new ListTag();
        values.forEach(v -> l.add(StringTag.valueOf(v)));
        tag.put(key, l);
    }

    /** One scan for one player: compares with the last scan and fires the rules that were seen twice in a row. */
    public static void sweep(ServerPlayer player) {
        List<String> current = scan(player);
        CompoundTag data = player.getPersistentData();
        List<String> last = readList(data, KEY_LAST), once = readList(data, KEY_ONCE);
        for (Rule rule : rules) {
            if (current.contains(rule.id)) {
                if (overtaken(rule, current)) continue;
                if (last.contains(rule.id)) {
                    if (debug) WinterheartSurvival.LOGGER.info("[Proximity] {}: {} confirmed", player.getGameProfile().getName(), rule.id);
                    GuideApi.fireProximity(player, rule.id);
                    if (rule.onOnce != null && !once.contains(rule.id)) { run(player, rule.onOnce); once.add(rule.id); }
                    if (rule.onRepeat != null) run(player, rule.onRepeat);
                }
            } else if (last.contains(rule.id) || once.contains(rule.id)) {
                once.remove(rule.id);   // left range: the one-time effect can fire again
            }
        }
        writeList(data, KEY_LAST, current);
        writeList(data, KEY_ONCE, once);
    }

    // ---- actions

    private static void run(ServerPlayer player, Phase phase) {
        Runnable all = () -> { for (JsonObject a : phase.actions) runAction(player, a); };
        if (phase.maxDelay > 0) Scheduler.runLaterRandom(player.server, phase.minDelay, phase.maxDelay, all);
        else all.run();
    }

    private static void runAction(ServerPlayer player, JsonObject a) {
        try {
            if (a.has("chance") && !Rand.chance(a.get("chance").getAsDouble())) return;
            if (a.has("sanity")) Sanity.add(player, a.get("sanity").getAsDouble());
            if (a.has("temperature")) Temperature.add(player, a.get("temperature").getAsDouble());
            if (a.has("effect")) {
                JsonObject e = a.getAsJsonObject("effect");
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(e.get("id").getAsString()));
                int duration;
                if (e.get("duration").isJsonArray()) {
                    JsonArray list = e.getAsJsonArray("duration");
                    duration = list.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(list.size())).getAsInt();
                } else duration = e.get("duration").getAsInt();
                if (effect != null) player.addEffect(new MobEffectInstance(effect, duration, e.has("amplifier") ? e.get("amplifier").getAsInt() : 0));
            }
            if (a.has("title")) {
                JsonObject t = a.getAsJsonObject("title");
                List<String> msgs = new ArrayList<>();
                t.getAsJsonArray("messages").forEach(m -> msgs.add(m.getAsString()));
                Titles.show(player, Rand.pick(msgs), t.has("bold") && t.get("bold").getAsBoolean(), t.has("italic") && t.get("italic").getAsBoolean());
            }
            if (a.has("command")) Cmd.run(player.server, a.get("command").getAsString().replace("{player}", player.getGameProfile().getName()));
        } catch (RuntimeException e) {
            WinterheartSurvival.LOGGER.error("[Proximity] action failed: {}", a, e);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % SCAN_INTERVAL != 0) return;
        sweep(p);
    }
}
