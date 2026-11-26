package io.github.soldierdonkey2.winterheart_fishing.mixin;

import io.github.soldierdonkey2.winterheart_fishing.Config;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops Butchery dropping a carcass when a fish dies. Its handlers do nothing else for a fish apart from removing the
 * vanilla drops. Butchery's own config switches also turn off placing and cutting up carcasses, so they stay on.
 */
@Pseudo
@Mixin(targets = {
        "net.mcreator.butchery.procedures.CodcarcassdropProcedure",
        "net.mcreator.butchery.procedures.SalmoncarcassdropProcedure",
        "net.mcreator.butchery.procedures.PufferfishcarcassdropProcedure"
}, remap = false)
public abstract class ButcheryCarcassMixin {
    @Inject(method = "onEntityDeath", at = @At("HEAD"), cancellable = true, remap = false)
    private static void winterheart$noFishCarcass(LivingDeathEvent event, CallbackInfo ci) {
        if (Config.get().fishDropNoCarcass && event.getEntity() instanceof AbstractFish) ci.cancel();
    }
}
