package io.github.soldierdonkey2.winterheart_time;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.world.WorldState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** /customtime ... (permission level 2): inspect and change the custom time system. */
public final class TimeCommands {
    private TimeCommands() {}

    private static MutableComponent c(String s, ChatFormatting f) { return Component.literal(s).withStyle(f); }

    private static int day(CompoundTag d) { int v = d.getInt("custom_day"); return v == 0 ? 1 : v; }

    private static void setPhase(CommandSourceStack src, String phase, double ticks) {
        MinecraftServer server = src.getServer();
        CompoundTag d = WorldState.tag(server);
        d.putDouble("exact_time", ticks);
        d.putString("current_phase", phase);
        TimeSystem.setTime(server, (long) ticks);
        TimeSystem.triggerTimeHook(server.overworld(), day(d), phase, true);
        WorldState.save(server);
        src.sendSystemMessage(c("[TimeSys] Set phase to ", ChatFormatting.GREEN).append(c(phase, ChatFormatting.YELLOW)).append(c(" (Tick: " + (long) ticks + ")", ChatFormatting.GREEN)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> phase(String name, String phase, java.util.function.DoubleSupplier ticks) {
        return Commands.literal(name).executes(ctx -> { setPhase(ctx.getSource(), phase, ticks.getAsDouble()); return 1; });
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("customtime").requires(s -> s.hasPermission(2))
                .then(Commands.literal("query").executes(ctx -> {
                    CompoundTag d = WorldState.tag(ctx.getSource().getServer());
                    String p = d.getString("current_phase");
                    CommandSourceStack s = ctx.getSource();
                    s.sendSystemMessage(c("--- [ Custom Time Status ] ---", ChatFormatting.GRAY));
                    s.sendSystemMessage(c("Day: " + day(d), ChatFormatting.AQUA));
                    s.sendSystemMessage(c("Phase: " + (p.isEmpty() ? "UNKNOWN" : p), ChatFormatting.YELLOW));
                    s.sendSystemMessage(c("Exact Tick: " + (long) Math.floor(d.getDouble("exact_time")), ChatFormatting.WHITE));
                    s.sendSystemMessage(c("Debug Mode: ", ChatFormatting.GRAY).append(TimeConfig.debug ? c("ON", ChatFormatting.GREEN) : c("OFF", ChatFormatting.RED)));
                    return 1;
                }))
                .then(Commands.literal("debug").executes(ctx -> {
                    TimeConfig.debug = !TimeConfig.debug;
                    ctx.getSource().sendSystemMessage(c("[TimeSys] Debug mode ", ChatFormatting.GRAY).append(TimeConfig.debug ? c("ENABLED", ChatFormatting.GREEN) : c("DISABLED", ChatFormatting.RED)));
                    return 1;
                }))
                .then(Commands.literal("day").then(Commands.argument("day", IntegerArgumentType.integer()).executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    CompoundTag d = WorldState.tag(server);
                    int newDay = IntegerArgumentType.getInteger(ctx, "day");
                    d.putInt("custom_day", newDay);
                    if (newDay >= TimeConfig.eternalNightDay) {
                        d.putString("current_phase", "ETERNAL_NIGHT");
                        d.putDouble("exact_time", TimeConfig.midnight);
                        TimeSystem.setTime(server, (long) TimeConfig.midnight);
                        TimeSystem.triggerTimeHook(server.overworld(), newDay, "ETERNAL_NIGHT", true);
                    }
                    WorldState.save(server);
                    ctx.getSource().sendSystemMessage(c("[TimeSys] Set current day to: " + newDay, ChatFormatting.GREEN));
                    return 1;
                })))
                .then(Commands.literal("time").then(Commands.argument("ticks", IntegerArgumentType.integer()).executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    int ticks = Math.max(0, Math.min(24000, IntegerArgumentType.getInteger(ctx, "ticks")));
                    WorldState.tag(server).putDouble("exact_time", ticks);
                    WorldState.save(server);
                    TimeSystem.setTime(server, ticks);
                    ctx.getSource().sendSystemMessage(c("[TimeSys] World time set to " + ticks + " ticks.", ChatFormatting.GREEN));
                    return 1;
                })))
                .then(Commands.literal("skipday").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    ServerLevel level = server.overworld();
                    CompoundTag d = WorldState.tag(server);
                    int nextDay = day(d) + 1;
                    d.putInt("sleep_cooldown", TimeConfig.sleepCooldown);
                    d.putInt("custom_day", nextDay);
                    d.putDouble("exact_time", 0.0);
                    d.putString("current_phase", "DAWN");
                    TimeSystem.setTime(server, 0);
                    WorldState.save(server);
                    TimeSystem.triggerTimeHook(level, nextDay, "DAWN", true);
                    for (ServerPlayer p : level.players()) {
                        String name = p.getGameProfile().getName();
                        Cmd.run(server, "execute as " + name + " run setcheckpoint @s");
                        Cmd.run(server, "execute as " + name + " run setSubaruPlayer @s");
                        p.sendSystemMessage(c("[Admin Skip] Day " + nextDay + " begins... Checkpoint updated.", ChatFormatting.GOLD));
                    }
                    return 1;
                }))
                .then(Commands.literal("phase")
                        .then(phase("dawn", "DAWN", () -> TimeConfig.dawn))
                        .then(phase("noon", "NOON", () -> TimeConfig.noon))
                        .then(phase("afternoon", "AFTERNOON", () -> TimeConfig.afternoon))
                        .then(phase("dusk", "DUSK", () -> TimeConfig.dusk))
                        .then(phase("night", "NIGHT", () -> TimeConfig.night))
                        .then(phase("midnight", "MIDNIGHT", () -> TimeConfig.midnight))));
    }
}
