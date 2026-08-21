package net.minecraft.world.level.chunk;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

public class LevelChunk extends ChunkAccess {
    public enum EntityCreationType { IMMEDIATE }

    public LevelChunk() { }
    public LevelChunk(Level level, ChunkPos pos) { }
    public ChunkPos getPos() { return null; }
    public Level getLevel() { return null; }
    public BlockEntity getBlockEntity(BlockPos position, EntityCreationType creationType) {
        return null;
    }
}
