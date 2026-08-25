package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.BlockTargetPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.network.protocol.game.ServerboundPlayerActionPacket")
abstract class PlayerActionBlockTargetMixin implements BlockTargetPacket {
    @Override
    public BlockPos aerogel$targetBlock() {
        ServerboundPlayerActionPacket packet =
            (ServerboundPlayerActionPacket) (Object) this;
        return switch (packet.getAction()) {
            case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK ->
                packet.getPos();
            default -> null;
        };
    }
}
