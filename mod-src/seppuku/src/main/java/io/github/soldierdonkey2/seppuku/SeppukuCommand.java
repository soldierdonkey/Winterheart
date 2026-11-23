package io.github.soldierdonkey2.seppuku;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;

/**
 * /seppuku play <targets> [animation]
 * /seppuku stop <targets>
 */
public final class SeppukuCommand {
    private SeppukuCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(Seppuku.MOD_ID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> play(ctx, Seppuku.DEFAULT_ANIMATION))
                                .then(Commands.argument("animation", ResourceLocationArgument.id())
                                        .executes(ctx -> play(ctx, ResourceLocationArgument.getId(ctx, "animation"))))))
                .then(Commands.literal("stop")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(SeppukuCommand::stop))));
    }

    private static int play(CommandContext<CommandSourceStack> ctx, ResourceLocation animation) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        targets.forEach(player -> SeppukuApi.play(player, animation));
        ctx.getSource().sendSuccess(() -> Component.literal("Playing " + animation + " on " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
        targets.forEach(SeppukuApi::stop);
        ctx.getSource().sendSuccess(() -> Component.literal("Stopped animation on " + targets.size() + " player(s)"), true);
        return targets.size();
    }
}
