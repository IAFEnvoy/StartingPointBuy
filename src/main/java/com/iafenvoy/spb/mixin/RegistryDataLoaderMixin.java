package com.iafenvoy.spb.mixin;

import com.iafenvoy.spb.data.validation.DatapackValidator;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * Runs before registry loading completes, so failure follows Minecraft's failed-datapack path.
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {
    @Inject(method = "load(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/List;)Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
    private static void validateDefinitions(CallbackInfoReturnable<CompletableFuture<RegistryAccess.Frozen>> cir) {
        cir.setReturnValue(cir.getReturnValue().thenApply(DatapackValidator::validate));
    }
}
