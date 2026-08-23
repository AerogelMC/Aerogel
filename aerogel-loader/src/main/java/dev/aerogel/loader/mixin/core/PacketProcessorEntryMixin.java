package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.QueuedPacketBridge;
import dev.aerogel.loader.network.PacketQueueMetrics;
import dev.aerogel.loader.internal.EntityOwnedPacketListener;
import dev.aerogel.loader.network.EntityTargetPackets;
import dev.aerogel.loader.runtime.AerogelRuntime;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.network.PacketProcessor$ListenerAndPacket")
abstract class PacketProcessorEntryMixin implements QueuedPacketBridge {
    @Shadow public abstract void handle();
    @Shadow @Final private PacketListener listener;
    @Shadow @Final private Packet<?> packet;
    @Unique private long aerogel$queuedAtNanos;
    @Unique private boolean aerogel$idlePump;
    @Unique private boolean aerogel$latencyRecorded;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$packetQueued(CallbackInfo callbackInfo) {
        aerogel$queuedAtNanos = PacketQueueMetrics.markQueued();
    }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void aerogel$routeAndRecordQueueLatency(CallbackInfo callbackInfo) {
        if (listener instanceof EntityOwnedPacketListener owned) {
            Entity entity = owned.aerogel$packetOwner();
            if (EntityTargetPackets.targeted(packet)
                && entity.level() instanceof ServerLevel level) {
                Entity target = level.getEntity(EntityTargetPackets.targetEntityId(packet));
                if (target != null && AerogelRuntime.routeInteractiveEntityTargetTask(
                    entity, target, this::handle)) {
                    callbackInfo.cancel();
                    return;
                }
            }
            if (packet instanceof ServerboundUseItemOnPacket useItemOn
                && entity.level() instanceof ServerLevel level
                && AerogelRuntime.routeInteractiveEntityBlockTask(
                    entity, level, useItemOn.getHitResult().getBlockPos().immutable(), this::handle)) {
                callbackInfo.cancel();
                return;
            }
            if (AerogelRuntime.routeInteractiveEntityTask(entity, this::handle)) {
                callbackInfo.cancel();
                return;
            }
        }
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
