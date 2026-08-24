package net.minecraft.server.level;

public class ChunkMap {
    private void setChunkUnsaved(net.minecraft.world.level.ChunkPos position) { }
    public DistanceManager getDistanceManager() { return null; }
    public ChunkHolder getUpdatingChunkIfPresent(long chunkKey) { return null; }
    public void forEachBlockTickingChunk(
        java.util.function.Consumer<net.minecraft.world.level.chunk.LevelChunk> action) { }
    public java.util.List<ServerPlayer> getPlayers(net.minecraft.world.level.ChunkPos position,
                                                   boolean boundaryOnly) { return null; }
    public void move(ServerPlayer player) { }
    public java.util.concurrent.CompletableFuture<?> getChunkRangeFuture(
        ChunkHolder holder, int radius,
        java.util.function.IntFunction<net.minecraft.world.level.chunk.status.ChunkStatus> status) {
        return null;
    }
}
