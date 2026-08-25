package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.BlockTargetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.network.protocol.game.ServerboundUseItemOnPacket")
abstract class UseItemOnBlockTargetMixin implements BlockTargetPacket {
    @Override
    public BlockPos aerogel$targetBlock() {
        return ((ServerboundUseItemOnPacket) (Object) this)
            .getHitResult().getBlockPos();
    }
}
