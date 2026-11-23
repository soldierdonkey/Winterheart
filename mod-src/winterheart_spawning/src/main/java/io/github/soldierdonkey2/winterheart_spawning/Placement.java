package io.github.soldierdonkey2.winterheart_spawning;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Finding somewhere a mob fits: block classification, hitbox fit checks, surface and cave candidates. */
final class Placement {
    private static final double DEG_TO_RAD = 0.017453292519943295;

    private Placement() {}

    record Pos(double x, double y, double z) {}
    record Fit(boolean valid, Pos pos, String reason) {}
    record Match(Pos pos, SpawnConfig.Template template, String reason) {}
    record Coords(int x, int y, int z, double yaw) {}

    static Coords coords(ServerPlayer p) {
        return new Coords((int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()), p.getYRot());
    }

    /** The block at a position, or null when it is out of the world's height or its chunk isn't loaded (never loads chunks). */
    static BlockState block(ServerLevel level, int x, int y, int z) {
        if (y < -64 || y > 320) return null;
        BlockPos pos = new BlockPos(x, y, z);
        return level.isLoaded(pos) ? level.getBlockState(pos) : null;
    }

    static String id(BlockState s) { return String.valueOf(ForgeRegistries.BLOCKS.getKey(s.getBlock())); }

    static boolean isPassable(BlockState b) {
        if (b == null) return true;
        String id = id(b);
        if (id.endsWith("air")) return true;
        return id.equals("minecraft:snow") || id.equals("minecraft:grass") || id.equals("minecraft:tall_grass") || id.equals("minecraft:fern")
                || id.equals("minecraft:large_fern") || id.endsWith("_flower") || id.contains("sapling") || id.contains("mushroom") || id.contains("bush");
    }

    static boolean isSolidFloor(BlockState b) {
        if (b == null) return false;
        String id = id(b);
        if (id.endsWith("air") || isPassable(b) || id.contains("leaves")) return false;
        return !(id.contains("water") || id.contains("lava") || id.contains("powder_snow"));
    }

    static Fit testFit(ServerLevel level, int x, int y, int z, SpawnConfig.Template t) {
        if (y < -60 || y + t.height() > 318) return new Fit(false, null, "Out of bounds Y=" + y);
        int[][] offsets = t.width() == 2 ? new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}} : new int[][]{{0, 0}};
        for (int[] o : offsets) {
            BlockState ground = block(level, x + o[0], y - 1, z + o[1]);
            if (ground == null) return new Fit(false, null, "Unloaded chunk floor");
            if (!isSolidFloor(ground)) return new Fit(false, null, "Unstable floor at [" + o[0] + ", " + o[1] + "]: " + id(ground));
        }
        for (int[] o : offsets) {
            for (int dy = 0; dy < t.height(); dy++) {
                BlockState b = block(level, x + o[0], y + dy, z + o[1]);
                if (b == null) return new Fit(false, null, "Unloaded chunk space");
                if (!isPassable(b)) return new Fit(false, null, "Obstructed at [" + o[0] + ", +" + dy + ", " + o[1] + "]: " + id(b));
            }
        }
        double center = t.width() == 2 ? 1.0 : 0.5;
        return new Fit(true, new Pos(x + center, y, z + center), "Clear");
    }

    /** The Y just above the topmost solid block of a column, searching down from near the player; -64 if there is none or it is liquid. */
    static int surfaceY(ServerLevel level, int x, int z, int playerY) {
        int py = playerY == 0 ? 64 : playerY;
        int startY = Math.min(319, Math.max(py + 35, 100));
        int minY = Math.max(-60, SpawnConfig.surfaceY - 8);
        for (int y = startY; y >= minY; y--) {
            BlockState b = block(level, x, y, z);
            if (b == null) continue;
            if (isPassable(b) || id(b).contains("leaves")) continue;
            if (isSolidFloor(b)) return y + 1;
            String id = id(b);
            if (id.contains("water") || id.contains("lava") || id.contains("powder_snow")) return -64;
        }
        return -64;
    }

    static Match selectFitting(ServerLevel level, int x, int y, int z, List<String> pool, Map<String, SpawnConfig.Template> templates) {
        List<String> candidates = new ArrayList<>(pool);
        Collections.shuffle(candidates);
        for (String key : candidates) {
            SpawnConfig.Template t = templates.get(key);
            if (t == null) continue;
            Fit fit = testFit(level, x, y, z, t);
            if (fit.valid()) return new Match(fit.pos(), t, "OK");
        }
        return null;
    }

    static boolean isDarkEnough(ServerLevel level, Pos pos) {
        BlockPos bp = new BlockPos((int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
        if (!level.isLoaded(bp)) return false;
        return level.getMaxLocalRawBrightness(bp) <= SpawnConfig.maxSpawnLight;
    }

    /** Caves: behind the player (opposite their facing, +-25 degrees), minR to maxR away, within 4 blocks of their height. */
    static Match findCave(ServerLevel level, Coords p, int minR, int maxR, List<String> pool) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int attempt = 1; attempt <= 8; attempt++) {
            double r = minR + rnd.nextDouble() * (maxR - minR);
            double angle = (p.yaw() + 180 + (rnd.nextDouble() * 50 - 25)) * DEG_TO_RAD;
            int tx = (int) Math.floor(p.x() - Math.sin(angle) * r), tz = (int) Math.floor(p.z() + Math.cos(angle) * r);
            long distSq = (long) (tx - p.x()) * (tx - p.x()) + (long) (tz - p.z()) * (tz - p.z());
            if (distSq < (long) minR * minR) continue;
            for (int dy = -4; dy <= 4; dy++) {
                Match m = selectFitting(level, tx, p.y() + dy, tz, pool, SpawnConfig.mobs);
                if (m != null) return m;
            }
        }
        return new Match(null, null, "No footing found");
    }

    /** Surface: a random point minR to maxR away at the column's surface, on or above SURFACE_Y - 5. */
    static Match findSurface(ServerLevel level, Coords p, int minR, int maxR, List<String> pool, Map<String, SpawnConfig.Template> templates) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        String last = "No candidate found";
        for (int attempt = 1; attempt <= 12; attempt++) {
            double r = minR + rnd.nextDouble() * (maxR - minR), angle = rnd.nextDouble() * 6.283185307179586;
            int tx = (int) Math.floor(p.x() + Math.cos(angle) * r), tz = (int) Math.floor(p.z() + Math.sin(angle) * r);
            long distSq = (long) (tx - p.x()) * (tx - p.x()) + (long) (tz - p.z()) * (tz - p.z());
            if (distSq < (long) minR * minR) continue;
            int top = surfaceY(level, tx, tz, p.y());
            if (top < SpawnConfig.surfaceY - 5) { last = "Surface Y=" + top + " below threshold (" + (SpawnConfig.surfaceY - 5) + ")"; continue; }
            Match m = selectFitting(level, tx, top, tz, pool, templates);
            if (m != null) return m;
            last = "No candidate mob fits terrain opening";
        }
        return new Match(null, null, last);
    }
}
