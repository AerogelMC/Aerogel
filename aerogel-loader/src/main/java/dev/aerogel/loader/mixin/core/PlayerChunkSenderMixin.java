package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.context.NativeTickCoordinator;
import dev.aerogel.loader.network.OutboundPacketPriority;
import dev.aerogel.loader.runtime.AerogelRuntime;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mixin(targets = "net.minecraft.server.network.PlayerChunkSender")
abstract class PlayerChunkSenderMixin {
    @Shadow @Final private LongSet pendingChunks;
    @Shadow private float desiredChunksPerTick;
    @Shadow private float batchQuota;
    @Shadow private int unacknowledgedBatches;

    @Unique private static final ThreadLocal<ClientboundLevelChunkWithLightPacket>
        AEROGEL_PREBUILT_PACKET = new ThreadLocal<>();
    @Unique private static final ConcurrentHashMap<LevelChunk, ChunkPacketWave>
        AEROGEL_PACKET_WAVES = new ConcurrentHashMap<>();

    @Unique private ChunkBatchFrame aerogel$batchFrame;
    @Unique private CompletableFuture<Void> aerogel$batchTail =
        CompletableFuture.completedFuture(null);

    @Invoker("sendChunk")
    private static void aerogel$sendPreparedChunk(
        ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk
    ) {
        throw new AssertionError();
    }

    /**
     * Vanilla's adaptive quota is useful when chunk packet construction and
     * compression share the connection event loop. Aerogel moves both costs to
     * owned Context/compression lanes, so every chunk ready at the start of this
     * call is admitted and the socket becomes the only remaining backpressure.
     */
    @Inject(
        method = "sendNextChunks(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At("HEAD")
    )
    private void aerogel$admitEveryReadyChunk(
        ServerPlayer player, CallbackInfo callbackInfo
    ) {
        float readyChunks = pendingChunks.size();
        desiredChunksPerTick = readyChunks;
        batchQuota = readyChunks;
        unacknowledgedBatches = 0;
    }

    @Redirect(
        method = "sendNextChunks(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/"
            + "ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
            ordinal = 0)
    )
    private void aerogel$prepareOwnedBatch(
        ServerGamePacketListenerImpl connection,
        Packet<? super ClientGamePacketListener> ignored
    ) {
        if (aerogel$batchFrame != null) {
            throw new IllegalStateException("Overlapping player chunk batch");
        }
        aerogel$batchFrame = new ChunkBatchFrame(
            (PlayerChunkSender) (Object) this, connection, aerogel$batchTail);
        aerogel$batchTail = aerogel$batchFrame.finished();
    }

    @Redirect(
        method = "sendNextChunks(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/PlayerChunkSender;"
            + "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/LevelChunk;)V")
    )
    private void aerogel$serializeOwned(
        ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk
    ) {
        ChunkBatchFrame frame = aerogel$batchFrame;
        if (frame == null) throw new IllegalStateException("Missing player chunk batch");
        ChunkBatchSlot slot = frame.reserve();
        aerogel$prepareOwnedPacket(frame, slot, level, chunk);
    }

    /**
     * Coalesces only requests that overlap before their owner task starts. The
     * Context removes the wave before reading the chunk, so a request arriving
     * after that linearization point gets a later snapshot and cannot observe a
     * stale block or light update. No rate, time window, or global lock is used.
     */
    @Unique
    private static void aerogel$prepareOwnedPacket(
        ChunkBatchFrame frame, ChunkBatchSlot slot,
        ServerLevel level, LevelChunk chunk
    ) {
        ChunkPacketWaiter waiter = new ChunkPacketWaiter(frame, slot);
        while (true) {
            ChunkPacketWave observed = AEROGEL_PACKET_WAVES.get(chunk);
            if (observed != null) {
                if (observed.add(waiter)) return;
                AEROGEL_PACKET_WAVES.remove(chunk, observed);
                continue;
            }

            ChunkPacketWave created = new ChunkPacketWave();
            ChunkPacketWave raced = AEROGEL_PACKET_WAVES.putIfAbsent(chunk, created);
            if (raced != null) continue;
            if (!created.add(waiter)) {
                throw new IllegalStateException("New chunk packet wave was already closed");
            }

            boolean accepted = AerogelRuntime.routeChunkTask(level, chunk, () -> {
                AEROGEL_PACKET_WAVES.remove(chunk, created);
                ChunkPacketWaiter requests = created.close();
                try {
                    ClientboundLevelChunkWithLightPacket packet =
                        new ClientboundLevelChunkWithLightPacket(
                            chunk, level.getLightEngine(), null, null);
                    while (requests != null) {
                        requests.frame.packetReady(
                            requests.slot, level, chunk, packet);
                        requests = requests.next;
                    }
                } catch (Throwable error) {
                    while (requests != null) {
                        requests.frame.packetFailed(requests.slot, chunk);
                        requests = requests.next;
                    }
                    throw error;
                }
            });
            if (!accepted) {
                AEROGEL_PACKET_WAVES.remove(chunk, created);
                ChunkPacketWaiter requests = created.close();
                while (requests != null) {
                    requests.frame.routeRejected(requests.slot, chunk);
                    requests = requests.next;
                }
            }
            return;
        }
    }

    @Redirect(
        method = "sendNextChunks(Lnet/minecraft/server/level/ServerPlayer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/"
            + "ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V",
            ordinal = 1)
    )
    private void aerogel$finishOwnedBatch(
        ServerGamePacketListenerImpl connection,
        Packet<? super ClientGamePacketListener> ignored
    ) {
        ChunkBatchFrame frame = aerogel$batchFrame;
        aerogel$batchFrame = null;
        if (frame == null) {
            connection.send(ignored);
            return;
        }
        frame.close();
    }

    @Redirect(
        method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
            + "Lnet/minecraft/server/level/ServerLevel;"
            + "Lnet/minecraft/world/level/chunk/LevelChunk;)V",
        at = @At(value = "NEW", target =
            "net/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket")
    )
    private static ClientboundLevelChunkWithLightPacket aerogel$usePreparedPacket(
        LevelChunk chunk, LevelLightEngine light, BitSet sky, BitSet block
    ) {
        ClientboundLevelChunkWithLightPacket prepared = AEROGEL_PREBUILT_PACKET.get();
        return prepared != null
            ? prepared
            : new ClientboundLevelChunkWithLightPacket(chunk, light, sky, block);
    }

    @Unique
    private static void aerogel$sendPrepared(
        ServerGamePacketListenerImpl connection, ServerLevel level,
        LevelChunk chunk, ClientboundLevelChunkWithLightPacket packet
    ) {
        AEROGEL_PREBUILT_PACKET.set(packet);
        try {
            aerogel$sendPreparedChunk(connection, level, chunk);
        } finally {
            AEROGEL_PREBUILT_PACKET.remove();
        }
    }

    @Unique
    private static final class ChunkBatchFrame {
        private final PlayerChunkSender sender;
        private final ServerGamePacketListenerImpl connection;
        private final AtomicInteger sentChunks = new AtomicInteger();
        private final CompletableFuture<Void> started = new CompletableFuture<>();
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private CompletableFuture<Void> tail = started;

        private ChunkBatchFrame(
            PlayerChunkSender sender,
            ServerGamePacketListenerImpl connection,
            CompletableFuture<Void> previousBatch
        ) {
            this.sender = sender;
            this.connection = connection;
            previousBatch.whenComplete((ignored, error) -> {
                try {
                    connection.send(ClientboundChunkBatchStartPacket.INSTANCE);
                } finally {
                    started.complete(null);
                }
            });
        }

        private CompletableFuture<Void> finished() {
            return finished;
        }

        private ChunkBatchSlot reserve() {
            ChunkBatchSlot slot = new ChunkBatchSlot(tail);
            tail = slot.completion();
            return slot;
        }

        private void packetReady(
            ChunkBatchSlot slot, ServerLevel level, LevelChunk chunk,
            ClientboundLevelChunkWithLightPacket packet
        ) {
            slot.predecessor().whenComplete((ignored, error) ->
                dispatchOwned(slot, level, chunk, packet));
        }

        private void packetFailed(ChunkBatchSlot slot, LevelChunk chunk) {
            NativeTickCoordinator.submitGlobalCommit(() -> sender.markChunkPendingToSend(chunk));
            skip(slot);
        }

        private void routeRejected(ChunkBatchSlot slot, LevelChunk chunk) {
            sender.markChunkPendingToSend(chunk);
            skip(slot);
        }

        private void close() {
            CompletableFuture<Void> completedTail = tail;
            completedTail.whenComplete((ignored, error) -> {
                try {
                    connection.send(new ClientboundChunkBatchFinishedPacket(
                        sentChunks.get()));
                } finally {
                    finished.complete(null);
                }
            });
        }

        private void dispatchOwned(
            ChunkBatchSlot slot, ServerLevel level, LevelChunk chunk,
            ClientboundLevelChunkWithLightPacket packet
        ) {
            Runnable send = () -> {
                try {
                    OutboundPacketPriority.runBulk(() -> {
                        aerogel$sendPrepared(connection, level, chunk, packet);
                        AerogelRuntime.playerChunkSent(
                            level, chunk, connection.player);
                    });
                    sentChunks.incrementAndGet();
                } catch (Throwable error) {
                    NativeTickCoordinator.submitGlobalCommit(
                        () -> sender.markChunkPendingToSend(chunk));
                    throw error;
                } finally {
                    slot.completion().complete(null);
                }
            };
            if (AerogelRuntime.isChunkOwnerContext(level, chunk)) {
                send.run();
            } else if (!AerogelRuntime.routeChunkTask(level, chunk, send)) {
                NativeTickCoordinator.submitGlobalCommit(
                    () -> sender.markChunkPendingToSend(chunk));
                slot.completion().complete(null);
            }
        }

        private static void skip(ChunkBatchSlot slot) {
            slot.predecessor().whenComplete((ignored, error) ->
                slot.completion().complete(null));
        }
    }

    @Unique
    private record ChunkBatchSlot(
        CompletableFuture<Void> predecessor,
        CompletableFuture<Void> completion
    ) {
        private ChunkBatchSlot(CompletableFuture<Void> predecessor) {
            this(predecessor, new CompletableFuture<>());
        }
    }

    @Unique
    private static final class ChunkPacketWave {
        private static final ChunkPacketWaiter CLOSED =
            new ChunkPacketWaiter(null, null);
        private final AtomicReference<ChunkPacketWaiter> waiters =
            new AtomicReference<>();

        private boolean add(ChunkPacketWaiter waiter) {
            ChunkPacketWaiter observed = waiters.get();
            while (observed != CLOSED) {
                waiter.next = observed;
                if (waiters.compareAndSet(observed, waiter)) return true;
                observed = waiters.get();
            }
            return false;
        }

        private ChunkPacketWaiter close() {
            ChunkPacketWaiter observed = waiters.getAndSet(CLOSED);
            return observed == CLOSED ? null : observed;
        }
    }

    @Unique
    private static final class ChunkPacketWaiter {
        private final ChunkBatchFrame frame;
        private final ChunkBatchSlot slot;
        private ChunkPacketWaiter next;

        private ChunkPacketWaiter(ChunkBatchFrame frame, ChunkBatchSlot slot) {
            this.frame = frame;
            this.slot = slot;
        }
    }
}
