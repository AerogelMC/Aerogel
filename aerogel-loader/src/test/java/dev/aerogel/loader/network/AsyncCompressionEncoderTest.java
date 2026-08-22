package dev.aerogel.loader.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncCompressionEncoderTest {
    @Test
    void preservesPacketAndFlushOrderWithinOneConnection() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        List<Integer> encodedOrder = Collections.synchronizedList(new ArrayList<>());
        AsyncCompressionEncoder.CompressionEngine engine =
            (deflater, threshold, input, output) -> {
                int marker = input.getUnsignedByte(input.readerIndex());
                encodedOrder.add(marker);
                if (invocations.incrementAndGet() == 1) {
                    firstStarted.countDown();
                    await(releaseFirst);
                }
                DirectPacketCompressor.encode(
                    deflater, threshold, input, output);
            };
        EmbeddedChannel channel = new EmbeddedChannel(
            new AsyncCompressionEncoder(0, workers, engine));
        try {
            ChannelFuture first = channel.write(Unpooled.directBuffer().writeByte(1));
            ChannelFuture second = channel.write(Unpooled.directBuffer().writeByte(2));
            channel.flush();

            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            assertEquals(1, invocations.get(),
                "A second packet from one connection must not compress concurrently");
            releaseFirst.countDown();
            pumpUntil(channel, () -> first.isDone() && second.isDone());

            assertTrue(first.isSuccess());
            assertTrue(second.isSuccess());
            assertEquals(List.of(1, 2), encodedOrder);
            assertArrayEquals(new byte[] { 1 }, decode(channel.readOutbound()));
            assertArrayEquals(new byte[] { 2 }, decode(channel.readOutbound()));
        } finally {
            releaseFirst.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownNow();
        }
    }

    @Test
    void compressesDifferentConnectionsOnDifferentWorkersConcurrently()
        throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AsyncCompressionEncoder.CompressionEngine engine =
            (deflater, threshold, input, output) -> {
                bothStarted.countDown();
                await(release);
                DirectPacketCompressor.encode(
                    deflater, threshold, input, output);
            };
        EmbeddedChannel firstChannel = new EmbeddedChannel(
            new AsyncCompressionEncoder(0, workers, engine));
        EmbeddedChannel secondChannel = new EmbeddedChannel(
            new AsyncCompressionEncoder(0, workers, engine));
        try {
            ChannelFuture first = firstChannel.writeAndFlush(
                Unpooled.directBuffer().writeByte(3));
            ChannelFuture second = secondChannel.writeAndFlush(
                Unpooled.directBuffer().writeByte(4));

            assertTrue(bothStarted.await(5, TimeUnit.SECONDS),
                "Independent connection lanes did not run in parallel");
            release.countDown();
            pumpUntil(firstChannel, first::isDone);
            pumpUntil(secondChannel, second::isDone);

            assertTrue(first.isSuccess());
            assertTrue(second.isSuccess());
            assertArrayEquals(new byte[] { 3 }, decode(firstChannel.readOutbound()));
            assertArrayEquals(new byte[] { 4 }, decode(secondChannel.readOutbound()));
        } finally {
            release.countDown();
            firstChannel.finishAndReleaseAll();
            secondChannel.finishAndReleaseAll();
            workers.shutdownNow();
        }
    }

    @Test
    void keepsPacketsBelowThresholdOnTheCallingEventLoop() {
        AtomicInteger compressionCalls = new AtomicInteger();
        EmbeddedChannel channel = new EmbeddedChannel(new AsyncCompressionEncoder(
            256,
            Runnable::run,
            (deflater, threshold, input, output) ->
                compressionCalls.incrementAndGet()));
        try {
            assertTrue(channel.writeOutbound(Unpooled.directBuffer().writeByte(9)));
            assertEquals(0, compressionCalls.get());
            ByteBuf encoded = channel.readOutbound();
            try {
                assertEquals(0, readVarInt(encoded));
                assertEquals(9, encoded.readUnsignedByte());
            } finally {
                encoded.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void continuesTheLaneInOrderAfterCompressionFailure() throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        AtomicInteger invocations = new AtomicInteger();
        AsyncCompressionEncoder.CompressionEngine engine =
            (deflater, threshold, input, output) -> {
                if (invocations.incrementAndGet() == 1) {
                    throw new IllegalStateException("expected test failure");
                }
                DirectPacketCompressor.encode(
                    deflater, threshold, input, output);
            };
        EmbeddedChannel channel = new EmbeddedChannel(
            new AsyncCompressionEncoder(0, workers, engine));
        try {
            ChannelFuture first = channel.write(
                Unpooled.directBuffer().writeByte(5));
            ChannelFuture second = channel.writeAndFlush(
                Unpooled.directBuffer().writeByte(6));

            pumpUntil(channel, () -> first.isDone() && second.isDone());

            assertFalse(first.isSuccess());
            assertTrue(second.isSuccess());
            assertEquals(2, invocations.get());
            assertArrayEquals(new byte[] { 6 }, decode(channel.readOutbound()));
        } finally {
            channel.finishAndReleaseAll();
            workers.shutdownNow();
        }
    }

    @Test
    void removingAnActiveLaneReleasesAndFailsEveryQueuedPacket()
        throws Exception {
        ExecutorService workers = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncCompressionEncoder.CompressionEngine engine =
            (deflater, threshold, input, output) -> {
                started.countDown();
                await(release);
                DirectPacketCompressor.encode(
                    deflater, threshold, input, output);
            };
        AsyncCompressionEncoder encoder =
            new AsyncCompressionEncoder(0, workers, engine);
        EmbeddedChannel channel = new EmbeddedChannel(encoder);
        ByteBuf firstInput = Unpooled.directBuffer().writeByte(7);
        ByteBuf queuedInput = Unpooled.directBuffer().writeByte(8);
        try {
            ChannelFuture first = channel.write(firstInput);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            ChannelFuture queued = channel.writeAndFlush(queuedInput);

            channel.pipeline().remove(encoder);

            assertTrue(queued.isDone());
            assertFalse(queued.isSuccess());
            assertEquals(0, queuedInput.refCnt());
            release.countDown();
            pumpUntil(channel, first::isDone);
            assertFalse(first.isSuccess());
            assertEquals(0, firstInput.refCnt());
        } finally {
            release.countDown();
            channel.finishAndReleaseAll();
            workers.shutdownNow();
        }
    }

    private static void pumpUntil(
        EmbeddedChannel channel, java.util.function.BooleanSupplier done
    ) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!done.getAsBoolean()) {
            channel.runPendingTasks();
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("Timed out waiting for compression completion");
            }
            Thread.onSpinWait();
        }
        channel.runPendingTasks();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test lane");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static byte[] decode(ByteBuf encoded) throws Exception {
        try {
            int length = readVarInt(encoded);
            byte[] compressed = new byte[encoded.readableBytes()];
            encoded.readBytes(compressed);
            Inflater inflater = new Inflater();
            try {
                inflater.setInput(compressed);
                byte[] result = new byte[length];
                assertEquals(length, inflater.inflate(result));
                return result;
            } finally {
                inflater.end();
            }
        } finally {
            encoded.release();
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
