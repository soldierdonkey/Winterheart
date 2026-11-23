package io.github.soldierdonkey2.winterheart_survival.limb;

import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.effect.ModEffects;
import io.github.soldierdonkey2.winterheart_hud.HudApi;
import io.github.soldierdonkey2.winterheart_survival.JsonConfig;
import io.github.soldierdonkey2.winterheart_survival.WinterheartSurvival;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Limb poising. Once a limb takes any damage (Legendary Survival Overhaul's per-limb HP drops below max) it is
 * "poised": every few ticks its health takes a small biased random step up or down instead of sitting still. The
 * lower a limb's health, the more the walk is skewed towards decay; above `high_health_threshold` it is skewed
 * towards healing. A limb stops being tracked once it is back to full. The effect `winterheart:limb_poising` (amplifier =
 * poised limbs) and the HUD's "Limb Status" section show it. A limb at ~0 HP is dead and ignored; a dead HEAD or CHEST
 * kills the player.
 *
 * Writes go through LSO's `/bodydamage <player> set <LIMB> <health>`: writing the capability NBT directly was
 * observed being silently reverted by LSO's routine packet sync. Reads use the capability NBT.
 * Tuning: data/winterheart/limb_poising/config.json.
 */
public final class LimbPoising {
    private record Limb(String key, String label) {}
    private static final List<Limb> LIMBS = List.of(new Limb("HEAD", "Head"), new Limb("CHEST", "Chest"), new Limb("LEFT_ARM", "Left Arm"),
            new Limb("RIGHT_ARM", "Right Arm"), new Limb("LEFT_LEG", "Left Leg"), new Limb("RIGHT_LEG", "Right Leg"),
            new Limb("LEFT_FOOT", "Left Foot"), new Limb("RIGHT_FOOT", "Right Foot"));
    private static final Map<String, String> TIER_STYLES = Map.of("stable", "accent", "strained", "normal", "wounded", "warning", "critical", "critical");

    private static final class LimbState {
        boolean active = true;
        String tier;
    }

    // in-memory, per player: reset on restart (as the script's state was on reload)
    private static final Map<UUID, Map<String, LimbState>> STATE = new HashMap<>();
    private static final Map<UUID, Set<String>> DEAD = new HashMap<>();

    public static final JsonConfig CONFIG = new JsonConfig(new ResourceLocation("winterheart", "limb_poising/config.json"), null);

    private LimbPoising() {}

    public static void init() {
        HudApi.addSection(LimbPoising::sidebarLines);
    }

    private static double d(String k, double def) { return CONFIG.d(k, def); }
    private static boolean debug() { return CONFIG.b("debug", false); }

    private static Map<String, LimbState> state(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new HashMap<>()); }
    private static Set<String> dead(ServerPlayer p) { return DEAD.computeIfAbsent(p.getUUID(), k -> new HashSet<>()); }

    private static CompoundTag cap(ServerPlayer p) {
        CompoundTag caps = p.saveWithoutId(new CompoundTag()).getCompound("ForgeCaps");
        String id = CONFIG.s("cap_id", "legendarysurvivaloverhaul:body_damage");
        return caps.contains(id, 10) ? caps.getCompound(id) : null;
    }

    private static double damage(CompoundTag cap, Limb l) { return cap.getDouble(l.key + "_damage"); }

    private static double maxHealth(CompoundTag cap, Limb l) { double m = cap.getDouble(l.key + "_maxHealth"); return m == 0 ? 8.0 : m; }

    private static MobEffect effect(String id) { return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(id)); }

    private static String tier(double pct) { return pct >= 0.8 ? "stable" : pct >= 0.5 ? "strained" : pct >= 0.25 ? "wounded" : "critical"; }

    private static void actionbar(ServerPlayer p, Component c) { p.displayClientMessage(c, true); }

    private static void markDead(ServerPlayer p, Limb limb) {
        if (!dead(p).add(limb.key)) return;
        state(p).remove(limb.key);
        if (debug()) WinterheartSurvival.LOGGER.info("[LimbPoise] {} {}: reached 0 HP, marked dead", p.getGameProfile().getName(), limb.key);
        if (CONFIG.b("notify_actionbar", true)) actionbar(p, Component.literal("Your " + limb.label + " has bled dry!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
    }

    private static void notifyLimb(ServerPlayer p, Limb limb, String tier, boolean healed) {
        if (!CONFIG.b("notify_actionbar", true)) return;
        String msg;
        ChatFormatting color;
        if (healed) { msg = limb.label + " has fully healed."; color = ChatFormatting.GREEN; }
        else switch (tier) {
            case "critical": msg = limb.label + " is critically wounded!"; color = ChatFormatting.DARK_RED; break;
            case "wounded": msg = limb.label + " is bleeding badly."; color = ChatFormatting.RED; break;
            case "strained": msg = limb.label + " is aching."; color = ChatFormatting.GOLD; break;
            default: msg = limb.label + " is recovering."; color = ChatFormatting.YELLOW; break;
        }
        actionbar(p, Component.literal(msg).withStyle(color, ChatFormatting.ITALIC));
    }

    private static void setLimbHealth(ServerPlayer p, String limbKey, double health) {
        Cmd.run(p.server, "bodydamage " + p.getGameProfile().getName() + " set " + limbKey + " " + String.format(Locale.ROOT, "%.4f", health));
    }

    /** One poise tick for one limb. Returns its health fraction if it is (still) poised, else null. */
    private static Double tickLimb(ServerPlayer p, CompoundTag cap, Limb limb) {
        if (dead(p).contains(limb.key)) return null;
        double damage = damage(cap, limb), maxHealth = maxHealth(cap, limb);
        Map<String, LimbState> st = state(p);
        if (maxHealth <= 0) return null;

        if (damage <= 0) {
            LimbState s = st.get(limb.key);
            if (s != null && s.active) notifyLimb(p, limb, "stable", true);
            st.remove(limb.key);
            return null;
        }

        double pct = Math.max(0, Math.min(1, 1 - damage / maxHealth));
        double decayWeight = d("base_decay_weight", 1.0) + (1 - pct) * d("low_health_decay_skew", 2.5);
        double healWeight = d("base_heal_weight", 0.1);
        if (pct >= d("high_health_threshold", 0.8)) healWeight += d("high_health_heal_bias", 5.5);

        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean decaying = r.nextDouble() * (decayWeight + healWeight) < decayWeight;
        double minStep = d("min_step_percent", 0.0005), maxStep = d("max_step_percent", 0.001);
        double step = maxHealth * (minStep + r.nextDouble() * (maxStep - minStep));

        double newDamage = Math.max(0, Math.min(maxHealth, damage + (decaying ? step : -step)));
        setLimbHealth(p, limb.key, maxHealth - newDamage);

        if (newDamage >= maxHealth * (1 - d("death_health_buffer", 0.02))) { markDead(p, limb); return null; }

        double newPct = Math.max(0, Math.min(1, 1 - newDamage / maxHealth));
        String newTier = tier(newPct);
        if (debug()) WinterheartSurvival.LOGGER.info("[LimbPoise] {} {}: hp%={} {} step={} newHp%={} tier={}", p.getGameProfile().getName(), limb.key, Math.round(pct * 1000) / 10.0, decaying ? "DECAY" : "HEAL", step, Math.round(newPct * 1000) / 10.0, newTier);

        boolean visible = newPct < d("visible_health_threshold", 0.8);
        LimbState s = st.get(limb.key);
        if (s == null) {
            s = new LimbState();
            s.tier = newTier;
            st.put(limb.key, s);
            if (visible) notifyLimb(p, limb, newTier, false);
        } else if (!newTier.equals(s.tier)) {
            s.tier = newTier;
            if (visible) notifyLimb(p, limb, newTier, false);
        }
        if (newDamage <= 0) { notifyLimb(p, limb, "stable", true); st.remove(limb.key); return null; }
        return newPct;
    }

    private static void applyEffect(ServerPlayer p, int count) {
        MobEffect e = ModEffects.LIMB_POISING.get();
        if (count <= 0) { if (p.hasEffect(e)) p.removeEffect(e); return; }
        int amp = Math.min(count - 1, CONFIG.i("max_effect_amplifier", 3));
        p.addEffect(new MobEffectInstance(e, CONFIG.i("effect_refresh_duration", 60), amp, true, false));
    }

    /** The HUD's "Limb Status" section: poised limbs below the visible threshold. */
    private static List<HudApi.Line> sidebarLines(ServerPlayer p) {
        if (!CONFIG.b("sidebar_enabled", true)) return List.of();
        CompoundTag cap = cap(p);
        if (cap == null) return List.of();
        List<HudApi.Line> lines = new ArrayList<>();
        for (Limb limb : LIMBS) {
            LimbState s = state(p).get(limb.key);
            if (s == null || !s.active) continue;
            double pct = Math.max(0, Math.min(1, 1 - damage(cap, limb) / maxHealth(cap, limb)));
            if (pct >= d("visible_health_threshold", 0.8)) continue;
            lines.add(new HudApi.Line(TIER_STYLES.getOrDefault(s.tier, "normal"), limb.label + ": " + Math.round(pct * 100) + "%"));
        }
        if (lines.isEmpty()) return List.of();
        List<HudApi.Line> out = new ArrayList<>();
        out.add(new HudApi.Line("spacer", ""));
        out.add(new HudApi.Line("section", "-- Limb Status --"));
        out.addAll(lines);
        return out;
    }

    /** Slowly restores main health while every limb (dead ones count as 0%) is above `body_regen_threshold`. */
    private static void bodyRegen(ServerPlayer p) {
        if (!p.isAlive() || p.getHealth() >= p.getMaxHealth()) return;
        CompoundTag cap = cap(p);
        if (cap == null) return;
        for (Limb limb : LIMBS) {
            double max = cap.getDouble(limb.key + "_maxHealth");
            if (max <= 0) continue;
            double pct = 1 - damage(cap, limb) / max;
            if (dead(p).contains(limb.key) || !(pct > d("body_regen_threshold", 0.8))) return;
        }
        p.heal((float) d("body_regen_per_tick", 0.025));
    }

    /** One poise step for a player. Public so tests can drive it. */
    public static void poiseTick(ServerPlayer p) {
        CompoundTag cap = cap(p);
        if (cap == null) return;
        MobEffect recovery = effect(CONFIG.s("recovery_effect_id", "legendarysurvivaloverhaul:recovery"));
        // while Recovery is active, poising is suspended: no steps, the effect is dropped, tracked state is kept
        if (recovery != null && p.hasEffect(recovery)) { applyEffect(p, 0); bodyRegen(p); return; }
        int visible = 0;
        for (Limb limb : LIMBS) {
            Double pct = tickLimb(p, cap, limb);
            if (pct != null && pct < d("visible_health_threshold", 0.8)) visible++;
        }
        applyEffect(p, visible);
        bodyRegen(p);
    }

    /** Vital limbs are checked more often than the poise tick, so death is quick whatever damaged them. Returns true if it killed. */
    public static boolean vitalCheck(ServerPlayer p) {
        CompoundTag cap = cap(p);
        if (cap == null) return false;
        for (String key : CONFIG.strings("vital_limbs", List.of("HEAD", "CHEST"))) {
            Limb limb = LIMBS.stream().filter(l -> l.key.equals(key)).findFirst().orElse(null);
            if (limb == null) continue;
            double max = cap.getDouble(key + "_maxHealth");
            double remaining = max - damage(cap, limb);
            if (dead(p).contains(key) || max <= 0 || remaining > max * d("death_health_buffer", 0.02)) continue;
            markDead(p, limb);
            WinterheartSurvival.LOGGER.info("[LimbPoise] {}: vital limb {} destroyed, killing player", p.getGameProfile().getName(), key);
            // player.kill() leaves LSO's body_damage capability in a broken (NaN) state; real lethal damage goes through the death event
            Cmd.run(p.server, "damage " + p.getGameProfile().getName() + " 1000 " + CONFIG.s("death_damage_type", "minecraft:generic"));
            return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer p)) return;
        if (!CONFIG.b("enabled", true)) return;
        if (p.tickCount % CONFIG.i("tick_interval", 5) == 0) poiseTick(p);
        if (p.tickCount % CONFIG.i("death_check_interval", 2) == 0) vitalCheck(p);
    }
}
