package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.block.entity.BlockEntity;

public abstract class Level {
    public static final ResourceKey<Level> OVERWORLD = null;
    public static final ResourceKey<Level> NETHER = null;
    public static final ResourceKey<Level> END = null;

    public enum ExplosionInteraction { }

    public BlockState getBlockState(BlockPos position) { return null; }
    public boolean setBlock(BlockPos position, BlockState state, int flags, int recursionLeft) {
        return false;
    }
    public boolean setBlock(BlockPos position, BlockState state, int flags) { return false; }
    public boolean removeBlock(BlockPos position, boolean moving) { return false; }
    public RegistryAccess registryAccess() { return null; }
    public LevelData getLevelData() { return null; }
    public ResourceKey<Level> dimension() { return null; }
    public BlockEntity getBlockEntity(BlockPos position) { return null; }
    public void destroyBlockProgress(int entityId, BlockPos position, int progress) { }
}
