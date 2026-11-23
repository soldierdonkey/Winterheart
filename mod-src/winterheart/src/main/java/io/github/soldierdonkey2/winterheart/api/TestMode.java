package io.github.soldierdonkey2.winterheart.api;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * /toggle_test: freezes the custom time system at noon and blocks mob spawns, for testing. Not saved: resets to off
 * on restart. The time system and the scripted spawner check {@link #enabled()}.
 */
public final class TestMode {
    private static volatile boolean enabled = false;

    private TestMode() {}

    public static boolean enabled() { return enabled; }

    /** Natural, spawner and mod biome spawns (the scripted spawner checks the flag itself). */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (enabled) event.setSpawnCancelled(true);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("toggle_test").requires(s -> s.hasPermission(2)).executes(ctx -> {
            enabled = !enabled;
            if (enabled) Cmd.run(ctx.getSource().getServer(), "time set 6000");
            Component state = enabled ? Component.literal("ON (always day, no mob spawns)").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED);
            ctx.getSource().sendSystemMessage(Component.literal("[Test] Test mode ").withStyle(ChatFormatting.GRAY).append(state));
            return 1;
        }));
    }
}
