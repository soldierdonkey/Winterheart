package io.github.soldierdonkey2.winterheart_spawning;

import io.github.soldierdonkey2.winterheart.api.Cmd;
import io.github.soldierdonkey2.winterheart.api.TestMode;
import io.github.soldierdonkey2.winterheart.api.Ticks;
import io.github.soldierdonkey2.winterheart.world.WorldState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The scripted mob spawner. Vanilla spawning is switched off (`doMobSpawning false`); this places mobs itself.
 * - Hostiles (every second, per player): cap and rules depend on the day (see config.json); caves spawn behind the player,
 *   the surface 24-60 blocks away, only where it is dark; far, untouched ones are reaped when over the cap.
 * - Livestock (every 4 seconds): flocks of 2-4 near the player, a quota per day, none after day 30.
 * - Wendigo: one on each of the configured days, after dusk, 40-60 blocks from a random player; natural wendigo spawns are blocked.
 * Spawned mobs get the tags `synthetic_hostile` / `synthetic_livestock` / `scripted_wendigo`; `interacted` marks hostiles that were hurt.
 */
public final class Spawner {
    private Spawner() {}

    private static void trace(String stage, ServerPlayer p, String msg) {
        if (SpawnConfig.debugVerbose) WinterheartSpawning.LOGGER.info("[Spawner:{}]{} {}", stage, p != null ? " [" + p.getGameProfile().getName() + "]" : "", msg);
    }

    private static int day(CompoundTag d) { int v = d.getInt("custom_day"); return v == 0 ? 1 : v; }

    private static String phase(CompoundTag d) { String p = d.getString("current_phase"); return p.isEmpty() ? "DAWN" : p; }

    private static Entity create(ServerLevel level, String id) {
        EntityType<?> type = EntityType.byString(id).orElse(null);
        return type == null ? null : type.create(level);
    }

    private static String fmt(Placement.Pos p) { return String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", p.x(), p.y(), p.z()); }

    // ---- hostiles

    /** One hostile pass over the overworld (public so tests can drive it). */
    public static void hostilePass(ServerLevel level) {
        CompoundTag data = WorldState.tag(level.getServer());
        int day = day(data);
        String phase = phase(data);
        int cap = SpawnConfig.hostileCap(day);

        for (ServerPlayer player : new ArrayList<>(level.players())) {
            Placement.Coords pc = Placement.coords(player);
            boolean cave = pc.y() < SpawnConfig.surfaceY;
            boolean night = phase.equals("NIGHT") || phase.equals("MIDNIGHT") || phase.equals("ETERNAL_NIGHT") || phase.equals("DUSK");

            List<Entity> nearby = new ArrayList<>();
            for (Entity e : level.getAllEntities()) {
                if (e.isAlive() && e.getTags().contains("synthetic_hostile") && e.distanceTo(player) <= SpawnConfig.despawnRadius) nearby.add(e);
            }

            // reaper: cull the farthest hostile nobody has touched
            if (nearby.size() >= cap) {
                nearby.stream().filter(e -> !e.getTags().contains("interacted"))
                        .max(Comparator.comparingDouble(e -> e.distanceTo(player))).ifPresent(culled -> {
                            culled.discard();
                            trace("Hostile:Reaper", player, String.format(Locale.ROOT, "Reaped distant '%s' at (%.1f, %.1f, %.1f)", ForgeRegistries.ENTITY_TYPES.getKey(culled.getType()), culled.getX(), culled.getY(), culled.getZ()));
                        });
                continue;
            }

            if (!(day >= 21 || cave || (day >= 11 && night))) continue;

            List<String> pool = cave ? SpawnConfig.hostileCave : SpawnConfig.hostileSurface;
            Placement.Match result = cave ? Placement.findCave(level, pc, 8, 16, pool) : Placement.findSurface(level, pc, 24, 60, pool, SpawnConfig.mobs);
            if (result.pos() == null || result.template() == null) { trace("Hostile:Spatial", player, "Spawning failed: " + result.reason()); continue; }
            if (!Placement.isDarkEnough(level, result.pos())) { trace("Hostile:Light", player, "Spawn rejected: too bright at " + fmt(result.pos())); continue; }

            Entity entity = create(level, result.template().id());
            if (entity == null) continue;
            entity.setPos(result.pos().x(), result.pos().y(), result.pos().z());
            entity.addTag("synthetic_hostile");
            if (result.template().baby()) {
                if (entity instanceof Zombie z) z.setBaby(true);
                else if (entity instanceof AgeableMob a) a.setBaby(true);
            }
            level.addFreshEntity(entity);
            trace("Hostile:Success", player, String.format(Locale.ROOT, "SUCCESS: Spawned '%s' (dim: %dx%d) at %s [%.1fm away | Yaw: %.1f]", result.template().name(), result.template().width(), result.template().height(), fmt(result.pos()),
                    Math.sqrt(Math.pow(result.pos().x() - pc.x(), 2) + Math.pow(result.pos().z() - pc.z(), 2)), pc.yaw()));
        }
    }

    // ---- livestock

    public static void livestockPass(ServerLevel level) {
        CompoundTag data = WorldState.tag(level.getServer());
        int day = day(data);
        String phase = phase(data);
        if (day > 30 || phase.equals("MIDNIGHT") || phase.equals("ETERNAL_NIGHT")) return;
        int quota = SpawnConfig.livestockQuota(day);

        for (ServerPlayer player : new ArrayList<>(level.players())) {
            Placement.Coords pc = Placement.coords(player);
            if (pc.y() < SpawnConfig.surfaceY - 5) continue;
            int animals = 0;
            for (Entity e : level.getAllEntities()) if (e.isAlive() && e instanceof Animal && e.distanceTo(player) <= 60) animals++;
            if (animals >= quota) continue;

            Placement.Match center = Placement.findSurface(level, pc, 24, 60, SpawnConfig.livestockPool, SpawnConfig.livestock);
            if (center.pos() == null || center.template() == null) { trace("Livestock:Spatial", player, "Flock anchor rejected: " + center.reason()); continue; }

            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            int groupSize = day >= 21 ? 2 : 2 + rnd.nextInt(3);
            int spawned = 0;
            for (int i = 0; i < groupSize; i++) {
                int ox = (int) Math.floor(center.pos().x() + (rnd.nextDouble() * 4 - 2)), oz = (int) Math.floor(center.pos().z() + (rnd.nextDouble() * 4 - 2));
                int groundY = Placement.surfaceY(level, ox, oz, pc.y());
                if (groundY < SpawnConfig.surfaceY - 5) continue;
                Placement.Fit fit = Placement.testFit(level, ox, groundY, oz, center.template());
                if (!fit.valid()) continue;
                Entity animal = create(level, center.template().id());
                if (animal == null) continue;
                animal.setPos(fit.pos().x(), fit.pos().y(), fit.pos().z());
                animal.addTag("synthetic_livestock");
                level.addFreshEntity(animal);
                spawned++;
            }
            trace("Livestock:Success", player, "Flock spawned: " + spawned + "/" + groupSize + " '" + center.template().name() + "' near " + fmt(center.pos()));
        }
    }

    // ---- wendigo

    public static void wendigoPass(ServerLevel level) {
        CompoundTag data = WorldState.tag(level.getServer());
        int day = day(data);
        if (!SpawnConfig.wendigoDays.contains(day)) return;
        String phase = phase(data);
        if (!(phase.equals("DUSK") || phase.equals("NIGHT") || phase.equals("MIDNIGHT") || phase.equals("ETERNAL_NIGHT"))) return;   // only after dusk
        String flag = "wendigo_spawned_day_" + day;
        if (data.getBoolean(flag)) return;
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        ServerPlayer player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        Placement.Match result = Placement.findSurface(level, Placement.coords(player), 40, 60, List.of("wendigo"), SpawnConfig.mobs);
        if (result.pos() == null || result.template() == null) { trace("Wendigo:Spatial", player, "Day " + day + " spawn failed, retrying: " + result.reason()); return; }

        Entity entity = create(level, result.template().id());
        if (entity == null) return;
        entity.setPos(result.pos().x(), result.pos().y(), result.pos().z());
        entity.addTag("scripted_wendigo");
        if (entity instanceof Mob m) m.setPersistenceRequired();
        level.addFreshEntity(entity);
        data.putBoolean(flag, true);
        WorldState.save(level.getServer());
        trace("Wendigo:Success", player, "Day " + day + ": spawned wendigo at " + fmt(result.pos()));
    }

    // ---- events

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide || event.level.dimension() != Level.OVERWORLD) return;
        long t = Ticks.current();
        if (t % 20 != 0 || TestMode.enabled()) return;
        ServerLevel level = (ServerLevel) event.level;
        hostilePass(level);
        if (t % 80 == 0) livestockPass(level);
        wendigoPass(level);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Cmd.run(event.getServer(), "gamerule doMobSpawning false");
        Cmd.run(event.getServer(), "gamerule wendigoSpawns false");
        Cmd.run(event.getServer(), "gamerule naturalRegeneration false");
        WinterheartSpawning.LOGGER.info("[Spawner] Gamerule doMobSpawning locked to false.");
    }

    /** Audit log of mobs that appear without the spawner (breeding, structures, other mods), and wendigo persistence. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (e.level().isClientSide) return;
        String id = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()));
        if (SpawnConfig.wendigoTypes.contains(id) && e instanceof Mob m) m.setPersistenceRequired();   // never despawn, including the mad/flute swaps
        if (!SpawnConfig.auditLog || !(e instanceof LivingEntity) || e instanceof Player) return;
        if (e.getTags().contains("synthetic_hostile") || e.getTags().contains("synthetic_livestock")) return;
        boolean baby = e instanceof AgeableMob a && a.isBaby();
        WinterheartSpawning.LOGGER.info("[External Mob Spawn] '{}' spawned at ({}, {}, {}){}", id, String.format(Locale.ROOT, "%.1f", e.getX()), String.format(Locale.ROOT, "%.1f", e.getY()), String.format(Locale.ROOT, "%.1f", e.getZ()), baby ? " [Baby/Breeding]" : "");
    }

    /** Hurting or being hurt by a synthetic hostile marks it `interacted`, so the reaper leaves it alone. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        Entity victim = event.getEntity(), attacker = event.getSource().getEntity();
        if (victim != null && victim.getTags().contains("synthetic_hostile")) victim.addTag("interacted");
        if (attacker != null && attacker.getTags().contains("synthetic_hostile")) attacker.addTag("interacted");
    }

    /** No natural wendigo spawns (biome modifier / flute procedure); scripted spawns and the mod's own transformations pass. */
    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        String id = String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(event.getEntity().getType()));
        if (SpawnConfig.wendigoTypes.contains(id) && SpawnConfig.blockedSpawnTypes.contains(event.getSpawnType().name().toLowerCase(Locale.ROOT))) event.setSpawnCancelled(true);
    }
}
