package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.context.CommitScope;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps secondary global indexes asynchronous while the chunk-owned entity is published. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
abstract class ServerLevelEntityCallbacksMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<Boolean> AEROGEL$REPLAYING_TICKING_CALLBACK =
        ThreadLocal.withInitial(() -> false);

    @Shadow public abstract void onCreated(Entity entity);
    @Shadow public abstract void onDestroyed(Entity entity);
    @Shadow public abstract void onTickingStart(Entity entity);
    @Shadow public abstract void onTickingEnd(Entity entity);
    @Shadow public abstract void onTrackingStart(Entity entity);
    @Shadow public abstract void onTrackingEnd(Entity entity);
    @Shadow public abstract void onSectionChange(Entity entity);

    @Inject(method = "onCreated", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferCreated(Entity entity, CallbackInfo callback) {
        defer("onCreated", entity, callback);
    }

    @Inject(method = "onDestroyed", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferDestroyed(Entity entity, CallbackInfo callback) {
        defer("onDestroyed", entity, callback);
    }

    @Inject(method = "onTickingStart", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferTickingStart(Entity entity, CallbackInfo callback) {
        if (AEROGEL$REPLAYING_TICKING_CALLBACK.get()) return;
        if (NativeTickCoordinator.isNativeWorker()
            && entity.level() instanceof ServerLevel level) {
            // Publish both Context scheduling eligibility and vanilla ticking
            // membership in the same owner transaction.
            AerogelRuntime.registerTickingEntity(level, entity);
        }
        defer("onTickingStart", entity, callback);
    }

    @Inject(method = "onTickingEnd", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferTickingEnd(Entity entity, CallbackInfo callback) {
        if (AEROGEL$REPLAYING_TICKING_CALLBACK.get()) return;
        if (NativeTickCoordinator.isNativeWorker()) {
            // Prevent already queued later logical ticks from running after this
            // exact vanilla ticking-membership generation has ended.
            AerogelRuntime.unregisterTickingEntity(entity);
        }
        defer("onTickingEnd", entity, callback);
    }

    @Inject(method = "onTickingStart", at = @At("RETURN"))
    private void aerogel$registerTickingEntity(Entity entity, CallbackInfo callback) {
        if (NativeTickCoordinator.isNativeWorker()
            || AEROGEL$REPLAYING_TICKING_CALLBACK.get()) return;
        if (entity.level() instanceof ServerLevel level) {
            AerogelRuntime.registerTickingEntity(level, entity);
        }
    }

    @Inject(method = "onTickingEnd", at = @At("RETURN"))
    private void aerogel$unregisterTickingEntity(Entity entity, CallbackInfo callback) {
        if (NativeTickCoordinator.isNativeWorker()
            || AEROGEL$REPLAYING_TICKING_CALLBACK.get()) return;
        AerogelRuntime.unregisterTickingEntity(entity);
    }

    @Inject(method = "onTrackingStart", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferTrackingStart(Entity entity, CallbackInfo callback) {
        defer("onTrackingStart", entity, callback);
    }

    @Inject(method = "onTrackingEnd", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferTrackingEnd(Entity entity, CallbackInfo callback) {
        defer("onTrackingEnd", entity, callback);
    }

    @Inject(method = "onSectionChange", at = @At("HEAD"), cancellable = true)
    private void aerogel$deferSectionChange(Entity entity, CallbackInfo callback) {
        defer("onSectionChange", entity, callback);
    }

    private void defer(String method, Entity entity, CallbackInfo callback) {
        if (!NativeTickCoordinator.isNativeWorker()) return;
        CommitScope scope = method.equals("onTickingStart")
            || method.equals("onTickingEnd")
            ? CommitScope.CONTEXT : CommitScope.SERVER;
        if (NativeTickCoordinator.deferCommit(scope, () -> invoke(method, entity))) {
            callback.cancel();
        }
    }

    private void invoke(String method, Entity entity) {
        boolean tickingCallback = method.equals("onTickingStart")
            || method.equals("onTickingEnd");
        if (tickingCallback) AEROGEL$REPLAYING_TICKING_CALLBACK.set(true);
        try {
            switch (method) {
                // A native owner may complete an entity's whole lifetime before
                // its server-owned publications are drained. Removal is terminal
                // for an Entity instance, so publishing an earlier start after
                // that point would resurrect it in global indexes and make
                // ChunkMap build a spawn packet for an already removed entity.
                case "onCreated" -> {
                    if (!entity.isRemoved()) onCreated(entity);
                }
                case "onDestroyed" -> onDestroyed(entity);
                case "onTickingStart" -> {
                    if (!entity.isRemoved()) onTickingStart(entity);
                }
                case "onTickingEnd" -> onTickingEnd(entity);
                case "onTrackingStart" -> {
                    if (!entity.isRemoved()) onTrackingStart(entity);
                }
                case "onTrackingEnd" -> onTrackingEnd(entity);
                case "onSectionChange" -> {
                    if (!entity.isRemoved()) onSectionChange(entity);
                }
                default -> throw new IllegalArgumentException(
                    "Unknown entity callback " + method);
            }
        } finally {
            if (tickingCallback) AEROGEL$REPLAYING_TICKING_CALLBACK.remove();
        }
    }
}
