package io.github.soldierdonkey2.winterheart.api;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Collection;

/**
 * `/winterheart dump_ids` (op) writes every item, block, entity type, damage type and advancement id, plus the item, block, entity type and damage type tags, to
 * config/winterheart/registry_ids.json. tools/guide_editor reads it to validate and suggest ids, so rerun it after
 * adding mods (a snapshot is committed at tools/guide_editor/registry_ids.json).
 */
public final class DumpIds {
    public static final String FILE = "config/winterheart/registry_ids.json";

    private DumpIds() {}

    private static JsonArray sorted(Collection<ResourceLocation> ids) {
        JsonArray a = new JsonArray();
        ids.stream().map(ResourceLocation::toString).sorted().forEach(a::add);
        return a;
    }

    public static int dump(MinecraftServer server) throws IOException {
        JsonObject o = new JsonObject();
        o.add("Item", sorted(ForgeRegistries.ITEMS.getKeys()));
        o.add("Block", sorted(ForgeRegistries.BLOCKS.getKeys()));
        o.add("EntityType", sorted(ForgeRegistries.ENTITY_TYPES.getKeys()));
        o.add("DamageType", sorted(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).keySet()));
        o.add("ItemTag", sorted(net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagNames().map(t -> t.location()).toList()));
        o.add("BlockTag", sorted(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagNames().map(t -> t.location()).toList()));
        o.add("EntityTypeTag", sorted(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getTagNames().map(t -> t.location()).toList()));
        o.add("DamageTypeTag", sorted(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getTagNames().map(t -> t.location()).toList()));
        o.add("Advancement", sorted(server.getAdvancements().getAllAdvancements().stream().map(a -> a.getId()).toList()));
        Path file = FMLPaths.GAMEDIR.get().resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n", StandardCharsets.UTF_8);
        return o.get("Item").getAsJsonArray().size();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("winterheart").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dump_ids").executes(ctx -> {
                    try {
                        int items = dump(ctx.getSource().getServer());
                        ctx.getSource().sendSystemMessage(Component.literal("Wrote " + FILE + " (" + items + " items)"));
                        return 1;
                    } catch (IOException e) {
                        Winterheart.LOGGER.error("dump_ids failed", e);
                        ctx.getSource().sendFailure(Component.literal("dump_ids failed: " + e.getMessage()));
                        return 0;
                    }
                })));
    }
}
