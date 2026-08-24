package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.EntityOwnedPacketListener;
import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.protocol.PacketUtils")
abstract class PacketUtilsMixin {
    @Inject(
        method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void aerogel$acceptEntityOwnerForLevel(
        Packet<?> packet, PacketListener listener, ServerLevel level, CallbackInfo callback
    ) {
        aerogel$acceptEntityOwner(packet, listener, callback);
    }

    @Inject(
        method = "ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;"
            + "Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
        at = @At("HEAD"), cancellable = true
    )
    private static void aerogel$acceptEntityOwnerForProcessor(
        Packet<?> packet, PacketListener listener, PacketProcessor processor,
        CallbackInfo callback
    ) {
        aerogel$acceptEntityOwner(packet, listener, callback);
    }

    private static void aerogel$acceptEntityOwner(
        Packet<?> packet, PacketListener listener, CallbackInfo callback
    ) {
        if (!(listener instanceof EntityOwnedPacketListener owned)) return;
        Entity entity = owned.aerogel$packetOwner();
        if (AerogelRuntime.isEntityMutationThread(entity)) {
            callback.cancel();
            return;
        }

        // A player can publish a new chunk owner between queue admission and
        // handler entry. Falling through here would enqueue the packet on the
        // global PacketProcessor and turn an ordinary boundary crossing into a
        // server-thread detour. Follow the entity's current owner instead. The
        // exception is the same control-flow signal vanilla uses to abort the
        // stale invocation; ServerCommonPacketListenerMixin keeps it out of the
        // error path when ListenerAndPacket is the caller.
        if (NativeTickCoordinator.isNativeWorker()
            && AerogelRuntime.routeInteractiveEntityTask(entity,
                () -> aerogel$handle(packet, listener))) {
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void aerogel$handle(Packet packet, PacketListener listener) {
        packet.handle(listener);
    }
}
