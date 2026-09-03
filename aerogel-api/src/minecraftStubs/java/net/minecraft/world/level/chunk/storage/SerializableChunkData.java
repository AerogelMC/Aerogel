package net.minecraft.world.level.chunk.storage;

public class SerializableChunkData {
    public static SerializableChunkData parse(
        net.minecraft.world.level.LevelHeightAccessor level,
        net.minecraft.world.level.chunk.PalettedContainerFactory factory,
        net.minecraft.nbt.CompoundTag tag
    ) { return null; }

    public net.minecraft.world.level.chunk.ProtoChunk read(
        net.minecraft.server.level.ServerLevel level,
        net.minecraft.world.entity.ai.village.poi.PoiManager poiManager,
        RegionStorageInfo storage,
        net.minecraft.world.level.ChunkPos position
    ) { return null; }

    public net.minecraft.world.level.chunk.status.ChunkStatus chunkStatus() { return null; }
}
