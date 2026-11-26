package io.github.soldierdonkey2.packfinder.mixin;

import com.google.common.collect.ImmutableList;
import io.github.soldierdonkey2.packfinder.FolderPacks;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Vanilla keeps the saved selection order, which differs between players and worlds; this applies ours instead. */
@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow @Final private Set<RepositorySource> sources;

    @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
    private void packfinder$applyOrder(Collection<String> ids, CallbackInfoReturnable<List<Pack>> cir) {
        for (RepositorySource source : sources) {
            if (source instanceof FolderPacks folder) {
                cir.setReturnValue(ImmutableList.copyOf(folder.reorder(cir.getReturnValue())));
            }
        }
    }
}
