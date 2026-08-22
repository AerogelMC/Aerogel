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
    private final Executor workers;
    private final CompressionEngine engine;
    private final Deflater deflater = new Deflater();
    private final ArrayDeque<PendingOperation> pending = new ArrayDeque<>();
    private int threshold;
    private boolean compressing;
    private boolean terminated;
    private boolean deflaterEnded;

    public AsyncCompressionEncoder(int threshold) {
        this(threshold, CompressionWorkers.executor(), DirectPacketCompressor::encode);
    }

    AsyncCompressionEncoder(
        int threshold, Executor workers, CompressionEngine engine
    ) {
        this.threshold = threshold;
        this.workers = Objects.requireNonNull(workers, "workers");
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
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
        if (compressing) {
            pending.addLast(new PendingWrite(message, promise));
            return;
        }
        writeNow(context, message, promise);
    }

    @Override
    public void flush(ChannelHandlerContext context) {
        if (compressing) {
            pending.addLast(PendingFlush.INSTANCE);
        } else {
            context.flush();
        }
    }

    @Override
    public void close(ChannelHandlerContext context, ChannelPromise promise) {
        if (compressing) {
            pending.addLast(new PendingClose(promise));
        } else {
            context.close(promise);
        }
    }

    @Override
    public void disconnect(ChannelHandlerContext context, ChannelPromise promise) {
        if (compressing) {
            pending.addLast(new PendingDisconnect(promise));
        } else {
            context.disconnect(promise);
        }
    }

    @Override
    public void deregister(ChannelHandlerContext context, ChannelPromise promise) {
        if (compressing) {
            pending.addLast(new PendingDeregister(promise));
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
        ChannelHandlerContext context, Object message, ChannelPromise promise
    ) {
        if (!(message instanceof ByteBuf input)) {
            context.write(message, promise);
            return;
        }
        if (input.readableBytes() >= threshold) {
            startCompression(context, input, promise, threshold);
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
        }
    }

    private void startCompression(
        ChannelHandlerContext context,
        ByteBuf input,
        ChannelPromise promise,
        int packetThreshold
    ) {
        compressing = true;
        try {
            workers.execute(() ->
                compress(context, input, promise, packetThreshold));
        } catch (Throwable error) {
            compressing = false;
            ReferenceCountUtil.release(input);
            promise.tryFailure(error);
            drain(context);
        }
    }

    private void compress(
        ChannelHandlerContext context,
        ByteBuf input,
        ChannelPromise promise,
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
                complete(context, promise, result, resultFailure));
        } catch (Throwable error) {
            ReferenceCountUtil.release(result);
            promise.tryFailure(resultFailure == null ? error : resultFailure);
            compressing = false;
            endDeflater();
        }
    }

    private void complete(
        ChannelHandlerContext context,
        ChannelPromise promise,
        ByteBuf output,
        Throwable failure
    ) {
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
            if (terminated) {
                endDeflater();
            } else {
                drain(context);
            }
        }
    }

    private void drain(ChannelHandlerContext context) {
        while (!compressing) {
            PendingOperation operation = pending.pollFirst();
            if (operation == null) return;
            operation.run(this, context);
        }
    }

    private void terminate(Throwable failure) {
        if (terminated) return;
        terminated = true;
        PendingOperation operation;
        while ((operation = pending.pollFirst()) != null) operation.fail(failure);
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

    private interface PendingOperation {
        void run(AsyncCompressionEncoder encoder, ChannelHandlerContext context);

        default void fail(Throwable failure) {
        }
    }

    private record PendingWrite(
        Object message, ChannelPromise promise
    ) implements PendingOperation {
        @Override
        public void run(
            AsyncCompressionEncoder encoder, ChannelHandlerContext context
        ) {
            encoder.writeNow(context, message, promise);
        }

        @Override
        public void fail(Throwable failure) {
            ReferenceCountUtil.release(message);
            promise.tryFailure(failure);
        }
    }

    private enum PendingFlush implements PendingOperation {
        INSTANCE;

        @Override
        public void run(
            AsyncCompressionEncoder encoder, ChannelHandlerContext context
        ) {
            context.flush();
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
