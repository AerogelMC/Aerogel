package net.minecraft.world.entity.ai.navigation;

import net.minecraft.core.BlockPos;

public abstract class PathNavigation {
    public boolean shouldRecomputePath(BlockPos position) { return false; }
    public void recomputePath() { }
}
