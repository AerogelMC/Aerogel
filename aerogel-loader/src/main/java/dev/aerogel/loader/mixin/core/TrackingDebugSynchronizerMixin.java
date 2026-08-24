package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.ServerPlayerDebugBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.IdentityHashMap;
import java.util.Set;

/** Reuses one permission/subscription result per player and server tick. */
@Mixin(targets = "net.minecraft.util.debug.TrackingDebugSynchronizer")
abstract class TrackingDebugSynchronizerMixin {
    @Unique private static final ThreadLocal<DebugSnapshot> aerogel$debugSnapshot =
        ThreadLocal.withInitial(DebugSnapshot::new);

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;debugSubscriptions()Ljava/util/Set;"
        )
    )
    private Set<?> aerogel$subscriptionsFromTickSnapshot(ServerPlayer player) {
        long tick = player.level().getServer().getTickCount();
        DebugSnapshot snapshot = aerogel$debugSnapshot.get();
        if (snapshot.tick != tick) {
            snapshot.tick = tick;
            snapshot.values.clear();
        }

        Set<?> subscriptions = snapshot.values.get(player);
        if (subscriptions != null) return subscriptions;
        subscriptions = ((ServerPlayerDebugBridge) player)
            .aerogel$debugSubscriptions();
        snapshot.values.put(player, subscriptions);
        return subscriptions;
    }

    @Unique
    private static final class DebugSnapshot {
        private long tick = Long.MIN_VALUE;
        private final IdentityHashMap<ServerPlayer, Set<?>> values =
            new IdentityHashMap<>();
    }
}
