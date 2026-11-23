package io.github.soldierdonkey2.winterheart_guides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A matcher from guides.json: absent or "*" (anything), "ns:id", "#ns:tag", {"regex": "..."} (found anywhere in the id),
 * {"page": "<lost page id>"} (a winterheart:lost_page item with that page), or an array of those (any of them).
 */
public final class Spec {
    private enum Kind { ANY, ID, TAG, REGEX, PAGE, ANY_OF }

    private final Kind kind;
    private final String text;
    private final Pattern pattern;
    private final List<Spec> parts;

    private Spec(Kind kind, String text, Pattern pattern, List<Spec> parts) {
        this.kind = kind; this.text = text; this.pattern = pattern; this.parts = parts;
    }

    public static final Spec ANY = new Spec(Kind.ANY, null, null, null);

    /** Throws IllegalArgumentException (bad regex or unsupported shape). */
    public static Spec parse(JsonElement e) {
        if (e == null || e.isJsonNull()) return ANY;
        if (e.isJsonArray()) {
            List<Spec> list = new ArrayList<>();
            for (JsonElement x : (JsonArray) e) list.add(parse(x));
            return new Spec(Kind.ANY_OF, null, null, list);
        }
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("regex")) return new Spec(Kind.REGEX, null, Pattern.compile(o.get("regex").getAsString()), null);
            if (o.has("page")) return new Spec(Kind.PAGE, o.get("page").getAsString(), null, null);
            throw new IllegalArgumentException("matcher object needs 'regex' or 'page'");
        }
        String s = e.getAsString();
        if (s.equals("*")) return ANY;
        return s.startsWith("#") ? new Spec(Kind.TAG, s.substring(1), null, null) : new Spec(Kind.ID, s, null, null);
    }

    public boolean isAny() { return kind == Kind.ANY; }

    public boolean matches(String id, Predicate<String> hasTag, ItemStack stack) {
        switch (kind) {
            case ANY: return true;
            case ANY_OF:
                for (Spec s : parts) if (s.matches(id, hasTag, stack)) return true;
                return false;
            case PAGE: return isLostPage(stack, text);
            case REGEX: return pattern.matcher(id).find();
            case TAG:
                try { return hasTag != null && hasTag.test(text); } catch (RuntimeException ex) { return false; }
            default: return text.equals(id);
        }
    }

    static boolean isLostPage(ItemStack stack, String pageId) {
        if (stack == null || stack.isEmpty() || !stack.hasTag()) return false;
        if (!"winterheart:lost_page".equals(io.github.soldierdonkey2.winterheart_guides.Books.idOf(stack))) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && pageId.equals(tag.getString("lostpage"));
    }

    @Override
    public String toString() {
        switch (kind) {
            case ANY: return "*";
            case TAG: return "#" + text;
            case REGEX: return "/" + pattern + "/";
            case PAGE: return "page " + text;
            case ANY_OF: return String.join(", ", parts.stream().map(Spec::toString).toList());
            default: return text;
        }
    }
}
