package io.github.soldierdonkey2.winterheart_fishing.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import io.github.soldierdonkey2.winterheart_fishing.cast.CastCharging;
import io.github.soldierdonkey2.winterheart_fishing.fishing.FishingSession;
import io.github.soldierdonkey2.winterheart_fishing.fishing.HookAccess;

@Mixin(FishingHook.class)
public abstract class FishingHookMixin implements HookAccess {
    @Unique private final FishingSession winterheart$session = new FishingSession((FishingHook) (Object) this);
    @Unique private boolean winterheart$caughtFish;

    @Shadow private void catchingFish(BlockPos pos) {}

    @Override
    public FishingSession winterheart$session() {
        return winterheart$session;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V", at = @At("TAIL"))
    private void winterheart$scaleCast(Player player, Level level, int luck, int lureSpeed, CallbackInfo ci) {
        FishingHook hook = (FishingHook) (Object) this;
        hook.setDeltaMovement(CastCharging.scale(hook.getDeltaMovement(), player));
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/FishingHook;catchingFish(Lnet/minecraft/core/BlockPos;)V"))
    private void winterheart$catching(FishingHook hook, BlockPos pos) {
        winterheart$session.tickCatching(() -> catchingFish(pos));
    }

    /** Vanilla draws the trail of an imaginary fish; the session draws the real one's. */
    @Redirect(method = "catchingFish", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean winterheart$noImaginaryFish(BlockState state, Block block) {
        return state.is(block) && winterheart$session.target() == null;
    }

    @Redirect(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> winterheart$fishingLoot(LootTable table, LootParams params) {
        FishingSession.Catch result = winterheart$session.reelIn(table.getRandomItems(params));
        winterheart$caughtFish = result.caughtFish();
        return result.items();
    }

    /** The second spawn in the item loop is the experience orb: one per caught fish, none for junk alone. */
    @Redirect(method = "retrieve", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean winterheart$experience(Level level, Entity orb) {
        if (!winterheart$caughtFish) return false;
        winterheart$caughtFish = false;
        return level.addFreshEntity(orb);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void winterheart$release(Entity.RemovalReason reason, CallbackInfo ci) {
        winterheart$session.onRemove();
    }
}
