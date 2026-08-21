package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.ContextualEntityCallback;
import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.PaddedAtomicReference;
import dev.aerogel.loader.internal.EntityContextOwnerBridge;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.entity.Entity")
abstract class EntityContextCallbackMixin implements EntityContextOwnerBridge {
    @Unique private final PaddedAtomicReference<Object> aerogel$contextOwner =
        new PaddedAtomicReference<>();

    @Override
    public Object aerogel$contextOwner() {
        return aerogel$contextOwner.get();
    }

    @Override
    public void aerogel$contextOwner(Object owner) {
        aerogel$contextOwner.set(owner);
    }

    @Override
    public boolean aerogel$compareAndSetContextOwner(
        Object expected, Object updated
    ) {
        return aerogel$contextOwner.compareAndSet(expected, updated);
    }

    @Inject(
        method = "remove(Lnet/minecraft/world/entity/Entity$RemovalReason;)V",
        at = @At("HEAD"), cancellable = true
    )
    private void aerogel$routeRemoval(
        Entity.RemovalReason reason, CallbackInfo callback
    ) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        if (NativeTickCoordinator.deferGlobalCommit(
            () -> ((Entity) (Object) this).remove(reason))) callback.cancel();
    }

    @ModifyVariable(
        method = "setLevelCallback(Lnet/minecraft/world/level/entity/EntityInLevelCallback;)V",
        at = @At("HEAD"), argsOnly = true
    )
    private EntityInLevelCallback aerogel$wrapLevelCallback(EntityInLevelCallback callback) {
        return callback instanceof ContextualEntityCallback
            ? callback
            : new ContextualEntityCallback((Entity) (Object) this, callback);
    }
}
