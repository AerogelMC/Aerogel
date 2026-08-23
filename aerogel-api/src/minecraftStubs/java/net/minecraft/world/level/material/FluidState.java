package net.minecraft.world.level.material;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.RandomSource;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class FluidState {
    public Fluid getType() { return null; }
    public int getAmount() { return 0; }
    public boolean isEmpty() { return false; }
    public boolean isSource() { return false; }
    public boolean isSourceOfType(Fluid fluid) { return false; }
    public float getOwnHeight() { return 0.0F; }
    public boolean isRandomlyTicking() { return false; }
    public void tick(ServerLevel level, BlockPos position, BlockState state) { }
    public void randomTick(ServerLevel level, BlockPos position, RandomSource random) { }
}
