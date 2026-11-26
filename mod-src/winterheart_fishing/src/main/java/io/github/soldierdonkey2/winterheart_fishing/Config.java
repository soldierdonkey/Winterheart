package io.github.soldierdonkey2.winterheart_fishing;

import com.google.gson.Gson;
import java.io.Reader;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Tuning from data/winterheart/fishing/config.json, re-read on /reload. The pack is single-player only, so the client reads
 * the same values as the integrated server.
 */
public final class Config {
    public static final ResourceLocation FILE = new ResourceLocation("winterheart", "fishing/config.json");

    public static final class Values {
        public double catchRadius = 2;
        public float intrigueChance = 0.33F;
        public double intrigueRadius = 3;
        public double intrigueDepth = 9;
        public float lureBonusMultiplier = 1.67F;
        public int intrigueRerollInterval = 40;
        public float intrigueRerollChanceMultiplier = 0.25F;
        public int minInterestTicks = 400;
        public int maxInterestTicks = 800;
        public int boredTicks = 600;
        public float intriguedFishWeight = 3;
        public int maxApproachTicks = 200;
        public double arrivalDistance = 1.5;
        public int minRelureTicks = 20;
        public int maxRelureTicks = 60;
        public boolean castCharging = true;
        public double minCastDistanceMultiplier = 0.25;
        public double maxCastDistanceMultiplier = 1.75;
        public boolean fishDropNoLoot = true;
        public boolean fishDropNoCarcass = true;
        /** Entity id to the item reeling that fish in gives. Fish not listed give their loot table. */
        public Map<String, String> catchItems = Map.of();
        public boolean allowFishingWithoutFish = false;
        public boolean protectReleasedFish = true;
        public boolean castBar = true;
        public boolean firstPersonAnimation = true;
        public boolean thirdPersonAnimation = true;
    }

    private static volatile Values values = new Values();

    private Config() {}

    public static Values get() {
        return values;
    }

    public static final class Loader extends SimplePreparableReloadListener<Values> {
        @Override
        protected Values prepare(ResourceManager manager, ProfilerFiller profiler) {
            try (Reader reader = manager.getResourceOrThrow(FILE).openAsReader()) {
                return new Gson().fromJson(reader, Values.class);
            } catch (Exception e) {
                WinterheartFishing.LOGGER.error("[Fishing] could not read {}: {} (keeping the current values)", FILE, e.toString());
                return null;
            }
        }

        @Override
        protected void apply(Values loaded, ResourceManager manager, ProfilerFiller profiler) {
            if (loaded == null) return;
            values = loaded;
            WinterheartFishing.LOGGER.info("[Fishing] config loaded");
        }
    }
}
