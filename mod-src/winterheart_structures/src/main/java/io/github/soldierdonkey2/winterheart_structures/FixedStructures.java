package io.github.soldierdonkey2.winterheart_structures;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.api.TestMode;
import io.github.soldierdonkey2.winterheart.world.WorldState;
import io.github.soldierdonkey2.winterheart_structures.StructureConfig.Placement;
import io.github.soldierdonkey2.winterheart_structures.StructureConfig.Profile;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Places structure templates once per world at fixed spots with a fixed rotation. Worldgen structures always get a
 * random rotation in 1.20.1, so these are pasted with `/place template`. Each pending spot is force-loaded at server
 * start and placed as soon as its chunks are loaded; "placed" is remembered in the world state (the key
 * `fixed_structure_placed:<template>@<x>,<z>`, as the KubeJS version did, so existing worlds don't place again).
 */
public final class FixedStructures {
    private static final int FIXUP_FLAGS = 2 | 16;          // send to clients without updating neighbour shapes
    private static final int GROUND_KEEL_LAYERS = 4;
    private static final Set<String> FAILED = new HashSet<>();   // failed this session (e.g. missing template); not retried until restart

    private FixedStructures() {}

    private record Box(int x1, int z1, int x2, int z2) {}
    private record Origin(int x, int z) {}

    private static boolean placed(MinecraftServer server, Placement s) {
        CompoundTag data = WorldState.tag(server);
        if (data.getBoolean(s.key())) return true;
        int[] legacy = StructureConfig.legacyPlaced.get(s.template);
        if (legacy != null && legacy[0] == s.x && legacy[1] == s.z && data.getBoolean("fixed_structure_placed:" + s.template)) {
            data.putBoolean(s.key(), true);
            WorldState.save(server);
            return true;
        }
        return false;
    }

    /** Template size [x, y, z], or null if the template doesn't exist. */
    private static int[] size(MinecraftServer server, Placement s) {
        if (s.size != null) return s.size;
        Optional<StructureTemplate> t = server.getStructureManager().get(new ResourceLocation(s.template));
        if (t.isEmpty()) return null;
        var v = t.get().getSize();
        s.size = new int[]{v.getX(), v.getY(), v.getZ()};
        return s.size;
    }

    /** The world box the rotated footprint covers, centred on the target (or with the profile's `center` point on it). */
    private static Box box(Placement s, int[] size) {
        boolean quarter = s.rotation.equals("clockwise_90") || s.rotation.equals("counterclockwise_90");
        int w = quarter ? size[2] : size[0], d = quarter ? size[0] : size[2];
        int[] c = s.profile.center;
        if (c != null && !s.rotation.equals("none")) WinterheartStructures.LOGGER.warn("[FixedStructures] {}: center ignored for rotation {}", s.template, s.rotation);
        boolean useCenter = c != null && s.rotation.equals("none");
        int x1 = s.x - (useCenter ? c[0] : w / 2), z1 = s.z - (useCenter ? c[1] : d / 2);
        return new Box(x1, z1, x1 + w - 1, z1 + d - 1);
    }

    /** /place template puts the template's min corner at the given pos for rotation none; other rotations move the corner. */
    private static Origin origin(Box b, Placement s) {
        switch (s.rotation) {
            case "clockwise_90": return new Origin(b.x2(), b.z1());
            case "180": return new Origin(b.x2(), b.z2());
            case "counterclockwise_90": return new Origin(b.x1(), b.z2());
            default: return new Origin(b.x1(), b.z1());
        }
    }

    /** Surface Y: the median of the centre and the corners, so one tree trunk or hole doesn't decide the height. */
    private static int surface(ServerLevel level, Box b) {
        int cx = (b.x1() + b.x2()) / 2, cz = (b.z1() + b.z2()) / 2;
        int[][] pts = {{cx, cz}, {b.x1(), b.z1()}, {b.x2(), b.z1()}, {b.x1(), b.z2()}, {b.x2(), b.z2()}};
        int[] ys = Arrays.stream(pts).mapToInt(p -> level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p[0], p[1])).sorted().toArray();
        return ys[2];
    }

    private static BlockState parseState(ServerLevel level, String text) {
        try {
            return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), text, false).blockState();
        } catch (Exception e) {
            WinterheartStructures.LOGGER.error("[FixedStructures] bad block state '{}': {}", text, e.toString());
            return null;
        }
    }

    /**
     * Connected blocks (e.g. Macaw's bridge corners) get recomputed from their neighbours by /place template.
     * python/structures/structure_fixups.py writes their saved states to <template>.fixups.json; this writes them back.
     */
    private static int applyFixups(ServerLevel level, Placement s, Origin o, int y0) {
        String[] id = s.template.split(":");
        ResourceLocation file = new ResourceLocation(id[0], "structures/" + id[1] + ".fixups.json");
        Optional<Resource> res = level.getServer().getResourceManager().getResource(file);
        if (res.isEmpty()) return 0;
        if (!s.rotation.equals("none")) { WinterheartStructures.LOGGER.warn("[FixedStructures] {} skipped: fixups only support rotation none", file); return 0; }
        int count = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement e : json.getAsJsonArray("blocks")) {
                JsonObject b = e.getAsJsonObject();
                var pos = b.getAsJsonArray("pos");
                BlockState state = parseState(level, b.get("state").getAsString());
                if (state == null) continue;
                level.setBlock(new BlockPos(o.x() + pos.get(0).getAsInt(), y0 + pos.get(1).getAsInt(), o.z() + pos.get(2).getAsInt()), state, FIXUP_FLAGS);
                count++;
            }
        } catch (Exception e) {
            WinterheartStructures.LOGGER.error("[FixedStructures] could not apply {}: {}", file, e.toString());
        }
        return count;
    }

    private static boolean isLoose(String id) { return id.equals("minecraft:water") || id.equals("minecraft:air") || id.equals("minecraft:cave_air"); }

    private static String idAt(ServerLevel level, int x, int y, int z) {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()));
    }

    private static void setBlock(ServerLevel level, int x, int y, int z, String blockId) {
        Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (b != null) level.setBlock(new BlockPos(x, y, z), b.defaultBlockState(), 3);
    }

    /**
     * Sea anchor: under the keel (ice in the template's bottom layers), fill with `groundBlock` down to the sea floor if
     * it is close, so a berg that nearly touches bottom sits on it. Only the keel, so the rest of the berg keeps its shape.
     */
    private static int groundSea(ServerLevel level, Box b, int y0, Profile p) {
        int filled = 0;
        for (int x = b.x1(); x <= b.x2(); x++) {
            for (int z = b.z1(); z <= b.z2(); z++) {
                Integer bottom = null;
                for (int y = y0; y < y0 + GROUND_KEEL_LAYERS; y++) {
                    String id = idAt(level, x, y, z);
                    if (id.endsWith("ice") && !id.equals("minecraft:frosted_ice")) { bottom = y; break; }
                    if (!isLoose(id)) break;    // sea floor already inside the template volume
                }
                if (bottom == null) continue;
                Integer floor = null;
                for (int y = bottom - 1; y >= bottom - p.groundDepth - 1; y--) {
                    if (!isLoose(idAt(level, x, y, z))) { floor = y; break; }
                }
                if (floor == null) continue;
                for (int y = floor + 1; y < bottom; y++) { setBlock(level, x, y, z, p.groundBlock); filled++; }
            }
        }
        return filled;
    }

    /** Surface anchor, foundation 'columns': continue each solid bottom-layer column down to the ground. */
    private static int columnFoundation(ServerLevel level, Box b, int y0, Profile p) {
        int filled = 0;
        for (int x = b.x1(); x <= b.x2(); x++) {
            for (int z = b.z1(); z <= b.z2(); z++) {
                if (level.getBlockState(new BlockPos(x, y0, z)).canBeReplaced()) continue;
                for (int y = y0 - 1; y >= y0 - p.foundationDepth; y--) {
                    if (!level.getBlockState(new BlockPos(x, y, z)).canBeReplaced()) break;
                    setBlock(level, x, y, z, p.foundationBlock);
                    filled++;
                }
            }
        }
        return filled;
    }

    private static boolean loaded(ServerLevel level, Box b) {
        return level.isLoaded(new BlockPos(b.x1(), 0, b.z1())) && level.isLoaded(new BlockPos(b.x2(), 0, b.z1()))
                && level.isLoaded(new BlockPos(b.x1(), 0, b.z2())) && level.isLoaded(new BlockPos(b.x2(), 0, b.z2()));
    }

    private static int run(MinecraftServer server, String cmd) { return Cmd.result(server, "execute in minecraft:overworld run " + cmd); }

    private static void place(ServerLevel level, Placement s, int[] size, Box b) {
        MinecraftServer server = level.getServer();
        Profile p = s.profile;
        int y0;
        if (p.anchor.equals("sea")) {
            y0 = level.getSeaLevel() - 1 - p.waterline;
        } else {
            y0 = surface(level, b) - p.groundLayers;
            // clear trees and snow inside the volume above the template's ground, then fill gaps under it
            if (p.clear) run(server, "fill " + b.x1() + " " + (y0 + p.groundLayers) + " " + b.z1() + " " + b.x2() + " " + (y0 + size[1] - 1) + " " + b.z2() + " minecraft:air");
            if (p.foundation.equals("fill")) run(server, "fill " + b.x1() + " " + (y0 - p.foundationDepth) + " " + b.z1() + " " + b.x2() + " " + (y0 - 1) + " " + b.z2() + " minecraft:dirt replace #minecraft:replaceable");
        }
        Origin o = origin(b, s);
        if (run(server, "place template " + s.template + " " + o.x() + " " + y0 + " " + o.z() + " " + s.rotation + " none") == 0) {
            FAILED.add(s.key());
            WinterheartStructures.LOGGER.error("[FixedStructures] /place template {} failed at {} {} {}", s.template, o.x(), y0, o.z());
            return;
        }
        int fixed = applyFixups(level, s, o, y0);
        int grounded = 0;
        if (p.anchor.equals("sea") && p.groundDepth > 0) grounded = groundSea(level, b, y0, p);
        else if (p.anchor.equals("surface") && p.foundation.equals("columns")) grounded = columnFoundation(level, b, y0, p);
        run(server, "forceload remove " + b.x1() + " " + b.z1() + " " + b.x2() + " " + b.z2());
        WorldState.tag(server).putBoolean(s.key(), true);
        WorldState.save(server);
        WinterheartStructures.LOGGER.info("[FixedStructures] Placed {} at {} {} {} .. {} {} {} (rotation {}, {} fixups, {} grounding blocks)", s.template,
                b.x1(), y0, b.z1(), b.x2(), y0 + size[1] - 1, b.z2(), s.rotation, fixed, grounded);
    }

    /** Force-loads each pending site so it is placed right away, before a player can see the terrain without it. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (Placement s : StructureConfig.placements) {
            if (placed(server, s)) continue;
            int[] size = size(server, s);
            if (size == null) {
                FAILED.add(s.key());
                WinterheartStructures.LOGGER.error("[FixedStructures] template {} not found", s.template);
                continue;
            }
            Box b = box(s, size);
            run(server, "forceload add " + b.x1() + " " + b.z1() + " " + b.x2() + " " + b.z2());
        }
    }

    /** One check of every pending placement (public so tests can drive it). */
    public static void pass(ServerLevel level) {
        for (Placement s : StructureConfig.placements) {
            if (FAILED.contains(s.key()) || placed(level.getServer(), s)) continue;
            int[] size = size(level.getServer(), s);
            if (size == null) continue;
            Box b = box(s, size);
            if (loaded(level, b)) place(level, s, size, b);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide || event.level.dimension() != Level.OVERWORLD) return;
        if (event.level.getGameTime() % 20 != 0) return;
        pass((ServerLevel) event.level);
    }
}
