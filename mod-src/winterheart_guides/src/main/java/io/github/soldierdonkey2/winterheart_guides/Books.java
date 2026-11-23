package io.github.soldierdonkey2.winterheart_guides;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.soldierdonkey2.winterheart.item.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Rendering guide pages into written books, opening them for players, and making the guide book / lost page items. */
public final class Books {
    private Books() {}

    public static String idOf(ItemStack stack) {
        return String.valueOf(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** One page's JSON text: [locked marker], bold coloured title, blank line, the content. */
    public static String renderPage(GuideData.Guide g, boolean locked) {
        JsonArray parts = new JsonArray();
        parts.add("");
        if (locked) parts.add(text("[locked] ", "gray", false));
        GuideData.Book book = GuideManager.data().findBook(g.book);
        String color = book != null && book.titleColor() != null ? book.titleColor() : "dark_red";
        parts.add(text(g.title, color, true));
        parts.add(text("\n\n", null, false));
        if (g.content != null) {
            if (g.content.isJsonArray()) g.content.getAsJsonArray().forEach(c -> parts.add(content(c)));
            else parts.add(content(g.content));
        }
        return parts.toString();
    }

    private static JsonElement content(JsonElement c) {
        return c.isJsonPrimitive() ? text(c.getAsString(), "black", false) : c;
    }

    private static JsonObject text(String text, String color, boolean bold) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        if (color != null) o.addProperty("color", color);
        if (bold) o.addProperty("bold", true);
        return o;
    }

    private static void sync(ServerPlayer p) {
        p.containerMenu.broadcastChanges();
        if (p.inventoryMenu != p.containerMenu) p.inventoryMenu.broadcastChanges();
    }

    /**
     * Opens the vanilla book screen with these pages (JSON strings). The open-book packet only works on the item in the
     * hand, so a written book is swapped in for the same tick and the original stack put back.
     */
    public static void openPages(ServerPlayer player, List<String> pages, InteractionHand hand, GuideData.Book book) {
        if (hand == null) hand = InteractionHand.MAIN_HAND;
        ItemStack original = player.getItemInHand(hand);
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = new CompoundTag();
        String title = book != null ? book.name() : GuideManager.BOOK_TITLE;
        tag.putString("title", title.length() > 32 ? title.substring(0, 32) : title);   // vanilla rejects titles over 32 characters
        tag.putString("author", book != null && book.author() != null ? book.author() : GuideManager.BOOK_AUTHOR);
        tag.putBoolean("resolved", true);
        ListTag list = new ListTag();
        pages.forEach(p -> list.add(StringTag.valueOf(p)));
        tag.put("pages", list);
        stack.setTag(tag);
        player.setItemInHand(hand, stack);
        sync(player);
        player.openItemGui(stack, hand);
        player.setItemInHand(hand, original);
        sync(player);
    }

    /** A book's unlocked guides in file order (includeLocked adds the rest, marked). Falls back to the first book. */
    public static void openFullBook(ServerPlayer player, InteractionHand hand, boolean includeLocked, String bookId) {
        GuideData d = GuideManager.data();
        GuideData.Book book = d.findBook(bookId) != null ? d.findBook(bookId) : d.books.get(0);
        List<String> pages = new ArrayList<>();
        for (GuideData.Guide g : d.guides) {
            if (!g.book.equals(book.id())) continue;
            boolean unlocked = GuideManager.isUnlocked(player.server, g.id);
            if (unlocked || includeLocked) pages.add(renderPage(g, !unlocked));
        }
        if (pages.isEmpty()) {
            JsonArray none = new JsonArray();
            none.add("");
            JsonObject o = text("Nothing recorded yet.", "gray", false);
            o.addProperty("italic", true);
            none.add(o);
            pages.add(none.toString());
        }
        openPages(player, pages, hand, book);
    }

    private static JsonObject nameJson(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("text", name);
        o.addProperty("italic", false);
        return o;
    }

    private static CompoundTag display(String name, String tooltip) {
        CompoundTag display = new CompoundTag();
        display.putString("Name", nameJson(name).toString());
        if (tooltip != null && !tooltip.isEmpty()) {
            JsonObject lore = nameJson(tooltip);
            lore.addProperty("color", "gray");
            ListTag l = new ListTag();
            l.add(StringTag.valueOf(lore.toString()));
            display.put("Lore", l);
        }
        return display;
    }

    /** The guide book item for a book: one shared item id, tagged with the book id, named from the JSON. */
    public static ItemStack makeBookItem(GuideData.Book book) {
        ItemStack stack = new ItemStack(ModItems.GUIDE_BOOK.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("guidebook", book.id());
        tag.put("display", display(book.name(), book.tooltip()));
        if (book.texture() != null && book.model() != null) tag.putDouble("CustomModelData", book.model());
        stack.setTag(tag);
        return stack;
    }

    public static ItemStack makeLostPageItem(GuideData.Page page) {
        ItemStack stack = new ItemStack(ModItems.LOST_PAGE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString("lostpage", page.id());
        tag.put("display", display(page.name(), page.tooltip()));
        stack.setTag(tag);
        return stack;
    }
}
