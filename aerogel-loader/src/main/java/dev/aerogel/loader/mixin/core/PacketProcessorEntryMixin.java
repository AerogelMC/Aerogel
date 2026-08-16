package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.QueuedPacketBridge;
import dev.aerogel.loader.network.PacketQueueMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
abstract class PacketProcessorEntryMixin implements QueuedPacketBridge {
    @Shadow public abstract void handle();
    @Unique private long aerogel$queuedAtNanos;
    @Unique private boolean aerogel$idlePump;
    @Unique private boolean aerogel$latencyRecorded;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$packetQueued(CallbackInfo callbackInfo) {
        aerogel$queuedAtNanos = PacketQueueMetrics.markQueued();
    }

    @Inject(method = "handle", at = @At("HEAD"))
    private void aerogel$recordQueueLatency(CallbackInfo callbackInfo) {
        if (aerogel$latencyRecorded) return;
        aerogel$latencyRecorded = true;
        PacketQueueMetrics.record(aerogel$queuedAtNanos, aerogel$idlePump);
    }

    @Override
    public void aerogel$handleQueuedPacket(boolean idlePump) {
        aerogel$idlePump = idlePump;
        try {
            handle();
        } finally {
            aerogel$idlePump = false;
        }
    }
}
