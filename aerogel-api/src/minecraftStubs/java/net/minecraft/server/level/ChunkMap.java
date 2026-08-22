package net.minecraft.server.level;

public class ChunkMap {
    private void setChunkUnsaved(net.minecraft.world.level.ChunkPos position) { }
    public DistanceManager getDistanceManager() { return null; }
    public void forEachBlockTickingChunk(
        java.util.function.Consumer<net.minecraft.world.level.chunk.LevelChunk> action) { }
    public java.util.List<ServerPlayer> getPlayers(net.minecraft.world.level.ChunkPos position,
                                                   boolean boundaryOnly) { return null; }
    public void move(ServerPlayer player) { }
}
