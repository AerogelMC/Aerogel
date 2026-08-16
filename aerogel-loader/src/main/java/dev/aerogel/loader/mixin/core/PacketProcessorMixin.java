package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.IdlePacketPump;
import dev.aerogel.loader.network.PacketProcessorBridge;
import dev.aerogel.loader.network.QueuedPacketBridge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Queue;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.network.PacketProcessor")
abstract class PacketProcessorMixin implements PacketProcessorBridge {
    @Shadow @Final private Queue<Object> packetsToBeHandled;
    @Unique private IdlePacketPump aerogel$idlePacketPump;

    @Override
    public void aerogel$configureIdlePump(BooleanSupplier idle, Consumer<Runnable> executor) {
        aerogel$idlePacketPump().configure(idle, executor);
    }

    @Override
    public void aerogel$requestIdlePump() {
        aerogel$idlePacketPump().request();
    }

    @Inject(method = "scheduleIfPossible", at = @At("RETURN"))
    private void aerogel$packetQueued(CallbackInfo callbackInfo) {
        if (aerogel$idlePacketPump != null) aerogel$idlePacketPump.request();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void aerogel$closeIdlePacketPump(CallbackInfo callbackInfo) {
        if (aerogel$idlePacketPump != null) aerogel$idlePacketPump.close();
    }

    @Unique
    private IdlePacketPump aerogel$idlePacketPump() {
        if (aerogel$idlePacketPump == null) {
            aerogel$idlePacketPump = new IdlePacketPump(
                packetsToBeHandled,
                entry -> ((QueuedPacketBridge) entry).aerogel$handleQueuedPacket(true));
        }
        return aerogel$idlePacketPump;
    }
}
