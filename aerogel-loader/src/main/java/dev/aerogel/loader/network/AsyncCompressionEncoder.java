package dev.aerogel.loader.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.zip.Deflater;

/**
 * Offloads only packets that actually require zlib. One handler instance is one
 * serial connection lane, while different instances may use compression workers
 * concurrently.
 */
public final class AsyncCompressionEncoder extends ChannelDuplexHandler {
    private final CompressionScheduler workers;
    private final CompressionEngine engine;
    private final Deflater deflater = new Deflater();
    private final ArrayDeque<PendingSegment> pending = new ArrayDeque<>();
    private final ArrayDeque<PacketPriority> encodedPriorities = new ArrayDeque<>();
    private int threshold;
    private boolean compressing;
    private boolean terminated;
    private boolean deflaterEnded;
    private long nextWriteSequence;
    private long flushedThroughSequence = -1L;

    public AsyncCompressionEncoder(int threshold) {
        this(threshold, CompressionWorkers::execute, DirectPacketCompressor::encode);
    }

    AsyncCompressionEncoder(
        int threshold, Executor workers, CompressionEngine engine
    ) {
        this(threshold, (priority, task) -> workers.execute(task), engine);
    }

    private AsyncCompressionEncoder(
        int threshold, CompressionScheduler workers, CompressionEngine engine
    ) {
        this.threshold = threshold;
        this.workers = Objects.requireNonNull(workers, "workers");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    /** Called synchronously by PacketEncoder for the ByteBuf it is about to emit. */
    public void markNextWrite(PacketPriority priority) {
        encodedPriorities.addLast(Objects.requireNonNull(priority, "priority"));
    }

    @Override
    public void write(
        ChannelHandlerContext context, Object message, ChannelPromise promise
    ) {
        if (terminated) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(new ClosedChannelException());
            return;
        }
        PacketPriority priority = message instanceof ByteBuf
            ? encodedPriorities.pollFirst() : null;
        if (!compressing && pending.isEmpty()
            && writeImmediate(context, message, promise)) return;
        PendingWrite write = new PendingWrite(
            message, promise,
            priority == null ? PacketPriority.INTERACTIVE : priority,
            nextWriteSequence++);
        if (compressing || !pending.isEmpty()) {
            enqueue(write);
            if (!compressing) drain(context);
            return;
        }
        writeNow(context, write);
    }

    /** Avoids a queue-node allocation for the common unqueued small packet. */
    private boolean writeImmediate(
        ChannelHandlerContext context, Object message, ChannelPromise promise
    ) {
        if (!(message instanceof ByteBuf input)) {
            context.write(message, promise);
            return true;
        }
        if (input.readableBytes() >= threshold) return false;

        ByteBuf output = context.alloc().ioBuffer(input.readableBytes() + 1);
        try {
            DirectPacketCompressor.encode(deflater, threshold, input, output);
            context.write(output, promise);
            output = null;
        } catch (Throwable error) {
            promise.tryFailure(error);
        } finally {
            ReferenceCountUtil.release(input);
            ReferenceCountUtil.release(output);
        }
        return true;
    }

    @Override
    public void flush(ChannelHandlerContext context) {
        flushedThroughSequence = nextWriteSequence - 1L;
        context.flush();
    }

    @Override
    public void close(ChannelHandlerContext context, ChannelPromise promise) {
        if (compressing) {
            enqueueBarrier(new PendingClose(promise));
        } else {
            context.close(promise);
        }
    }

    @Override
    public void disconnect(ChannelHandlerContext context, ChannelPromise promise) {
        if (compressing) {
            enqueueBarrier(new PendingDisconnect(promise));
        } else {
            context.disconnect(promise);
        }
    }

    @Override
    public void deregister(ChannelHandlerContext context, ChannelPromise promise) {
        if (compressing) {
            enqueueBarrier(new PendingDeregister(promise));
        } else {
            context.deregister(promise);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        terminate(new ClosedChannelException());
        context.fireChannelInactive();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext context) {
        terminate(new ClosedChannelException());
    }

    private void writeNow(
        ChannelHandlerContext context, PendingWrite write
    ) {
        Object message = write.message;
        ChannelPromise promise = write.promise;
        if (!(message instanceof ByteBuf input)) {
            context.write(message, promise);
            finishWrite(context, write);
            return;
        }
        if (input.readableBytes() >= threshold) {
            startCompression(context, write, input, threshold);
            return;
        }

        ByteBuf output = context.alloc().ioBuffer(input.readableBytes() + 1);
        try {
            DirectPacketCompressor.encode(deflater, threshold, input, output);
            context.write(output, promise);
            output = null;
        } catch (Throwable error) {
            promise.tryFailure(error);
        } finally {
            ReferenceCountUtil.release(input);
            ReferenceCountUtil.release(output);
            finishWrite(context, write);
        }
    }

    private void startCompression(
        ChannelHandlerContext context,
        PendingWrite write, ByteBuf input,
        int packetThreshold
    ) {
        compressing = true;
        try {
            workers.execute(write.priority, () ->
                compress(context, write, input, packetThreshold));
        } catch (Throwable error) {
            compressing = false;
            ReferenceCountUtil.release(input);
            write.promise.tryFailure(error);
            finishWrite(context, write);
            drain(context);
        }
    }

    private void compress(
        ChannelHandlerContext context,
        PendingWrite write, ByteBuf input,
        int packetThreshold
    ) {
        ByteBuf output = context.alloc().ioBuffer();
        Throwable failure = null;
        try {
            engine.encode(deflater, packetThreshold, input, output);
        } catch (Throwable error) {
            failure = error;
        } finally {
            ReferenceCountUtil.release(input);
        }

        ByteBuf result = output;
        Throwable resultFailure = failure;
        try {
            context.executor().execute(() ->
                complete(context, write, result, resultFailure));
        } catch (Throwable error) {
            ReferenceCountUtil.release(result);
            write.promise.tryFailure(
                resultFailure == null ? error : resultFailure);
            compressing = false;
            endDeflater();
        }
    }

    private void complete(
        ChannelHandlerContext context,
        PendingWrite write,
        ByteBuf output,
        Throwable failure
    ) {
        ChannelPromise promise = write.promise;
        try {
            if (terminated) {
                promise.tryFailure(failure == null
                    ? new ClosedChannelException() : failure);
            } else if (failure != null) {
                promise.tryFailure(failure);
            } else {
                context.write(output, promise);
                output = null;
            }
        } catch (Throwable error) {
            promise.tryFailure(error);
        } finally {
            ReferenceCountUtil.release(output);
            compressing = false;
            finishWrite(context, write);
            if (terminated) {
                endDeflater();
            } else {
                drain(context);
            }
        }
    }

    private void drain(ChannelHandlerContext context) {
        while (!compressing) {
            PendingOperation operation = pollNextOperation();
            if (operation == null) return;
            operation.run(this, context);
        }
    }

    private PendingOperation pollNextOperation() {
        while (true) {
            PendingSegment segment = pending.peekFirst();
            if (segment == null) return null;
            PendingWrite write = segment.interactive.pollFirst();
            if (write != null) return write;
            write = segment.bulk.pollFirst();
            if (write != null) return write;
            PendingOperation barrier = segment.barrier;
            pending.removeFirst();
            if (barrier != null) return barrier;
        }
    }

    private void enqueue(PendingWrite write) {
        PendingSegment segment = tailSegment();
        if (write.priority == PacketPriority.BARRIER) {
            segment.barrier = write;
            pending.addLast(new PendingSegment());
        } else if (write.priority == PacketPriority.BULK) {
            segment.bulk.addLast(write);
        } else {
            segment.interactive.addLast(write);
        }
    }

    private void enqueueBarrier(PendingOperation barrier) {
        PendingSegment segment = tailSegment();
        segment.barrier = barrier;
        pending.addLast(new PendingSegment());
    }

    private PendingSegment tailSegment() {
        PendingSegment segment = pending.peekLast();
        if (segment != null) return segment;
        segment = new PendingSegment();
        pending.addLast(segment);
        return segment;
    }

    private void finishWrite(ChannelHandlerContext context, PendingWrite write) {
        if (write.sequence <= flushedThroughSequence) context.flush();
    }

    private void terminate(Throwable failure) {
        if (terminated) return;
        terminated = true;
        encodedPriorities.clear();
        PendingSegment segment;
        while ((segment = pending.pollFirst()) != null) segment.fail(failure);
        if (!compressing) endDeflater();
    }

    private synchronized void endDeflater() {
        if (deflaterEnded) return;
        deflaterEnded = true;
        deflater.end();
    }

    @FunctionalInterface
    interface CompressionEngine {
        void encode(
            Deflater deflater, int threshold, ByteBuf input, ByteBuf output
        );
    }

    @FunctionalInterface
    private interface CompressionScheduler {
        void execute(PacketPriority priority, Runnable task);
    }

    private interface PendingOperation {
        void run(AsyncCompressionEncoder encoder, ChannelHandlerContext context);

        default void fail(Throwable failure) {
        }
    }

    private static final class PendingWrite implements PendingOperation {
        private final Object message;
        private final ChannelPromise promise;
        private final PacketPriority priority;
        private final long sequence;

        private PendingWrite(
            Object message, ChannelPromise promise, PacketPriority priority,
            long sequence
        ) {
            this.message = message;
            this.promise = promise;
            this.priority = priority;
            this.sequence = sequence;
        }

        @Override
        public void run(
            AsyncCompressionEncoder encoder, ChannelHandlerContext context
        ) {
            encoder.writeNow(context, this);
        }

        @Override
        public void fail(Throwable failure) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(failure);
        }
    }

    private static final class PendingSegment {
        private final ArrayDeque<PendingWrite> interactive = new ArrayDeque<>();
        private final ArrayDeque<PendingWrite> bulk = new ArrayDeque<>();
        private PendingOperation barrier;

        private void fail(Throwable failure) {
            PendingWrite write;
            while ((write = interactive.pollFirst()) != null) write.fail(failure);
            while ((write = bulk.pollFirst()) != null) write.fail(failure);
            if (barrier != null) barrier.fail(failure);
        }
    }

    private record PendingClose(ChannelPromise promise) implements PendingOperation {
        @Override
        public void run(
            AsyncCompressionEncoder encoder, ChannelHandlerContext context
        ) {
            context.close(promise);
        }

        @Override public void fail(Throwable failure) { promise.tryFailure(failure); }
    }

    private record PendingDisconnect(ChannelPromise promise) implements PendingOperation {
        @Override
        public void run(
            AsyncCompressionEncoder encoder, ChannelHandlerContext context
        ) {
            context.disconnect(promise);
        }

        @Override public void fail(Throwable failure) { promise.tryFailure(failure); }
    }

    private record PendingDeregister(ChannelPromise promise) implements PendingOperation {
        @Override
        public void run(
            AsyncCompressionEncoder encoder, ChannelHandlerContext context
        ) {
            context.deregister(promise);
        }

        @Override public void fail(Throwable failure) { promise.tryFailure(failure); }
    }
}
