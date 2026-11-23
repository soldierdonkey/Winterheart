package io.github.soldierdonkey2.winterheart_guides;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** /guides ... debug and admin commands (permission level 2). See docs/features/guides.md. */
public final class GuideCommands {
    private GuideCommands() {}

    private static GuideData d() { return GuideManager.data(); }

    private static void feedback(CommandSourceStack src, Component msg) { src.sendSystemMessage(msg); }

    private static MutableComponent gray(String s) { return Component.literal(s).withStyle(ChatFormatting.GRAY); }
    private static MutableComponent green(String s) { return Component.literal(s).withStyle(ChatFormatting.GREEN); }
    private static MutableComponent red(String s) { return Component.literal(s).withStyle(ChatFormatting.RED); }
    private static MutableComponent yellow(String s) { return Component.literal(s).withStyle(ChatFormatting.YELLOW); }
    private static MutableComponent aqua(String s) { return Component.literal(s).withStyle(ChatFormatting.AQUA); }
    private static MutableComponent white(String s) { return Component.literal(s).withStyle(ChatFormatting.WHITE); }

    private static MutableComponent state(boolean unlocked) { return unlocked ? green("unlocked") : red("locked"); }

    private static final SuggestionProvider<CommandSourceStack> GUIDE_IDS = (c, b) -> SharedSuggestionProvider.suggest(d().guides.stream().map(g -> g.id).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> GUIDE_IDS_ALL = (c, b) -> {
        b.suggest("all");
        return SharedSuggestionProvider.suggest(d().guides.stream().map(g -> g.id).toList(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> BOOK_IDS = (c, b) -> SharedSuggestionProvider.suggest(d().books.stream().map(GuideData.Book::id).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> PAGE_IDS = (c, b) -> SharedSuggestionProvider.suggest(d().pages.stream().map(GuideData.Page::id).toList(), b);

    private static ServerPlayer needPlayer(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p == null) feedback(ctx.getSource(), red("[Guides] This command must be run by a player"));
        return p;
    }

    private static GuideData.Book bookFrom(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "book");
        GuideData.Book b = d().findBook(id);
        if (b == null) feedback(ctx.getSource(), red("[Guides] Unknown book '" + id + "' (try /guides books)"));
        return b;
    }

    private static GuideData.Page pageFrom(CommandContext<CommandSourceStack> ctx) {
        String id = StringArgumentType.getString(ctx, "page");
        GuideData.Page p = d().findPage(id);
        if (p == null) feedback(ctx.getSource(), red("[Guides] Unknown lost page '" + id + "' (try /guides pages)"));
        return p;
    }

    /** Runs fn for one id, or every guide when withAll and the id is 'all'. Returns how many returned true. */
    private static int withGuides(CommandContext<CommandSourceStack> ctx, boolean withAll, Predicate<GuideData.Guide> fn) {
        String id = StringArgumentType.getString(ctx, "id");
        List<GuideData.Guide> targets = new ArrayList<>();
        if (withAll && id.equals("all")) targets.addAll(d().guides);
        else if (d().find(id) != null) targets.add(d().find(id));
        else { feedback(ctx.getSource(), red("[Guides] Unknown guide '" + id + "'")); return 0; }
        int n = 0;
        for (GuideData.Guide g : targets) if (fn.test(g)) n++;
        return n;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> idArg(boolean withAll) {
        return Commands.argument("id", StringArgumentType.word()).suggests(withAll ? GUIDE_IDS_ALL : GUIDE_IDS);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> bookArg() {
        return Commands.argument("book", StringArgumentType.word()).suggests(BOOK_IDS);
    }

    private static String describe(GuideData.Trigger t) {
        StringBuilder sb = new StringBuilder(t.sweep != null ? "sweep " : "event ").append(t.kind());
        if ("inventory".equals(t.sweep)) {
            sb.append(" item=").append(t.item);
            if (t.count != null) sb.append(" count>=").append(t.count);
        } else if (!"custom".equals(t.sweep)) sb.append(" match=").append(t.match);
        if (t.distance != null) sb.append(" distance<=").append(t.distance);
        if (t.by != null) sb.append(" by=").append(t.by);
        if (t.minDamage != null) sb.append(" minDamage=").append(t.minDamage);
        if (t.whereFn != null) sb.append(" where=").append(t.whereFn);
        return sb.toString();
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("guides").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    feedback(ctx.getSource(), gray("--- [ Guides ] ---"));
                    int i = 1;
                    for (GuideData.Guide g : d().guides) {
                        feedback(ctx.getSource(), gray((i++) + ". ").append(white(g.id + " ")).append(gray("[" + g.book + "] ")).append(state(GuideManager.isUnlocked(server, g.id))));
                    }
                    return 1;
                }))
                .then(Commands.literal("info").then(idArg(false).executes(ctx -> withGuides(ctx, false, g -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    feedback(ctx.getSource(), Component.literal(g.id + " ").withStyle(ChatFormatting.GOLD).append(gray("\"" + g.title + "\" in book " + g.book + " ")).append(state(GuideManager.isUnlocked(server, g.id))));
                    if (g.unlock.isEmpty()) feedback(ctx.getSource(), gray("  (no triggers: commands/code only)"));
                    g.unlock.forEach(t -> feedback(ctx.getSource(), gray("  any of: ").append(yellow(describe(t)))));
                    return true;
                }))))
                .then(Commands.literal("unlock").then(idArg(false).executes(ctx -> withGuides(ctx, false, g -> {
                    boolean ok = GuideManager.unlock(ctx.getSource().getServer(), g.id, false, ctx.getSource().getPlayer());
                    feedback(ctx.getSource(), ok ? green("[Guides] Unlocked " + g.id) : yellow("[Guides] " + g.id + " was already unlocked (use /guides popup " + g.id + " to replay it)"));
                    return ok;
                }))))
                .then(Commands.literal("grant").then(idArg(true).executes(ctx -> {
                    int n = withGuides(ctx, true, g -> GuideManager.unlock(ctx.getSource().getServer(), g.id, true, null));
                    feedback(ctx.getSource(), green("[Guides] Silently unlocked " + n + " guide(s)"));
                    return n;
                })))
                .then(Commands.literal("lock").then(idArg(true).executes(ctx -> {
                    int n = withGuides(ctx, true, g -> GuideManager.lock(ctx.getSource().getServer(), g.id));
                    feedback(ctx.getSource(), green("[Guides] Locked " + n + " guide(s)"));
                    return n;
                })))
                .then(Commands.literal("popup").then(idArg(false).executes(ctx -> withGuides(ctx, false, g -> {
                    GuideManager.popup(ctx.getSource().getServer(), g.id);
                    return true;
                }))))
                .then(Commands.literal("show").then(idArg(false).executes(ctx -> {
                    ServerPlayer player = needPlayer(ctx);
                    if (player == null) return 0;
                    return withGuides(ctx, false, g -> {
                        Books.openPages(player, List.of(Books.renderPage(g, false)), null, null);
                        return true;
                    });
                })))
                .then(Commands.literal("books").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    feedback(ctx.getSource(), gray("--- [ Books ] ---"));
                    for (GuideData.Book b : d().books) {
                        List<GuideData.Guide> pages = d().guides.stream().filter(g -> g.book.equals(b.id())).toList();
                        long open = pages.stream().filter(g -> GuideManager.isUnlocked(server, g.id)).count();
                        feedback(ctx.getSource(), white(b.id() + " ").append(gray("\"" + b.name() + "\" " + open + "/" + pages.size() + " pages unlocked")));
                    }
                    return 1;
                }))
                .then(Commands.literal("give").then(bookArg()
                        .executes(ctx -> {
                            ServerPlayer player = needPlayer(ctx);
                            GuideData.Book book = player == null ? null : bookFrom(ctx);
                            if (book == null) return 0;
                            player.addItem(Books.makeBookItem(book));
                            feedback(ctx.getSource(), green("[Guides] Gave " + book.name()));
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            GuideData.Book book = bookFrom(ctx);
                            if (book == null) return 0;
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            target.addItem(Books.makeBookItem(book));
                            feedback(ctx.getSource(), green("[Guides] Gave " + book.name() + " to " + target.getGameProfile().getName()));
                            return 1;
                        }))))
                .then(Commands.literal("pages").executes(ctx -> {
                    feedback(ctx.getSource(), gray("--- [ Lost pages ] ---"));
                    d().pages.forEach(p -> feedback(ctx.getSource(), white(p.id() + " ").append(gray("\"" + p.name() + "\""))));
                    return 1;
                }))
                .then(Commands.literal("page").then(Commands.argument("page", StringArgumentType.word()).suggests(PAGE_IDS)
                        .executes(ctx -> {
                            ServerPlayer player = needPlayer(ctx);
                            GuideData.Page page = player == null ? null : pageFrom(ctx);
                            if (page == null) return 0;
                            player.addItem(Books.makeLostPageItem(page));
                            feedback(ctx.getSource(), green("[Guides] Gave " + page.name()));
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            GuideData.Page page = pageFrom(ctx);
                            if (page == null) return 0;
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            target.addItem(Books.makeLostPageItem(page));
                            feedback(ctx.getSource(), green("[Guides] Gave " + page.name() + " to " + target.getGameProfile().getName()));
                            return 1;
                        }))))
                .then(Commands.literal("book")
                        .executes(ctx -> {
                            ServerPlayer player = needPlayer(ctx);
                            if (player == null) return 0;
                            Books.openFullBook(player, null, false, null);
                            return 1;
                        })
                        .then(bookArg().executes(ctx -> {
                            ServerPlayer player = needPlayer(ctx);
                            GuideData.Book book = player == null ? null : bookFrom(ctx);
                            if (book == null) return 0;
                            Books.openFullBook(player, null, false, book.id());
                            return 1;
                        })))
                .then(Commands.literal("bookall").then(bookArg().executes(ctx -> {
                    ServerPlayer player = needPlayer(ctx);
                    GuideData.Book book = player == null ? null : bookFrom(ctx);
                    if (book == null) return 0;
                    Books.openFullBook(player, null, true, book.id());
                    return 1;
                })))
                .then(Commands.literal("probe").executes(ctx -> {
                    ServerPlayer player = needPlayer(ctx);
                    if (player == null) return 0;
                    MinecraftServer server = ctx.getSource().getServer();
                    Sweeps.Ctx sweepCtx = Sweeps.ctx(player);
                    Map<String, String> s = Sweeps.snapshot(sweepCtx);
                    feedback(ctx.getSource(), gray("--- [ Sweep probe ] ---"));
                    feedback(ctx.getSource(), aqua("standing on: " + s.get("standingOn")));
                    feedback(ctx.getSource(), aqua("standing in: " + s.get("standingIn")));
                    feedback(ctx.getSource(), aqua("position: " + s.get("position")));
                    feedback(ctx.getSource(), aqua("looking at block: " + s.get("block")));
                    feedback(ctx.getSource(), aqua("looking at entity: " + s.get("entity")));
                    feedback(ctx.getSource(), aqua("distance: " + s.get("distance")));
                    WinterheartGuides.LOGGER.info("[Guides] probe {}: {}", player.getGameProfile().getName(), s);
                    for (GuideData.Guide g : d().guides) {
                        boolean unlocked = GuideManager.isUnlocked(server, g.id);
                        for (GuideData.Trigger t : g.unlock) {
                            if (t.sweep == null) continue;
                            MutableComponent shown;
                            try { shown = Sweeps.eval(t, sweepCtx) ? green("PASS") : red("fail"); }
                            catch (RuntimeException e) { shown = red("error: " + e); }
                            feedback(ctx.getSource(), gray(g.id + (unlocked ? " (unlocked)" : "") + ": " + describe(t) + " -> ").append(shown));
                        }
                    }
                    return 1;
                }))
                .then(Commands.literal("trace").executes(ctx -> {
                    ServerPlayer player = needPlayer(ctx);
                    if (player == null) return 0;
                    boolean on = !GuideManager.trace.remove(player.getUUID());
                    if (on) GuideManager.trace.add(player.getUUID());
                    feedback(ctx.getSource(), gray("[Guides] Trace ").append(on ? green("ON") : red("OFF")));
                    return 1;
                }))
                .then(Commands.literal("fire").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer player = needPlayer(ctx);
                    if (player == null) return 0;
                    GuideApi.fire(player, StringArgumentType.getString(ctx, "name"));
                    return 1;
                })))
                .then(Commands.literal("status").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    Boolean forced = Pause.readForcePause();
                    List<String> problems = new ArrayList<>(GuideManager.dataProblems);
                    feedback(ctx.getSource(), gray("--- [ Guide status ] ---"));
                    feedback(ctx.getSource(), aqua("Unlocked: " + GuideManager.unlockedGuides(server).size() + "/" + d().guides.size()));
                    feedback(ctx.getSource(), aqua("Popup active: " + GuideManager.popupActive + " | viewers: " + GuideManager.viewers.size() + " | queued: " + GuideManager.queue.size()));
                    feedback(ctx.getSource(), aqua("Force pause: " + (forced == null ? "unknown" : forced) + " | pause held by guides: " + Pause.ownsPause));
                    feedback(ctx.getSource(), aqua("Sweep interval: " + GuideManager.SWEEP_INTERVAL_TICKS + " ticks"));
                    if (problems.isEmpty()) feedback(ctx.getSource(), green("Guide definitions: OK"));
                    problems.forEach(p -> feedback(ctx.getSource(), p.startsWith("warning: ") ? yellow(p) : red(p)));
                    return 1;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    boolean ok = GuideManager.checkDataFile(null, true);
                    feedback(ctx.getSource(), ok ? green("[Guides] guides.json reloaded") : red("[Guides] guides.json rejected, see the server log or /guides status"));
                    return ok ? 1 : 0;
                }))
                .then(Commands.literal("resume").executes(ctx -> {
                    GuideManager.reset(ctx.getSource().getServer());
                    feedback(ctx.getSource(), green("[Guides] Popup state cleared, pause released"));
                    return 1;
                })));
    }
}
