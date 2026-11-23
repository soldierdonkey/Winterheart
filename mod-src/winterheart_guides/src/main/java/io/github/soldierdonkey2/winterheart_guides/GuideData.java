package io.github.soldierdonkey2.winterheart_guides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** guides.json parsed and validated: books, guides (pages) with their triggers, and lost pages. */
public final class GuideData {
    public static final List<String> SWEEP_KINDS = List.of("standing_on", "standing_in", "looking_at_block", "looking_at_entity", "inventory", "holding", "time", "position", "custom");
    public static final List<String> EVENT_KINDS = List.of("block_broken", "block_placed", "block_used", "item_used", "item_crafted", "item_smelted",
            "item_picked_up", "item_eaten", "entity_killed", "entity_hurt", "player_hurt", "player_died", "advancement", "time_phase", "proximity", "custom");
    private static final Pattern ID = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Set<String> COLOR_NAMES = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");

    public record Book(String id, String name, String author, String tooltip, String titleColor, String texture, Integer model) {}
    public record Page(String id, String name, String tooltip) {}

    public static final class Trigger {
        public JsonObject raw;
        public String sweep, event, hand, dimension, testFn, whereFn;
        public Spec match = Spec.ANY, item = Spec.ANY, by = Spec.ANY, holding;
        public boolean hasItem, hasMatch;
        public Integer count, minDay, maxDay;
        public Double distance, minDamage, minY, maxY, x, z, radius;

        public String kind() { return sweep != null ? sweep : event; }
    }

    public static final class Guide {
        public String id, title, book;
        public JsonElement content;
        public List<Trigger> unlock = new ArrayList<>();
    }

    public final List<Guide> guides = new ArrayList<>();
    public final List<Book> books = new ArrayList<>();
    public final List<Page> pages = new ArrayList<>();
    public final List<String> problems = new ArrayList<>();

    public static boolean validColor(String c) {
        return c != null && (COLOR_NAMES.contains(c) || COLOR.matcher(c).matches());
    }

    private static String str(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : null; }
    private static Double num(JsonObject o, String k) { return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : null; }

    /** Parses and validates. Problems starting with "warning: " don't stop a load. */
    public static GuideData parse(String text) {
        GuideData d = new GuideData();
        JsonElement root;
        try {
            root = JsonParser.parseString(text);
        } catch (RuntimeException e) {
            d.problems.add("guides.json is not valid JSON: " + e.getMessage());
            return d;
        }
        JsonArray src = root.isJsonArray() ? root.getAsJsonArray()
                : root.isJsonObject() && root.getAsJsonObject().has("guides") ? root.getAsJsonObject().getAsJsonArray("guides") : null;
        if (src == null) { d.problems.add("guides.json needs a \"guides\" array"); return d; }
        JsonObject obj = root.isJsonObject() ? root.getAsJsonObject() : new JsonObject();

        if (obj.has("books") && obj.getAsJsonArray("books").size() > 0) {
            int i = 0;
            for (JsonElement e : obj.getAsJsonArray("books")) {
                JsonObject b = e.getAsJsonObject();
                String id = str(b, "id");
                if (id == null || !ID.matcher(id).matches()) { d.problems.add("books[" + i + "]: id must be a string of a-z, 0-9 and _"); i++; continue; }
                Double model = num(b, "model");
                d.books.add(new Book(id, str(b, "name"), str(b, "author"), str(b, "tooltip"), str(b, "titleColor"), str(b, "texture"), model == null ? null : model.intValue()));
                i++;
            }
        }
        if (d.books.isEmpty()) d.books.add(new Book("guide", "Guide Book", null, null, null, null, null));
        if (obj.has("pages")) {
            int i = 0;
            for (JsonElement e : obj.getAsJsonArray("pages")) {
                JsonObject p = e.getAsJsonObject();
                String id = str(p, "id");
                if (id == null || !ID.matcher(id).matches()) { d.problems.add("pages[" + i + "]: id must be a string of a-z, 0-9 and _"); i++; continue; }
                d.pages.add(new Page(id, str(p, "name"), str(p, "tooltip")));
                i++;
            }
        }
        int i = 0;
        for (JsonElement e : src) {
            JsonObject g = e.getAsJsonObject();
            Guide guide = new Guide();
            guide.id = str(g, "id");
            guide.title = str(g, "title");
            guide.book = str(g, "book") == null || str(g, "book").isEmpty() ? d.books.get(0).id() : str(g, "book");
            guide.content = g.get("content");
            JsonElement u = g.get("unlock");
            List<JsonElement> triggers = new ArrayList<>();
            if (u != null && u.isJsonArray()) u.getAsJsonArray().forEach(triggers::add);
            else if (u != null && !u.isJsonNull()) triggers.add(u);
            for (JsonElement t : triggers) {
                try {
                    guide.unlock.add(parseTrigger(t.getAsJsonObject()));
                } catch (RuntimeException ex) {
                    d.problems.add("guide '" + guide.id + "': bad trigger: " + ex.getMessage());
                }
            }
            d.guides.add(guide);
            i++;
        }
        d.validate();
        return d;
    }

    private static Trigger parseTrigger(JsonObject o) {
        Trigger t = new Trigger();
        t.raw = o;
        t.sweep = str(o, "sweep");
        t.event = str(o, "event");
        t.hand = str(o, "hand");
        t.dimension = str(o, "dimension");
        t.hasItem = o.has("item");
        t.hasMatch = o.has("match");
        t.match = Spec.parse(o.get("match"));
        t.item = Spec.parse(o.get("item"));
        t.by = o.has("by") ? Spec.parse(o.get("by")) : null;
        t.holding = o.has("holding") ? Spec.parse(o.get("holding")) : null;
        t.testFn = str(o, "test");
        t.whereFn = str(o, "where");
        Double c = num(o, "count"); t.count = c == null ? null : c.intValue();
        Double a = num(o, "minDay"); t.minDay = a == null ? null : a.intValue();
        Double b = num(o, "maxDay"); t.maxDay = b == null ? null : b.intValue();
        t.distance = num(o, "distance"); t.minDamage = num(o, "minDamage");
        t.minY = num(o, "minY"); t.maxY = num(o, "maxY"); t.x = num(o, "x"); t.z = num(o, "z"); t.radius = num(o, "radius");
        return t;
    }

    private void validate() {
        Set<String> seenPages = new HashSet<>();
        for (Page p : pages) {
            if (!seenPages.add(p.id())) problems.add("lost page '" + p.id() + "': duplicate id");
            if (p.name() == null || p.name().isEmpty()) problems.add("lost page '" + p.id() + "': missing name");
        }
        Set<String> seenBooks = new HashSet<>();
        for (Book b : books) {
            if (!seenBooks.add(b.id())) problems.add("book '" + b.id() + "': duplicate id");
            if (b.name() == null || b.name().isEmpty()) problems.add("book '" + b.id() + "': missing name");
            if (b.titleColor() != null && !validColor(b.titleColor())) problems.add("book '" + b.id() + "': titleColor must be a color name or #rrggbb");
        }
        Set<String> seen = new HashSet<>();
        for (Guide g : guides) {
            if (g.id == null || !ID.matcher(g.id).matches()) { problems.add("guide: id must be a string of a-z, 0-9 and _"); continue; }
            String where = "guide '" + g.id + "'";
            if (!seen.add(g.id)) problems.add(where + ": duplicate id");
            if (g.title == null) problems.add(where + ": missing title");
            if (!seenBooks.contains(g.book)) problems.add(where + ": book '" + g.book + "' does not exist");
            if (g.content == null) problems.add(where + ": missing content");
            if (g.unlock.isEmpty()) problems.add("warning: " + where + ": no unlock trigger (only unlockable from code/commands)");
            for (Trigger t : g.unlock) {
                if (t.sweep != null) {
                    if (!SWEEP_KINDS.contains(t.sweep)) problems.add(where + ": unknown sweep '" + t.sweep + "'");
                    if (t.sweep.equals("custom") && (t.testFn == null || !GuideFunctions.has(t.testFn))) problems.add(where + ": custom sweep needs 'test' naming a registered function");
                    if (t.sweep.equals("inventory") && !t.hasItem) problems.add(where + ": inventory sweep needs item");
                    if (t.sweep.equals("holding") && !t.hasItem) problems.add(where + ": holding sweep needs item");
                    if (t.sweep.equals("position")) {
                        if (t.minY == null && t.maxY == null && t.x == null && t.dimension == null) problems.add(where + ": position sweep needs minY, maxY, x/z/radius or dimension");
                        if ((t.x == null) != (t.z == null)) problems.add(where + ": position sweep needs both x and z");
                    }
                } else if (t.event != null) {
                    if (!EVENT_KINDS.contains(t.event)) problems.add(where + ": unknown event '" + t.event + "'");
                } else {
                    problems.add(where + ": trigger needs a 'sweep' or 'event' key");
                }
                if (t.hand != null && !List.of("any", "main", "off").contains(t.hand)) problems.add(where + ": hand must be any, main or off");
                if (t.whereFn != null && !GuideFunctions.has(t.whereFn)) problems.add(where + ": no function '" + t.whereFn + "' registered in GuideFunctions");
            }
        }
    }

    public boolean hasErrors() { return problems.stream().anyMatch(p -> !p.startsWith("warning: ")); }

    public Guide find(String id) { return guides.stream().filter(g -> g.id.equals(id)).findFirst().orElse(null); }

    public Book findBook(String id) { return books.stream().filter(b -> b.id().equals(id)).findFirst().orElse(null); }

    public Page findPage(String id) { return pages.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null); }
}
