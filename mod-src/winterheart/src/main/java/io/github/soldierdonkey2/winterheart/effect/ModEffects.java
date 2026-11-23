package io.github.soldierdonkey2.winterheart.effect;

import io.github.soldierdonkey2.winterheart.Winterheart;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Winterheart.MOD_ID);

    /** Shown while one or more limbs are decaying or healing. The behaviour lives in the limb-poisoning script. */
    public static final RegistryObject<MobEffect> LIMB_POISING = EFFECTS.register("limb_poising", LimbPoisingEffect::new);

    private ModEffects() {}

    private static final class LimbPoisingEffect extends MobEffect {
        LimbPoisingEffect() {
            super(MobEffectCategory.HARMFUL, 0x4caf50);
        }
    }
}
