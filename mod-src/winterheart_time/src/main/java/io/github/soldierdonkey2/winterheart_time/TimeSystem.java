package io.github.soldierdonkey2.winterheart_time;

import io.github.soldierdonkey2.winterheart.api.CapBackup;
import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.api.Numbers;
import io.github.soldierdonkey2.winterheart.api.Rand;
import io.github.soldierdonkey2.winterheart.api.Sanity;
import io.github.soldierdonkey2.winterheart.api.Scheduler;
import io.github.soldierdonkey2.winterheart.api.TestMode;
import io.github.soldierdonkey2.winterheart.api.Ticks;
import io.github.soldierdonkey2.winterheart.world.WorldState;
import io.github.soldierdonkey2.winterheart_guides.GuideApi;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The custom day/night cycle. The vanilla cycle is off; this advances `exact_time` at a speed that depends on the day
 * (days get shorter and nights longer up to the eternal night), counts days, lets sleeping pass the day, drains sanity
 * after midnight, and rewinds to morning when a player dies (MineZero revives them with the `unluck` effect).
 * State is in the world state under the same keys the KubeJS version used: custom_day, exact_time, current_phase,
 * sleep_cooldown, death_cooldown, death_count, midnight_ticks, previous_sanity_<uuid>, has_loaded<uuid>, ...
 */
public final class TimeSystem {
    private TimeSystem() {}

    private static CompoundTag data(MinecraftServer server) { return WorldState.tag(server); }

    private static void save(MinecraftServer server) { WorldState.save(server); }

    private static int day(CompoundTag d) { int v = d.getInt("custom_day"); return v == 0 ? 1 : v; }

    /** `time set`: every dimension's day time. */
    static void setTime(MinecraftServer server, long ticks) {
        for (ServerLevel l : server.getAllLevels()) l.setDayTime(ticks);
    }

    private static MobEffect effect(String id) { return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(id)); }

    private static double scaledSpeed(int day, double[] interval) {
        double factor = Math.min(1.0, Math.max(0.0, (day - 1.0) / (TimeConfig.eternalNightDay - 1.0)));
        return interval[0] + factor * (interval[1] - interval[0]);
    }

    /** Recovery (also pauses limb poising) and regeneration after waking up. */
    private static void applyWakeUpEffects(ServerPlayer player) {
        if (!TimeConfig.wakeEnabled) return;
        int regenDuration = (int) Math.ceil(player.getMaxHealth() * TimeConfig.regenHealFraction * TimeConfig.regenTicksPerHp);
        MobEffect recovery = effect(TimeConfig.recoveryEffect), regen = effect(TimeConfig.regenEffect);
        if (recovery != null) player.addEffect(new MobEffectInstance(recovery, TimeConfig.recoveryDuration, TimeConfig.recoveryAmplifier, false, true));
        if (regen != null) player.addEffect(new MobEffectInstance(regen, regenDuration, TimeConfig.regenAmplifier, false, true));
    }

    /** Past midnight, sanity drains at an exponentially growing rate (called every tick). */
    private static void applyMidnightDrain(ServerLevel level, CompoundTag d) {
        if (!TimeConfig.drainEnabled) return;
        double ticks = d.getDouble("midnight_ticks") + 1;
        d.putDouble("midnight_ticks", ticks);
        if (ticks % 20 != 0) return;
        double minutes = ticks / 1200;
        double rate = TimeConfig.drainBaseRate * Math.pow(2, minutes / TimeConfig.drainDoublingMinutes);
        if (TimeConfig.drainMaxRate > 0) rate = Math.min(rate, TimeConfig.drainMaxRate);
        double drain = rate / 60;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative()) continue;
            Sanity.set(p, Sanity.get(p) - drain);
        }
    }

    // ---- checkpoint / death loop

    /** The wake-up checkpoint: bumps sanity, remembers it and the retained capabilities, and tells MineZero. */
    static void saveState(ServerPlayer player, MinecraftServer server) {
        CompoundTag d = data(server);
        d.putBoolean("registering_checkpoint", true);
        Sanity.set(player, Sanity.get(player) + 30);
        d.putDouble("previous_sanity_" + player.getUUID(), Sanity.get(player));
        for (Map.Entry<String, String> e : TimeConfig.retainedCaps.entrySet()) CapBackup.save(player, e.getValue(), e.getKey() + player.getUUID() + "_");
        int currentDay = day(d);
        String name = player.getGameProfile().getName();
        MobEffect unluck = MobEffects.UNLUCK;
        player.addEffect(new MobEffectInstance(unluck, 100, 60, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false));
        Cmd.run(server, "execute as " + name + " run setcheckpoint @s");
        Cmd.run(server, "execute as " + name + " run setSubaruPlayer @s");
        player.sendSystemMessage(Component.literal("You wake up. Day " + Numbers.toText(currentDay) + " begins...").withStyle(ChatFormatting.GOLD));
        Scheduler.runLater(server, 5, () -> {
            player.removeEffect(unluck);
            data(server).putBoolean("registering_checkpoint", false);
            save(server);
        });
        save(server);
        WinterheartTime.LOGGER.info("[TimeSys] Player {} slept through the night. Day {} begins.", name, Numbers.toText(currentDay));
    }

    static void handleDeathLoopReset(MinecraftServer server, ServerPlayer player) {
        ServerLevel overworld = server.overworld();
        CompoundTag d = data(server);
        int day = day(d);
        if (d.getInt("death_cooldown") > 0) return;

        int deathCount = d.getInt("death_count") + 1;
        d.putInt("death_count", deathCount);
        d.putInt("death_cooldown", 40);
        WinterheartTime.LOGGER.info("[TimeSys] Player {} died on day {}. Resetting timeline to morning...", player.getGameProfile().getName(), day);

        if (day < TimeConfig.eternalNightDay) {
            d.putDouble("exact_time", 0.0);
            d.putString("current_phase", "DAWN");
            d.putInt("sleep_cooldown", TimeConfig.sleepCooldown);
            setTime(server, 0);
            triggerTimeHook(overworld, day, "DAWN", true);
        } else {
            setTime(server, (long) TimeConfig.midnight);
        }

        String splash = Rand.pick(TimeConfig.deathSplash);
        String msg = Numbers.ominousMessage(deathCount);
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
            String name = p.getGameProfile().getName();
            if (day == 1) Cmd.run(server, "starterkit give " + name);
            p.sendSystemMessage(Component.literal("☠ ").withStyle(ChatFormatting.DARK_RED).append(Component.literal(msg).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)));
            Cmd.run(server, "execute as " + name + " run playSound minecraft:entity.warden.heartbeat master 1.0 0.6");
            double newSanity = Math.max(d.getDouble("previous_sanity_" + p.getUUID()) - 10, 25);
            Sanity.set(p, newSanity);
            d.putDouble("previous_sanity_" + p.getUUID(), newSanity);
            for (Map.Entry<String, String> e : TimeConfig.retainedCaps.entrySet()) {
                WinterheartTime.LOGGER.info("[TimeSys] Loading {} for player {} after death.", e.getValue(), name);
                CapBackup.load(p, e.getValue(), e.getKey() + p.getUUID() + "_");
            }
        }
        Cmd.run(server, "title @a times 10 60 20");
        Cmd.run(server, "title @a title {\"text\":\"" + splash + "\",\"color\":\"dark_red\",\"bold\":true}");
        Cmd.run(server, "title @a subtitle {\"text\":\"" + msg + "\",\"color\":\"gray\",\"italic\":true}");
        player.removeEffect(MobEffects.UNLUCK);
        save(server);
    }

    // ---- phase hooks

    /** Called every tick with phase change awareness. */
    static void triggerTimeHook(ServerLevel level, int day, String phase, boolean phaseChange) {
        MinecraftServer server = level.getServer();
        if (phaseChange) triggerPhaseChangeHook(server, day, phase);
        boolean tenSeconds = Ticks.everyTenSeconds();
        switch (phase) {
            case "DAWN": if (tenSeconds) Cmd.run(server, "weather clear"); break;
            case "NOON": Cmd.run(server, day > 30 && tenSeconds ? "weather rain" : "weather clear"); break;
            case "AFTERNOON":
                if (tenSeconds) Cmd.run(server, day > 20 ? "weather rain" : "weather clear");
                break;
            case "ETERNAL_NIGHT": if (tenSeconds) Cmd.run(server, "weather thunder"); break;
            default: break;
        }
    }

    /** Called once, at the moment a phase begins. */
    static void triggerPhaseChangeHook(MinecraftServer server, int day, String phase) {
        GuideApi.firePhase(server, day, phase);   // 'time_phase' guide triggers
        switch (phase) {
            case "NIGHT": showTitle(server, "NIGHT", "It's getting late...", "gray"); break;
            case "MIDNIGHT": showTitle(server, "MIDNIGHT", "You should go to sleep...", "#8A0303"); break;
            default: break;
        }
    }

    private static void showTitle(MinecraftServer server, String title, String subtitle, String color) {
        Cmd.run(server, "title @a times 20 80 40");
        Cmd.run(server, "title @a subtitle {\"text\":\"" + subtitle + "\",\"color\":\"" + color + "\",\"italic\":true}");
        Cmd.run(server, "title @a title {\"text\":\"" + title + "\",\"color\":\"" + color + "\",\"bold\":true}");
    }

    // ---- the engine

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide || event.level.dimension() != Level.OVERWORLD) return;
        if (TestMode.enabled()) return;   // /toggle_test
        tick((ServerLevel) event.level);
    }

    /** One tick of the time engine (the overworld's). Public so tests can drive it. */
    public static void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        CompoundTag d = data(server);
        if (!d.contains("custom_day")) d.putInt("custom_day", 1);
        if (!d.contains("death_count")) d.putInt("death_count", 0);
        if (!d.contains("exact_time")) d.putDouble("exact_time", 0.0);
        if (!d.contains("current_phase")) d.putString("current_phase", "DAWN");
        if (!d.contains("debug_timer")) d.putInt("debug_timer", 0);
        if (!d.contains("sleep_cooldown")) d.putInt("sleep_cooldown", 0);
        if (!d.contains("death_cooldown")) d.putInt("death_cooldown", 0);
        List<ServerPlayer> players = new ArrayList<>(level.players());
        for (ServerPlayer p : players) if (!d.contains("previous_sanity_" + p.getUUID())) d.putDouble("previous_sanity_" + p.getUUID(), 100.0);

        int currentDay = d.getInt("custom_day");
        double exactTime = d.getDouble("exact_time");
        String currentPhase = d.getString("current_phase");
        int sleepCooldown = d.getInt("sleep_cooldown"), deathCooldown = d.getInt("death_cooldown");
        if (sleepCooldown > 0) d.putInt("sleep_cooldown", sleepCooldown - 1);
        if (deathCooldown > 0) d.putInt("death_cooldown", deathCooldown - 1);
        save(server);

        // ETERNAL NIGHT (day 50+)
        if (currentDay >= TimeConfig.eternalNightDay) {
            if (Ticks.current() % 10 == 0) setTime(server, (long) TimeConfig.midnight);
            boolean change = !currentPhase.equals("ETERNAL_NIGHT");
            if (change) d.putString("current_phase", "ETERNAL_NIGHT");
            triggerTimeHook(level, currentDay, "ETERNAL_NIGHT", change);
            applyMidnightDrain(level, d);
            return;
        }

        // SLEEP: when every player is in bed, the day passes after 95 ticks
        boolean allSleeping = !players.isEmpty() && players.stream().allMatch(ServerPlayer::isSleeping);
        if (allSleeping && sleepCooldown <= 0) {
            d.putInt("sleep_cooldown", TimeConfig.sleepCooldown);
            Scheduler.runLater(server, 95, () -> passDay(level, players));
            return;
        }

        // SPEED: days get shorter and nights longer as the day count rises; blend between dusk and night
        double baseDay = scaledSpeed(currentDay, TimeConfig.daySpeed), baseNight = scaledSpeed(currentDay, TimeConfig.nightSpeed);
        double speed = baseDay;
        if (exactTime >= TimeConfig.dusk && exactTime < TimeConfig.night) {
            double blend = (exactTime - TimeConfig.dusk) / (TimeConfig.night - TimeConfig.dusk);
            speed = baseDay + blend * (baseNight - baseDay);
        } else if (exactTime >= TimeConfig.night) {
            speed = baseNight;
        }

        if (exactTime < TimeConfig.midnight) {
            exactTime += speed;
            d.putDouble("exact_time", exactTime);
            setTime(server, (long) Math.floor(exactTime));

            String newPhase = currentPhase;
            if (exactTime >= TimeConfig.midnight) newPhase = "MIDNIGHT";
            else if (exactTime >= TimeConfig.night) newPhase = "NIGHT";
            else if (exactTime >= TimeConfig.dusk) newPhase = "DUSK";
            else if (exactTime >= TimeConfig.afternoon) newPhase = "AFTERNOON";
            else if (exactTime >= TimeConfig.noon) newPhase = "NOON";
            boolean change = !newPhase.equals(currentPhase);
            if (change) { d.putString("current_phase", newPhase); currentPhase = newPhase; }
            triggerTimeHook(level, currentDay, currentPhase, change);
            // not past midnight yet (new day, sleep, death rewind): reset the drain timer
            if (exactTime < TimeConfig.midnight) d.putDouble("midnight_ticks", 0.0);
        } else {
            if (Ticks.current() % 10 == 0) setTime(server, (long) TimeConfig.midnight);
            triggerTimeHook(level, currentDay, "MIDNIGHT", false);
            applyMidnightDrain(level, d);
        }

        if (TimeConfig.debug) {
            int timer = d.getInt("debug_timer") + 1;
            if (timer >= TimeConfig.debugInterval) {
                timer = 0;
                String line = "[TimeSys] Day: " + currentDay + " | Phase: " + currentPhase + " | Time: " + (long) Math.floor(exactTime) + " | Speed: " + String.format("%.2f", speed);
                for (ServerPlayer p : level.players()) p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
            }
            d.putInt("debug_timer", timer);
        }
    }

    /** Sleep finished: new day, wake everyone with their effects and a new checkpoint. Aborts if anyone got up. */
    static void passDay(ServerLevel level, List<ServerPlayer> players) {
        MinecraftServer server = level.getServer();
        boolean allSleeping = !players.isEmpty() && players.stream().allMatch(ServerPlayer::isSleeping);
        if (!allSleeping) return;
        CompoundTag d = data(server);
        int newDay = day(d) + 1;
        d.putInt("custom_day", newDay);
        d.putDouble("exact_time", 0.0);
        d.putString("current_phase", "DAWN");
        setTime(server, 0);
        triggerTimeHook(level, newDay, "DAWN", true);
        for (ServerPlayer p : players) {
            applyWakeUpEffects(p);
            saveState(p, server);
            p.stopSleeping();
        }
        save(server);
    }

    // ---- player hooks

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.server;
        CompoundTag d = data(server);
        String key = "has_loaded" + player.getUUID();
        if (!d.contains(key) || !d.getBoolean(key)) {
            d.putBoolean(key, true);
            saveState(player, server);
        }
    }

    /** On the eternal night, beds won't let you sleep. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().getBlockState(event.getPos()).is(BlockTags.BEDS)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (day(data(player.server)) >= TimeConfig.eternalNightDay) {
            player.sendSystemMessage(Component.literal("You close your eyes, but sleep won't come. The night is eternal.").withStyle(ChatFormatting.DARK_RED));
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** MineZero revives a dead player with the `unluck` effect; seeing it (outside a checkpoint save) means a death. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;
        CompoundTag d = data(player.server);
        if (d.getBoolean("registering_checkpoint")) return;
        if (player.hasEffect(MobEffects.UNLUCK)) handleDeathLoopReset(player.server, player);
    }
}
