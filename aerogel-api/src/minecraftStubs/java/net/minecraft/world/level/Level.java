package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.TickRateManager;
import net.minecraft.util.RandomSource;

public abstract class Level implements LevelAccessor {
    public boolean setBlockAndUpdate(BlockPos position, BlockState state) { return false; }
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
    public boolean isInValidBounds(BlockPos position) { return false; }
    public LevelChunk getChunkAt(BlockPos position) { return null; }
    public BlockEntity getBlockEntity(BlockPos position) { return null; }
    public TickRateManager tickRateManager() { return null; }
    public boolean shouldTickBlocksAt(BlockPos position) { return true; }
    public boolean hasChunkAt(BlockPos position) { return false; }
    public void neighborChanged(
        BlockState state, BlockPos position, Block sourceBlock,
        Orientation orientation, boolean moved
    ) { }
    public void addBlockEntityTicker(TickingBlockEntity ticker) { }
    public void tickBlockEntities() { }
    public RandomSource getRandom() { return null; }
    public void destroyBlockProgress(int entityId, BlockPos position, int progress) { }
}
