package dev.aerogel.loader.mixin.core;

import dev.aerogel.loader.network.AsyncCompressionEncoder;
import dev.aerogel.loader.network.OutboundPacketPriority;
import dev.aerogel.loader.network.PacketPriority;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Attaches packet semantics before PacketEncoder erases the type into a ByteBuf. */
@Mixin(targets = "net.minecraft.network.PacketEncoder")
abstract class PacketEncoderMixin {
    @Unique private boolean aerogel$insideBundle;

    @Inject(
        method = "encode(Lio/netty/channel/ChannelHandlerContext;"
            + "Lnet/minecraft/network/protocol/Packet;Lio/netty/buffer/ByteBuf;)V",
        at = @At("RETURN")
    )
    private void aerogel$markCompressionPriority(
        ChannelHandlerContext context, Packet<?> packet, ByteBuf output,
        CallbackInfo callbackInfo
    ) {
        if (!(context.pipeline().get("compress")
            instanceof AsyncCompressionEncoder encoder)) return;

        boolean delimiter = packet instanceof BundleDelimiterPacket<?>;
        PacketPriority priority;
        if (delimiter || aerogel$insideBundle || packet.isTerminal()) {
            priority = PacketPriority.BARRIER;
        } else if (OutboundPacketPriority.current() == PacketPriority.BULK
            || packet instanceof ClientboundLevelChunkWithLightPacket
            || packet instanceof ClientboundChunkBatchStartPacket
            || packet instanceof ClientboundChunkBatchFinishedPacket) {
            priority = PacketPriority.BULK;
        } else {
            priority = PacketPriority.INTERACTIVE;
        }
        encoder.markNextWrite(priority);
        if (delimiter) aerogel$insideBundle = !aerogel$insideBundle;
    }
}
