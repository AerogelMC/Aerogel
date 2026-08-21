package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;

public class BlockState {
    public static final Codec<BlockState> CODEC = null;

    public boolean isAir() { return false; }
    public boolean is(Object block) { return false; }
    public boolean isRedstoneConductor(BlockGetter level, BlockPos position) { return false; }
    public void tick(ServerLevel level, BlockPos position, RandomSource random) { }
    public void randomTick(ServerLevel level, BlockPos position, RandomSource random) { }
    public InteractionResult useItemOn(ItemStack item, Level level, Player player,
                                       InteractionHand hand, BlockHitResult hit) { return null; }
    public InteractionResult useWithoutItem(Level level, Player player, BlockHitResult hit) {
        return null;
    }
}
