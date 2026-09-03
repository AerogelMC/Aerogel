package net.minecraft.world.level.chunk.status;

public final class ChunkStatus {
    public static final ChunkStatus EMPTY = new ChunkStatus();
    public static final ChunkStatus STRUCTURE_STARTS = new ChunkStatus();
    public static final ChunkStatus STRUCTURE_REFERENCES = new ChunkStatus();
    public static final ChunkStatus BIOMES = new ChunkStatus();
    public static final ChunkStatus NOISE = new ChunkStatus();
    public static final ChunkStatus SURFACE = new ChunkStatus();
    public static final ChunkStatus CARVERS = new ChunkStatus();
    public static final ChunkStatus FEATURES = new ChunkStatus();
    public static final ChunkStatus INITIALIZE_LIGHT = new ChunkStatus();
    public static final ChunkStatus LIGHT = new ChunkStatus();
    public static final ChunkStatus SPAWN = new ChunkStatus();
    public static final ChunkStatus FULL = new ChunkStatus();

    public int getIndex() { return 0; }
    public boolean isAfter(ChunkStatus other) { return false; }
    public boolean isOrAfter(ChunkStatus other) { return false; }
    public boolean isBefore(ChunkStatus other) { return false; }
    public ChunkType getChunkType() { return null; }
}
