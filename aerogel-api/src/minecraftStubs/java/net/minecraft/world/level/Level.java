package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelData;

public abstract class Level {
    public static final ResourceKey<Level> OVERWORLD = null;
    public static final ResourceKey<Level> NETHER = null;
    public static final ResourceKey<Level> END = null;

    public BlockState getBlockState(BlockPos position) { return null; }
    public RegistryAccess registryAccess() { return null; }
    public LevelData getLevelData() { return null; }
}
