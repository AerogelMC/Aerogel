package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.BlockTargetPacket;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Publishes the command block's protocol-defined target to Context routing. */
@Mixin(targets = "net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket")
abstract class SetCommandBlockTargetMixin implements BlockTargetPacket {
    @Shadow public abstract BlockPos getPos();

    @Override
    public BlockPos aerogel$targetBlock() {
        return getPos();
    }
}
