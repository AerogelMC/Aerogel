package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class CompressionDecoder extends ByteToMessageDecoder {
    public CompressionDecoder(int threshold, boolean validateDecompressed) { }

    public void setThreshold(int threshold, boolean validateDecompressed) { }

    @Override
    protected void decode(
        ChannelHandlerContext context, ByteBuf input, List<Object> output
    ) { }
}
