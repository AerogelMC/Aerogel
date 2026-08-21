package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.internal.EntityOwnedPacketListener;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketProcessor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerLevel;
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
        aerogel$acceptEntityOwner(listener, callback);
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
        aerogel$acceptEntityOwner(listener, callback);
    }

    private static void aerogel$acceptEntityOwner(
        PacketListener listener, CallbackInfo callback
    ) {
        if (listener instanceof EntityOwnedPacketListener owned
            && AerogelRuntime.isEntityMutationThread(owned.aerogel$packetOwner())) {
            callback.cancel();
        }
    }
}
