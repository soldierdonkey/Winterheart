package io.github.soldierdonkey2.winterheart_guides;

import io.github.soldierdonkey2.winterheart.api.Scheduler;
import io.github.soldierdonkey2.winterheart.world.WorldState;
import io.github.soldierdonkey2.winterheart_guides.net.GuideNet;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The guide engine: loads guides.json (re-read every 2 s, so saving in the guide editor updates the running game),
 * stores which guides are unlocked (world-wide, in the world state), indexes the triggers of locked guides and runs
 * the unlock popup flow (a book with just that page for every player, the server paused until all have closed it).
 */
public final class GuideManager {
    public static final int SWEEP_INTERVAL_TICKS = 10;
    public static final int RELOAD_CHECK_TICKS = 40;
    public static final int POPUP_GAP_TICKS = 10;
    public static final boolean PAUSE_ON_UNLOCK = true;
    public static final String BOOK_TITLE = "Winterheart Guide";
    public static final String BOOK_AUTHOR = "Winterheart";
    public static final String STORE_KEY = "winterheart_guides_unlocked";
    public static final Path DATA_FILE = FMLPaths.GAMEDIR.get().resolve("datapacks/winterheart_guides/data/winterheart/guides/guides.json");

    // ---- runtime state
    private static GuideData data = GuideData.parse("{\"guides\":[]}");
    private static String dataText;
    private static boolean readFailed;
    public static List<String> dataProblems = new ArrayList<>();
    public static final Set<UUID> viewers = new HashSet<>();
    public static boolean popupActive;
    public static final Deque<String> queue = new ArrayDeque<>();
    public static final Set<UUID> trace = new HashSet<>();
    static final Map<UUID, String> lastTrace = new HashMap<>();
    private static final Set<String> warned = new HashSet<>();

    public record Entry(GuideData.Guide guide, GuideData.Trigger trigger) {}
    private static final class Index {
        final List<Entry> sweeps = new ArrayList<>();
        final Map<String, List<Entry>> events = new HashMap<>();
    }
    private static Index index;

    private GuideManager() {}

    public static GuideData data() { return data; }

    // ---- data file

    private static String readFile() {
        try {
            String text = Files.readString(DATA_FILE, StandardCharsets.UTF_8);
            if (!text.isEmpty()) { readFailed = false; return text; }
        } catch (IOException e) {
            if (!readFailed) WinterheartGuides.LOGGER.error("[Guides] could not read {}: {}", DATA_FILE, e.toString());
        }
        readFailed = true;
        return null;
    }

    private static boolean apply(String text) {
        GuideData parsed = GuideData.parse(text);
        dataProblems = parsed.problems;
        for (String p : parsed.problems) {
            if (p.startsWith("warning: ")) WinterheartGuides.LOGGER.warn("[Guides] {}", p);
            else WinterheartGuides.LOGGER.error("[Guides] {}", p);
        }
        if (parsed.hasErrors()) {
            WinterheartGuides.LOGGER.error("[Guides] guides.json has errors; keeping the previous guides");
            return false;
        }
        data = parsed;
        invalidate();
        WinterheartGuides.LOGGER.info("[Guides] Loaded {} guide(s) in {} book(s) from guides.json", parsed.guides.size(), parsed.books.size());
        return true;
    }

    /** force = re-apply even if the file did not change. Returns true if the guides were (re)loaded. */
    public static boolean checkDataFile(MinecraftServer server, boolean force) {
        String text = readFile();
        if (text == null) return false;
        if (!force && text.equals(dataText)) return false;
        boolean first = dataText == null;
        dataText = text;     // remembered even when rejected, so a broken file is reported once, not every check
        boolean ok = apply(text);
        if (server != null && !first) {
            Component msg = ok ? Component.literal("[guides] guides.json reloaded").withStyle(ChatFormatting.GRAY)
                    : Component.literal("[guides] guides.json rejected, see the server log").withStyle(ChatFormatting.RED);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p.hasPermissions(2)) p.sendSystemMessage(msg);
        }
        return ok;
    }

    // ---- unlock store

    private static CompoundTag store(MinecraftServer server) {
        CompoundTag root = WorldState.tag(server);
        if (!root.contains(STORE_KEY, 10)) root.put(STORE_KEY, new CompoundTag());
        return root.getCompound(STORE_KEY);
    }

    public static boolean isUnlocked(MinecraftServer server, String id) { return store(server).contains(id); }

    public static List<GuideData.Guide> unlockedGuides(MinecraftServer server) {
        return data.guides.stream().filter(g -> isUnlocked(server, g.id)).toList();
    }

    public static boolean lock(MinecraftServer server, String id) {
        if (!isUnlocked(server, id)) return false;
        store(server).remove(id);
        WorldState.save(server);
        invalidate();
        return true;
    }

    // ---- trigger index (locked guides only, rebuilt whenever unlock state changes)

    static void invalidate() { index = null; }

    private static Index index(MinecraftServer server) {
        if (index != null) return index;
        Index ix = new Index();
        for (GuideData.Guide g : data.guides) {
            if (g.id == null || isUnlocked(server, g.id)) continue;
            for (GuideData.Trigger t : g.unlock) {
                if (t.sweep != null) ix.sweeps.add(new Entry(g, t));
                else if (t.event != null) ix.events.computeIfAbsent(t.event, k -> new ArrayList<>()).add(new Entry(g, t));
            }
        }
        index = ix;
        return ix;
    }

    static void warnOnce(String id, String msg) {
        if (warned.add(id)) WinterheartGuides.LOGGER.error("[Guides] guide '{}': {}", id, msg);
    }

    // ---- sweeps

    static void runSweep(MinecraftServer server) {
        List<Entry> sweeps = index(server).sweeps;
        if (sweeps.isEmpty() && trace.isEmpty()) return;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            if (!player.isAlive()) continue;
            Sweeps.Ctx ctx = Sweeps.ctx(player);
            if (trace.contains(player.getUUID())) traceLine(player, ctx);
            for (Entry entry : new ArrayList<>(sweeps)) {
                if (isUnlocked(server, entry.guide().id)) continue;
                try {
                    if (Sweeps.eval(entry.trigger(), ctx)) unlock(server, entry.guide().id, false, player);
                } catch (RuntimeException e) {
                    warnOnce(entry.guide().id, "sweep '" + entry.trigger().sweep + "' threw: " + e);
                }
            }
        }
    }

    private static void traceLine(ServerPlayer player, Sweeps.Ctx ctx) {
        Map<String, String> s = Sweeps.snapshot(ctx);
        String line = "on " + s.get("standingOn") + " | in " + s.get("standingIn") + " | looking at " + s.get("block") + " / " + s.get("entity") + " (" + s.get("distance") + "m) | " + s.get("time");
        player.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.GRAY), true);
        String key = s.get("standingOn") + "|" + s.get("standingIn") + "|" + s.get("block") + "|" + s.get("entity");
        if (!key.equals(lastTrace.get(player.getUUID()))) {
            lastTrace.put(player.getUUID(), key);
            WinterheartGuides.LOGGER.info("[Guides] sweep {}: {}", player.getGameProfile().getName(), line);
        }
    }

    // ---- events

    /** What an event carries; only built if someone is listening or tracing. */
    public static final class Payload {
        public String id;
        public List<String> ids;
        public java.util.function.Predicate<String> hasTag;
        public net.minecraft.world.item.ItemStack stack;
        public net.minecraft.world.entity.Entity attacker;
        public Double damage;
        public Integer day;
    }

    public static void dispatch(String kind, ServerPlayer player, Supplier<Payload> make) {
        if (player == null || player.server == null) return;
        dispatchCore(kind, player, player.server, make);
    }

    /** player is null for world-level events (time phases); `ctx.player` is then null in `where` functions. */
    public static void dispatchCore(String kind, ServerPlayer player, MinecraftServer server, Supplier<Payload> make) {
        List<ServerPlayer> tracers = new ArrayList<>();
        if (player != null) { if (trace.contains(player.getUUID())) tracers.add(player); }
        else for (ServerPlayer p : server.getPlayerList().getPlayers()) if (trace.contains(p.getUUID())) tracers.add(p);
        List<Entry> list = index(server).events.get(kind);
        if (list == null && tracers.isEmpty()) return;

        Payload p = make.get();
        if (!tracers.isEmpty()) {
            String extra = p.damage != null ? " (" + Math.round(p.damage * 10) / 10.0 + " dmg)" : p.day != null ? " (day " + p.day + ")" : "";
            String shown = p.ids != null ? String.join(" / ", p.ids) : p.id;
            Component line = Component.literal("[guides] ").withStyle(ChatFormatting.GRAY).append(Component.literal(kind).withStyle(ChatFormatting.AQUA)).append(Component.literal(" " + shown + extra));
            tracers.forEach(tp -> tp.sendSystemMessage(line));
        }
        if (list == null) return;
        for (Entry entry : new ArrayList<>(list)) {
            GuideData.Trigger t = entry.trigger();
            if (isUnlocked(server, entry.guide().id)) continue;
            try {
                boolean any = false;
                for (String id : p.ids != null ? p.ids : List.of(p.id)) if (t.match.matches(id, p.hasTag, p.stack)) { any = true; break; }
                if (!any) continue;
                if (t.minDamage != null && !(p.damage != null && p.damage >= t.minDamage)) continue;
                if (p.day != null && !Sweeps.dayInRange(t, p.day)) continue;
                if (t.by != null && !(p.attacker != null && Sweeps.matchEntity(t.by, p.attacker))) continue;
                if (!Sweeps.requirementOk(t, player, server)) continue;
                if (t.whereFn != null) {
                    var where = GuideFunctions.get(t.whereFn);
                    if (where == null || !where.test(player != null ? Sweeps.ctx(player) : null)) continue;
                }
                unlock(server, entry.guide().id, false, player);
            } catch (RuntimeException e) {
                warnOnce(entry.guide().id, "event '" + kind + "' threw: " + e);
            }
        }
    }

    // ---- unlock + popup flow

    /** silent: skip the popup; player: who triggered it. Returns true if it was newly unlocked. */
    public static boolean unlock(MinecraftServer server, String id, boolean silent, ServerPlayer player) {
        if (data.find(id) == null || isUnlocked(server, id)) return false;
        store(server).putBoolean(id, true);
        WorldState.save(server);
        invalidate();
        WinterheartGuides.LOGGER.info("[Guides] Unlocked '{}'{}", id, player != null ? " (triggered by " + player.getGameProfile().getName() + ")" : "");
        if (!silent) popup(server, id);
        return true;
    }

    /** Shows a guide to everyone (book + pause) without touching unlock state. */
    public static boolean popup(MinecraftServer server, String id) {
        if (data.find(id) == null) return false;
        queue.add(id);
        showNextPopup(server);
        return true;
    }

    private static void showNextPopup(MinecraftServer server) {
        if (popupActive) return;
        while (!queue.isEmpty()) {
            GuideData.Guide g = data.find(queue.poll());
            if (g == null) continue;
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) continue;
            List<String> pages = List.of(Books.renderPage(g, false));
            popupActive = true;
            for (ServerPlayer p : new ArrayList<>(players)) {
                Books.openPages(p, pages, InteractionHand.MAIN_HAND, data.findBook(g.book));
                viewers.add(p.getUUID());
                GuideNet.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new GuideNet.OpenPacket());
            }
            Pause.pause(server);
            return;
        }
    }

    private static void finishPopup(MinecraftServer server) {
        WinterheartGuides.LOGGER.info("[Guides] Popup closed by all viewers; releasing the pause ({} more queued)", queue.size());
        viewers.clear();
        popupActive = false;
        Pause.resume(server);
        if (!queue.isEmpty()) Scheduler.runLater(server, POPUP_GAP_TICKS, () -> showNextPopup(server));
    }

    /** Emergency release: forget the popup queue, release the pause we hold. */
    public static void reset(MinecraftServer server) {
        queue.clear();
        finishPopup(server);
    }

    public static void onBookClosed(ServerPlayer player) {
        if (player == null || !popupActive) return;
        viewers.remove(player.getUUID());
        if (viewers.isEmpty()) finishPopup(player.server);
    }

    public static void onLoggedOut(ServerPlayer player) {
        if (!viewers.remove(player.getUUID())) return;
        if (popupActive && viewers.isEmpty()) finishPopup(player.server);
    }
}
