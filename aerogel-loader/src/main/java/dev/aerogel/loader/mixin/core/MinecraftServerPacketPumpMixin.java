package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.PacketProcessorBridge;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.server.MinecraftServer")
abstract class MinecraftServerPacketPumpMixin {
    @Shadow private boolean waitingForNextTick;
    @Shadow public abstract PacketProcessor packetProcessor();
    @Shadow public abstract void executeIfPossible(Runnable task);
    @Unique private PacketProcessorBridge aerogel$packetProcessor;

    @Inject(
        method = "waitUntilNextTick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;managedBlock("
                + "Ljava/util/function/BooleanSupplier;)V",
            shift = At.Shift.BEFORE
        )
    )
    private void aerogel$beginIdlePacketPump(CallbackInfo callbackInfo) {
        if (aerogel$packetProcessor == null) {
            aerogel$packetProcessor = (PacketProcessorBridge) packetProcessor();
            aerogel$packetProcessor.aerogel$configureIdlePump(
                () -> waitingForNextTick,
                this::executeIfPossible);
        }
        aerogel$packetProcessor.aerogel$requestIdlePump();
    }
}
