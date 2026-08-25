package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.BlockTargetPacket;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket")
abstract class BlockEntityTagQueryTargetMixin implements BlockTargetPacket {
    @Shadow public abstract BlockPos getPos();

    @Override public BlockPos aerogel$targetBlock() { return getPos(); }
}
