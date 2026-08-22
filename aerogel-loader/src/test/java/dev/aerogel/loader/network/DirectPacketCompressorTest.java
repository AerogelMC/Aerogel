package dev.aerogel.loader.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DirectPacketCompressorTest {
    @Test
    void leavesPacketsBelowThresholdUncompressed() {
        byte[] payload = "small packet".getBytes(StandardCharsets.UTF_8);
        ByteBuf input = Unpooled.directBuffer().writeBytes(payload);
        ByteBuf output = Unpooled.directBuffer();
        Deflater deflater = new Deflater();
        try {
            DirectPacketCompressor.encode(deflater, payload.length + 1, input, output);
            assertEquals(0, readVarInt(output));
            byte[] actual = new byte[output.readableBytes()];
            output.readBytes(actual);
            assertArrayEquals(payload, actual);
            assertEquals(0, input.readableBytes());
        } finally {
            deflater.end();
            input.release();
            output.release();
        }
    }

    @Test
    void compressesDirectAndCompositeBuffersWithoutChangingBytes() throws Exception {
        byte[] payload = new byte[256 * 1024];
        new Random(42L).nextBytes(payload);
        CompositeByteBuf input = Unpooled.compositeBuffer();
        input.addComponents(true,
            Unpooled.directBuffer().writeBytes(payload, 0, payload.length / 2),
            Unpooled.directBuffer().writeBytes(
                payload, payload.length / 2, payload.length - payload.length / 2));
        ByteBuf output = Unpooled.directBuffer();
        Deflater deflater = new Deflater();
        try {
            DirectPacketCompressor.encode(deflater, 256, input, output);
            int uncompressedLength = readVarInt(output);
            assertEquals(payload.length, uncompressedLength);
            byte[] compressed = new byte[output.readableBytes()];
            output.readBytes(compressed);
            assertArrayEquals(payload, inflate(compressed, uncompressedLength));
            assertEquals(0, input.readableBytes());
        } finally {
            deflater.end();
            input.release();
            output.release();
        }
    }

    @Test
    void resetsDeflaterBetweenPackets() throws Exception {
        byte[] first = new byte[32 * 1024];
        byte[] second = new byte[48 * 1024];
        Arrays.fill(first, (byte) 11);
        new Random(7L).nextBytes(second);
        Deflater deflater = new Deflater();
        try {
            assertRoundTrip(deflater, first);
            assertRoundTrip(deflater, second);
        } finally {
            deflater.end();
        }
    }

    @Test
    void rejectsPacketsAboveProtocolMaximum() {
        ByteBuf input = Unpooled.buffer(
            DirectPacketCompressor.MAXIMUM_UNCOMPRESSED_LENGTH + 1)
            .writerIndex(DirectPacketCompressor.MAXIMUM_UNCOMPRESSED_LENGTH + 1);
        ByteBuf output = Unpooled.buffer();
        Deflater deflater = new Deflater();
        try {
            assertThrows(IllegalArgumentException.class, () ->
                DirectPacketCompressor.encode(deflater, 0, input, output));
        } finally {
            deflater.end();
            input.release();
            output.release();
        }
    }

    private static void assertRoundTrip(Deflater deflater, byte[] payload) throws Exception {
        ByteBuf input = Unpooled.directBuffer().writeBytes(payload);
        ByteBuf output = Unpooled.directBuffer();
        try {
            DirectPacketCompressor.encode(deflater, 0, input, output);
            int length = readVarInt(output);
            byte[] compressed = new byte[output.readableBytes()];
            output.readBytes(compressed);
            assertArrayEquals(payload, inflate(compressed, length));
        } finally {
            input.release();
            output.release();
        }
    }

    private static byte[] inflate(byte[] compressed, int length) throws Exception {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] result = new byte[length];
            assertEquals(length, inflater.inflate(result));
            return result;
        } finally {
            inflater.end();
        }
    }

    private static int readVarInt(ByteBuf input) {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = input.readByte();
            value |= (current & 127) << position++ * 7;
        } while ((current & 128) != 0);
        return value;
    }
}
