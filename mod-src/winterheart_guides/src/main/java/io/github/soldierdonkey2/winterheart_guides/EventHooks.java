package io.github.soldierdonkey2.winterheart_guides;

import io.github.soldierdonkey2.winterheart.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Forge events -> guide event triggers, plus the engine's server tick (file reload every 2 s, sweeps twice a second). */
public final class EventHooks {
    private EventHooks() {}

    // ---- payloads

    private static GuideManager.Payload block(BlockState s) {
        GuideManager.Payload p = new GuideManager.Payload();
        p.id = Sweeps.blockId(s);
        p.hasTag = t -> s.is(TagKey.create(Registries.BLOCK, new ResourceLocation(t)));
        return p;
    }

    private static GuideManager.Payload item(ItemStack s) {
        GuideManager.Payload p = new GuideManager.Payload();
        p.id = Books.idOf(s);
        p.hasTag = t -> s.is(TagKey.create(Registries.ITEM, new ResourceLocation(t)));
        p.stack = s;
        return p;
    }

    private static GuideManager.Payload entity(Entity e) {
        GuideManager.Payload p = new GuideManager.Payload();
        p.id = Sweeps.entityId(e);
        p.hasTag = t -> e.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(t)));
        return p;
    }

    /** A damage source has a message id ('inFire') and a registry id ('minecraft:in_fire'); accept either. */
    static List<String> damageIds(DamageSource source) {
        List<String> ids = new ArrayList<>();
        ids.add(source.getMsgId());
        source.typeHolder().unwrapKey().ifPresent(k -> {
            String key = k.location().toString();
            if (!ids.contains(key)) ids.add(key);
            String path = k.location().getPath();
            if (!ids.contains(path)) ids.add(path);
        });
        for (String i : new ArrayList<>(ids)) if (!i.contains(":") && !ids.contains("minecraft:" + i)) ids.add("minecraft:" + i);
        return ids;
    }

    private static ServerPlayer sp(Object o) { return o instanceof ServerPlayer p ? p : null; }

    // ---- events

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        GuideManager.checkDataFile(null, true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        int tick = event.getServer().getTickCount();
        if (tick % GuideManager.RELOAD_CHECK_TICKS == 0) GuideManager.checkDataFile(event.getServer(), false);
        if (tick % GuideManager.SWEEP_INTERVAL_TICKS == 0) GuideManager.runSweep(event.getServer());
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent e) {
        GuideManager.dispatch("block_broken", sp(e.getPlayer()), () -> block(e.getState()));
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent e) {
        GuideManager.dispatch("block_placed", sp(e.getEntity()), () -> block(e.getPlacedBlock()));
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide) return;
        BlockState s = e.getLevel().getBlockState(e.getPos());
        GuideManager.dispatch("block_used", sp(e.getEntity()), () -> block(s));
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide || !(e.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = e.getItemStack();
        GuideManager.dispatch("item_used", player, () -> item(stack));
        if (stack.is(ModItems.GUIDE_BOOK.get())) {
            CompoundTag tag = stack.getTag();
            String bookId = tag != null && tag.contains("guidebook") ? tag.getString("guidebook") : null;
            Books.openFullBook(player, e.getHand(), false, bookId);
        }
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent e) {
        GuideManager.dispatch("item_crafted", sp(e.getEntity()), () -> item(e.getCrafting()));
    }

    @SubscribeEvent
    public static void onSmelted(PlayerEvent.ItemSmeltedEvent e) {
        GuideManager.dispatch("item_smelted", sp(e.getEntity()), () -> item(e.getSmelting()));
    }

    @SubscribeEvent
    public static void onPickedUp(PlayerEvent.ItemPickupEvent e) {
        GuideManager.dispatch("item_picked_up", sp(e.getEntity()), () -> item(e.getStack()));
    }

    @SubscribeEvent
    public static void onEaten(LivingEntityUseItemEvent.Finish e) {
        if (e.getItem().isEdible()) GuideManager.dispatch("item_eaten", sp(e.getEntity()), () -> item(e.getItem()));
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent e) {
        GuideManager.dispatch("advancement", sp(e.getEntity()), () -> {
            GuideManager.Payload p = new GuideManager.Payload();
            p.id = e.getAdvancement().getId().toString();
            return p;
        });
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = e.getSource();
        if (victim instanceof ServerPlayer player) {
            GuideManager.dispatch("player_hurt", player, () -> {
                GuideManager.Payload p = new GuideManager.Payload();
                p.ids = damageIds(source);
                p.id = p.ids.get(0);
                p.attacker = source.getEntity();
                p.damage = (double) e.getAmount();
                return p;
            });
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker) {
            GuideManager.dispatch("entity_hurt", attacker, () -> {
                GuideManager.Payload p = entity(victim);
                p.damage = (double) e.getAmount();
                return p;
            });
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = e.getSource();
        if (victim instanceof ServerPlayer player) {
            GuideManager.dispatch("player_died", player, () -> {
                GuideManager.Payload p = new GuideManager.Payload();
                p.ids = damageIds(source);
                p.id = p.ids.get(0);
                p.attacker = source.getEntity();
                return p;
            });
            return;
        }
        if (source.getEntity() instanceof ServerPlayer killer) GuideManager.dispatch("entity_killed", killer, () -> entity(victim));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) GuideManager.onLoggedOut(p);
    }
}
