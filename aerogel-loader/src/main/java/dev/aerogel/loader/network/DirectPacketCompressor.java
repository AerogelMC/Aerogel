package dev.aerogel.loader.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.EncoderException;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/** Minecraft packet compression without a packet-sized intermediate byte array. */
public final class DirectPacketCompressor {
    static final int MAXIMUM_UNCOMPRESSED_LENGTH = 8 * 1024 * 1024;

    private DirectPacketCompressor() {
    }

    public static void encode(
        Deflater deflater, int threshold, ByteBuf input, ByteBuf output
    ) {
        int readable = input.readableBytes();
        if (readable > MAXIMUM_UNCOMPRESSED_LENGTH) {
            throw new IllegalArgumentException(
                "Packet is larger than the protocol maximum: " + readable);
        }
        if (readable < threshold) {
            writeVarInt(output, 0);
            output.writeBytes(input, readable);
            return;
        }

        writeVarInt(output, readable);
        try {
            int readerIndex = input.readerIndex();
            if (input.nioBufferCount() == 1) {
                consume(deflater, input.nioBuffer(readerIndex, readable), output);
            } else {
                for (ByteBuffer source : input.nioBuffers(readerIndex, readable)) {
                    consume(deflater, source, output);
                }
            }

            deflater.finish();
            while (!deflater.finished()) {
                if (deflate(deflater, output) == 0) {
                    throw new EncoderException(
                        "zlib made no progress while finishing a packet");
                }
            }
            input.skipBytes(readable);
        } finally {
            deflater.reset();
        }
    }

    private static void consume(
        Deflater deflater, ByteBuffer source, ByteBuf output
    ) {
        if (!source.hasRemaining()) return;
        deflater.setInput(source);
        while (!deflater.needsInput()) {
            if (deflate(deflater, output) == 0 && !deflater.needsInput()) {
                throw new EncoderException(
                    "zlib made no progress while consuming packet input");
            }
        }
    }

    private static int deflate(Deflater deflater, ByteBuf output) {
        if (!output.isWritable()) output.ensureWritable(1);
        int writerIndex = output.writerIndex();
        ByteBuffer target = output.internalNioBuffer(
            writerIndex, output.writableBytes());
        int written = deflater.deflate(target);
        output.writerIndex(writerIndex + written);
        return written;
    }

    private static void writeVarInt(ByteBuf output, int value) {
        while ((value & -128) != 0) {
            output.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        output.writeByte(value);
    }
}
