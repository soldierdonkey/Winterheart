package io.github.soldierdonkey2.winterheart_fishing.fishing;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import io.github.soldierdonkey2.winterheart_fishing.Config;
import io.github.soldierdonkey2.winterheart_fishing.mixin.FishingHookAccessor;

/** The fishing state of one bobber: which fish are interested, which one is approaching, and what reeling in yields. */
public class FishingSession {

    private final FishingHook hook;
    private final FishingHookAccessor timers;
    private final List<AbstractFish> lured = new ArrayList<>();
    @Nullable private AbstractFish targetFish = null;
    private int waterTicks = 0;
    private int approachTicks = 0;
    private int fishCheckCooldown = 0;
    private boolean fishNearby = false;

    public FishingSession(FishingHook hook) {
        this.hook = hook;
        this.timers = (FishingHookAccessor) hook;
    }

    @Nullable
    public AbstractFish target() {
        return targetFish;
    }

    /** Replaces vanilla's FishingHook#catchingFish, which runs each server tick while the bobber floats. */
    public void tickCatching(Runnable vanillaCatching) {
        if (waterTicks == 0) rollIntrigue(1);
        if (++waterTicks % Math.max(1, Config.get().intrigueRerollInterval) == 0) {
            expireInterest();
            rollIntrigue(Config.get().intrigueRerollChanceMultiplier);
        }

        if (timers.winterheart$getNibble() > 0) {
            tickBite(vanillaCatching);
            return;
        }

        if (timers.winterheart$getTimeUntilHooked() > 0 && targetFish != null) {
            tickApproach(vanillaCatching);
            return;
        }

        boolean allowWithoutFish = Config.get().allowFishingWithoutFish;
        boolean approaching = timers.winterheart$getTimeUntilHooked() > 0;
        if (!approaching && !allowWithoutFish && !isFishNearby()) return;
        vanillaCatching.run();
        if (!approaching && timers.winterheart$getTimeUntilHooked() > 0 && !selectTarget() && !allowWithoutFish) resetToLure();
    }

    public Catch reelIn(ObjectArrayList<ItemStack> vanillaItems) {
        Player player = hook.getPlayerOwner();
        if (player == null) return new Catch(vanillaItems, false);
        ServerLevel level = (ServerLevel) hook.level();
        ObjectArrayList<ItemStack> items = new ObjectArrayList<>();
        AbstractFish fish = targetFish;
        targetFish = null;
        boolean caughtFish = fish != null && isHooked(fish);
        if (caughtFish) {
            items.addAll(FishingLoot.fish(level, fish, player));
            if (Config.get().junkAndTreasure) {
                vanillaItems.stream().filter(itemStack -> !itemStack.is(ItemTags.FISHES)).forEach(items::add);
            }
            fish.discard();
        } else if (Config.get().allowFishingWithoutFish && Config.get().junkAndTreasure) {
            items.addAll(FishingLoot.junkOrTreasure(level, hook, player));
        }
        return new Catch(items, caughtFish);
    }

    public void onRemove() {
        lured.forEach(this::cleanup);
        lured.clear();
        targetFish = null;
    }

    private void tickBite(Runnable vanillaCatching) {
        if (targetFish != null && !isValidTarget(targetFish)) timers.winterheart$setNibble(1);
        vanillaCatching.run();
        if (timers.winterheart$getNibble() <= 0) endBite();
    }

    private void tickApproach(Runnable vanillaCatching) {
        if (targetFish == null || !isValidTarget(targetFish)) {
            releaseTarget(false);
            if (!selectTarget()) {
                resetToLure();
                return;
            }
        } else if (++approachTicks > Config.get().maxApproachTicks) {
            releaseTarget(true);
            resetToLure();
            return;
        }
        AbstractFish fish = targetFish;
        timers.winterheart$setTimeUntilHooked(hasArrived(fish) ? 1 : 3);
        FishingParticles.approach((ServerLevel) hook.level(), hook, fish);
        vanillaCatching.run();
        if (timers.winterheart$getNibble() > 0) startBite();
    }

    /** Items for a reeled-in bobber; XP is only awarded when a fish was caught. */
    public record Catch(ObjectArrayList<ItemStack> items, boolean caughtFish) {}

    private float lureProgress() {
        // 1.20.1 stores the Lure level (0-3), not ticks
        return Mth.clamp(timers.winterheart$getLureSpeed() / 3F, 0, 1);
    }

    private List<AbstractFish> getFishInIntrigueArea() {
        float lureProgress = lureProgress();
        float radiusMult = Mth.lerp(lureProgress, 1, Config.get().lureBonusMultiplier);
        float depthMult = Mth.lerp(lureProgress, 1, Config.get().lureBonusMultiplier * 2);
        return FishingUtil.eligibleFish(
                hook, Config.get().intrigueRadius * radiusMult, Config.get().intrigueDepth * depthMult
        );
    }

    private List<AbstractFish> getFishInCatchArea() {
        double catchRadius = Config.get().catchRadius;
        return FishingUtil.eligibleFish(hook, catchRadius, catchRadius);
    }

    private void rollIntrigue(float chanceMultiplier) {
        long now = hook.level().getGameTime();
        RandomSource random = hook.level().getRandom();
        float chance = Config.get().intrigueChance * Mth.lerp(lureProgress(), 1, 2) * chanceMultiplier;
        List<AbstractFish> inRange = getFishInIntrigueArea();
        for (AbstractFish fish : inRange) {
            FishState extension = state(fish);
            if (extension.boredUntil > now || FishingUtil.isActive(extension.hook)) continue;
            if (random.nextFloat() < chance) {
                extension.hook = hook;
                extension.interestExpiry = now + randomBetween(
                        random, Config.get().minInterestTicks, Config.get().maxInterestTicks
                );
                track(fish);
            }
        }
    }

    private void expireInterest() {
        long now = hook.level().getGameTime();
        lured.removeIf(fish -> {
            FishState extension = state(fish);
            if (!fish.isAlive() || extension.hook != hook) return true;
            if (fish != targetFish && now > extension.interestExpiry) {
                cleanup(fish);
                extension.boredUntil = now + Config.get().boredTicks;
                return true;
            }
            return false;
        });
    }

    private boolean isFishNearby() {
        if (--fishCheckCooldown <= 0) {
            fishCheckCooldown = 5;
            fishNearby = !getFishInCatchArea().isEmpty();
        }
        return fishNearby;
    }

    private boolean selectTarget() {
        Set<AbstractFish> candidates = new LinkedHashSet<>(getFishInCatchArea());
        for (AbstractFish fish : getFishInIntrigueArea()) {
            if (state(fish).hook == hook) candidates.add(fish);
        }
        candidates.removeIf(fish -> {
            FishingHook fishHook = state(fish).hook;
            return fishHook != hook && FishingUtil.isTargetOf(fish, fishHook);
        });
        if (candidates.isEmpty()) return false;

        AbstractFish selected = pickWeighted(new ArrayList<>(candidates));
        FishState extension = state(selected);
        extension.hook = hook;
        extension.interestExpiry = hook.level().getGameTime() + Math.max(
                Config.get().minInterestTicks,
                Config.get().maxInterestTicks
        );
        track(selected);
        targetFish = selected;
        approachTicks = 0;
        return true;
    }

    private AbstractFish pickWeighted(List<AbstractFish> fishes) {
        double[] weights = new double[fishes.size()];
        double total = 0;
        for (int i = 0; i < fishes.size(); i++) {
            AbstractFish fish = fishes.get(i);
            double weight = state(fish).hook == hook ? Config.get().intriguedFishWeight : 1;
            weights[i] = weight / (1 + fish.distanceTo(hook));
            total += weights[i];
        }
        double roll = hook.level().getRandom().nextDouble() * total;
        for (int i = 0; i < fishes.size(); i++) {
            roll -= weights[i];
            if (roll < 0) return fishes.get(i);
        }
        return fishes.get(fishes.size() - 1);
    }

    private boolean isValidTarget(AbstractFish fish) {
        return fish.isAlive() && fish.level() == hook.level() && state(fish).hook == hook;
    }

    private boolean hasArrived(AbstractFish fish) {
        double arrivalDistance = Config.get().arrivalDistance;
        return fish.distanceToSqr(FishingUtil.approachTarget(hook)) <= arrivalDistance * arrivalDistance;
    }

    private boolean isHooked(AbstractFish fish) {
        double maxDistance = Config.get().catchRadius + 1;
        return timers.winterheart$getNibble() > 0 &&
                isValidTarget(fish) &&
                state(fish).caught &&
                fish.distanceToSqr(hook) <= maxDistance * maxDistance;
    }

    private void startBite() {
        if (targetFish == null) return;
        state(targetFish).caught = true;
    }

    private void endBite() {
        if (targetFish == null) return;
        releaseTarget(true);
    }

    private void resetToLure() {
        releaseTarget(false);
        timers.winterheart$setTimeUntilHooked(0);
        timers.winterheart$setTimeUntilLured(randomBetween(
                hook.level().getRandom(), Config.get().minRelureTicks, Config.get().maxRelureTicks
        ));
    }

    private void releaseTarget(boolean bored) {
        AbstractFish fish = targetFish;
        if (fish == null) return;
        targetFish = null;
        FishState extension = state(fish);
        if (extension.hook != hook) return;
        extension.caught = false;
        if (bored) {
            extension.hook = null;
            extension.boredUntil = hook.level().getGameTime() + Config.get().boredTicks;
            lured.remove(fish);
        }
    }

    private void track(AbstractFish fish) {
        if (!lured.contains(fish)) lured.add(fish);
    }

    private void cleanup(AbstractFish fish) {
        FishState extension = state(fish);
        if (extension.hook == hook) {
            extension.caught = false;
            extension.hook = null;
        }
    }

    private static int randomBetween(RandomSource random, int first, int second) {
        return Mth.nextInt(random, Math.min(first, second), Math.max(first, second));
    }

    private static FishState state(AbstractFish fish) {
        return FishAccess.state(fish);
    }
}
