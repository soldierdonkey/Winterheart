package io.github.soldierdonkey2.winterheart_guides;

import io.github.soldierdonkey2.winterheart.world.WorldState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Sweep triggers: conditions checked twice a second for every player (what they stand on, look at, carry, ...). */
public final class Sweeps {
    public static final int LOOK_DISTANCE = 16;

    private Sweeps() {}

    public record Time(String phase, int day) {}
    public record Look(BlockState block, Entity entity, Vec3 hit) {}

    /** What the scripts could ask about a player, computed lazily and at most once per sweep. */
    public static final class Ctx {
        public final ServerPlayer player;
        public final MinecraftServer server;
        private final Map<String, Object> memo = new HashMap<>();

        Ctx(ServerPlayer player) { this.player = player; this.server = player.server; }

        @SuppressWarnings("unchecked")
        private <T> T once(String key, Supplier<T> fn) {
            if (!memo.containsKey(key)) {
                try { memo.put(key, fn.get()); }
                catch (RuntimeException e) { memo.put(key, null); WinterheartGuides.LOGGER.error("[Guides] sweep lookup '{}' failed for {}", key, player.getGameProfile().getName(), e); }
            }
            return (T) memo.get(key);
        }

        /** The block the player stands on (null while airborne). */
        public BlockState standingOn() {
            return once("on", () -> {
                if (!player.onGround()) return null;
                int x = (int) Math.floor(player.getX()), z = (int) Math.floor(player.getZ());
                int y = (int) Math.floor(player.getY() - 0.2);
                BlockState b = player.level().getBlockState(new BlockPos(x, y, z));
                // tall collision shapes (fences) put the player in the air block above them
                if (b.isAir()) b = player.level().getBlockState(new BlockPos(x, y - 1, z));
                return b;
            });
        }

        public BlockState standingIn() { return once("in", () -> player.level().getBlockState(player.blockPosition())); }

        public Time time() { return once("time", () -> timeState(server)); }

        /** What the crosshair touches within LOOK_DISTANCE: the nearer of a block and an entity. */
        public Look look() {
            return once("look", () -> {
                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getLookAngle().scale(LOOK_DISTANCE));
                BlockHitResult blockHit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                double blockDist = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : eye.distanceToSqr(blockHit.getLocation());
                AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(LOOK_DISTANCE)).inflate(1.0);
                EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, box, e -> !e.isSpectator() && e.isPickable(), Math.min(blockDist, (double) LOOK_DISTANCE * LOOK_DISTANCE));
                if (entityHit != null) return new Look(null, entityHit.getEntity(), entityHit.getLocation());
                if (blockHit.getType() == HitResult.Type.MISS) return new Look(null, null, null);
                return new Look(player.level().getBlockState(blockHit.getBlockPos()), null, blockHit.getLocation());
            });
        }

        /** Real eye-to-hit distance in blocks (infinity if nothing was hit). */
        public double lookDistance() {
            Look l = look();
            return l == null || l.hit() == null ? Double.POSITIVE_INFINITY : player.getEyePosition().distanceTo(l.hit());
        }

        public List<ItemStack> stacks() {
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty()) out.add(s);
            }
            return out;
        }
    }

    public static Ctx ctx(ServerPlayer player) { return new Ctx(player); }

    /** { phase, day } of the custom time system, kept in the world state. */
    public static Time timeState(MinecraftServer server) {
        CompoundTag data = WorldState.tag(server);
        String phase = data.getString("current_phase");
        int day = data.getInt("custom_day");
        return new Time(phase.isEmpty() ? "DAWN" : phase, day == 0 ? 1 : day);
    }

    // ---- matching helpers

    public static String blockId(BlockState s) { return String.valueOf(ForgeRegistries.BLOCKS.getKey(s.getBlock())); }

    public static boolean matchBlock(Spec spec, BlockState s) {
        return spec.matches(blockId(s), t -> s.is(TagKey.create(Registries.BLOCK, new ResourceLocation(t))), null);
    }

    public static boolean matchItem(Spec spec, ItemStack s) {
        return spec.matches(Books.idOf(s), t -> s.is(TagKey.create(Registries.ITEM, new ResourceLocation(t))), s);
    }

    public static String entityId(Entity e) { return String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType())); }

    public static boolean matchEntity(Spec spec, Entity e) {
        return spec.matches(entityId(e), t -> e.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(t))), null);
    }

    public static boolean holding(ServerPlayer p, Spec spec, String hand) {
        List<ItemStack> stacks = new ArrayList<>();
        if (!"off".equals(hand)) stacks.add(p.getItemInHand(InteractionHand.MAIN_HAND));
        if (!"main".equals(hand)) stacks.add(p.getItemInHand(InteractionHand.OFF_HAND));
        for (ItemStack s : stacks) if (!s.isEmpty() && matchItem(spec, s)) return true;
        return false;
    }

    /** The optional `holding` requirement any trigger may carry. World-level events have no player: any online player satisfies it. */
    public static boolean requirementOk(GuideData.Trigger t, ServerPlayer player, MinecraftServer server) {
        if (t.holding == null) return true;
        if (player != null) return holding(player, t.holding, t.hand);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (holding(p, t.holding, t.hand)) return true;
        return false;
    }

    public static boolean dayInRange(GuideData.Trigger t, int day) {
        return (t.minDay == null || day >= t.minDay) && (t.maxDay == null || day <= t.maxDay);
    }

    private static boolean inArea(GuideData.Trigger t, ServerPlayer p) {
        if (t.dimension != null && !p.level().dimension().location().toString().equals(t.dimension)) return false;
        if (t.minY != null && p.getY() < t.minY) return false;
        if (t.maxY != null && p.getY() > t.maxY) return false;
        if (t.x != null && t.z != null) {
            double dx = p.getX() - t.x, dz = p.getZ() - t.z, r = t.radius == null ? 16 : t.radius;
            if (dx * dx + dz * dz > r * r) return false;
        }
        return true;
    }

    private static int countItems(Ctx ctx, Spec spec) {
        int total = 0;
        for (ItemStack s : ctx.stacks()) if (matchItem(spec, s)) total += s.getCount();
        return total;
    }

    public static boolean eval(GuideData.Trigger t, Ctx ctx) {
        boolean result;
        switch (t.sweep) {
            case "standing_on": { BlockState b = ctx.standingOn(); result = b != null && matchBlock(t.match, b); break; }
            case "standing_in": { BlockState b = ctx.standingIn(); result = b != null && matchBlock(t.match, b); break; }
            case "looking_at_block": {
                Look l = ctx.look();
                result = l != null && l.block() != null && (t.distance == null || ctx.lookDistance() <= t.distance) && matchBlock(t.match, l.block());
                break;
            }
            case "looking_at_entity": {
                Look l = ctx.look();
                result = l != null && l.entity() != null && (t.distance == null || ctx.lookDistance() <= t.distance) && matchEntity(t.match, l.entity());
                break;
            }
            case "holding": result = holding(ctx.player, t.item, t.hand); break;
            case "time": { Time tm = ctx.time(); result = tm != null && t.match.matches(tm.phase(), null, null) && dayInRange(t, tm.day()); break; }
            case "position": result = inArea(t, ctx.player); break;
            case "inventory": result = countItems(ctx, t.item) >= (t.count == null ? 1 : t.count); break;
            case "custom": { var fn = GuideFunctions.get(t.testFn); result = fn != null && fn.test(ctx); break; }
            default: return false;
        }
        if (!result || !requirementOk(t, ctx.player, ctx.server)) return false;
        if (t.whereFn == null) return true;
        var where = GuideFunctions.get(t.whereFn);
        return where != null && where.test(ctx);
    }

    /** A one-line description of what the sweeps see for this player (action bar trace, /guides probe). */
    public static Map<String, String> snapshot(Ctx ctx) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        BlockState on = ctx.standingOn(), in = ctx.standingIn();
        Look l = ctx.look();
        Time tm = ctx.time();
        ServerLevel level = ctx.player.serverLevel();
        m.put("standingOn", on == null ? "-" : blockId(on));
        m.put("standingIn", in == null ? "-" : blockId(in));
        m.put("position", (int) Math.floor(ctx.player.getX()) + " " + (int) Math.floor(ctx.player.getY()) + " " + (int) Math.floor(ctx.player.getZ()) + " in " + level.dimension().location());
        m.put("block", l != null && l.block() != null ? blockId(l.block()) : "-");
        m.put("entity", l != null && l.entity() != null ? entityId(l.entity()) : "-");
        m.put("time", tm == null ? "-" : tm.phase() + " day " + tm.day());
        m.put("distance", l != null && l.hit() != null ? String.valueOf(Math.round(ctx.lookDistance() * 10) / 10.0) : "0");
        return m;
    }
}
