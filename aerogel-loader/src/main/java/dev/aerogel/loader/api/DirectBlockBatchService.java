package dev.aerogel.loader.api;

import dev.aerogel.api.blockbatch.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DirectBlockBatchService implements BlockBatchService {
    DirectBlockBatchService(PluginApiScope scope) { }
    @Override public BlockBatch create(ServerLevel level) { return direct(level); }

    public static BlockBatch direct(ServerLevel level) {
        return new Batch(Objects.requireNonNull(level, "level"));
    }

    private static final class Batch implements BlockBatch {
        private final ServerLevel level;
        private final Map<BlockPos, BlockState> changes = new LinkedHashMap<>();
        private Batch(ServerLevel level) { this.level = level; }
        @Override public ServerLevel level() { return level; }
        @Override public BlockBatch set(BlockPos position, BlockState state) {
            changes.put(Objects.requireNonNull(position, "position"), Objects.requireNonNull(state, "state"));
            return this;
        }
        @Override public BlockBatch setAll(Map<BlockPos, BlockState> values) {
            Objects.requireNonNull(values, "changes").forEach(this::set); return this;
        }
        @Override public int size() { return changes.size(); }
        @Override public void clear() { changes.clear(); }

        @Override public BlockBatchResult commit(BlockBatchOptions options) {
            Objects.requireNonNull(options, "options");
            if (!level.getServer().isSameThread()) {
                throw new IllegalStateException("Block batches must commit on the Minecraft server thread");
            }
            if (changes.isEmpty()) return new BlockBatchResult(0, 0, 0);
            ServerChunkCache source = level.getChunkSource();
            Set<Long> chunks = new LinkedHashSet<>();
            for (BlockPos position : changes.keySet()) {
                int chunkX = position.getX() >> 4;
                int chunkZ = position.getZ() >> 4;
                if (options.requireLoadedChunks() && !source.hasChunk(chunkX, chunkZ)) {
                    throw new IllegalStateException("Block batch targets an unloaded chunk: " + chunkX + ", " + chunkZ);
                }
                chunks.add(pack(chunkX, chunkZ));
            }

            Map<BlockPos, BlockState> previous = options.rollbackOnFailure()
                ? new LinkedHashMap<>() : Map.of();
            int changed = 0;
            int flags = options.updateFlags() & ~Block.UPDATE_CLIENTS;
            try {
                for (Map.Entry<BlockPos, BlockState> entry : changes.entrySet()) {
                    if (options.rollbackOnFailure()) previous.put(entry.getKey(), level.getBlockState(entry.getKey()));
                    if (level.setBlock(entry.getKey(), entry.getValue(), flags)) changed++;
                }
            } catch (RuntimeException | Error failure) {
                if (options.rollbackOnFailure()) {
                    previous.forEach((position, state) -> level.setBlock(position, state, flags));
                    synchronize(source, chunks);
                }
                throw failure;
            }
            int synchronizedChunks = synchronize(source, chunks);
            int requested = changes.size();
            changes.clear();
            return new BlockBatchResult(requested, changed, synchronizedChunks);
        }

        private int synchronize(ServerChunkCache source, Set<Long> chunks) {
            int sent = 0;
            for (long packed : chunks) {
                int chunkX = (int) (packed >> 32);
                int chunkZ = (int) packed;
                LevelChunk chunk = source.getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                    chunk, source.getLightEngine(), null, null);
                for (ServerPlayer player : source.chunkMap.getPlayers(new ChunkPos(chunkX, chunkZ), false)) {
                    player.sendPacket(packet);
                }
                sent++;
            }
            return sent;
        }
        private static long pack(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }
    }
}
