package dev.aerogel.loader.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import dev.aerogel.api.storage.DataCodec;
import dev.aerogel.api.storage.DataFile;
import dev.aerogel.api.storage.StorageException;
import dev.aerogel.api.storage.StorageOptions;
import dev.aerogel.api.storage.StorageService;
import dev.aerogel.api.storage.TypeRef;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Plugin-confined storage with coalesced writes and crash-safe replacement. */
final class ManagedStorageService implements StorageService {
    private static final Gson JSON = new GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    private static final ExecutorService IO = Executors.newFixedThreadPool(
        Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
        daemonFactory("Aerogel storage I/O")
    );
    private static final ScheduledThreadPoolExecutor TIMER = timer();

    private final PluginApiScope scope;
    private final Path root;
    private final Logger logger;
    private final Map<Path, ManagedDataFile<?>> files = new HashMap<>();
    private final List<ManagedDataFile<?>> awaitingServer = new ArrayList<>();
    private final MinecraftJsonSupport minecraftJson;
    private boolean serverReady;

    ManagedStorageService(PluginApiScope scope, Path dataDirectory, Logger logger) {
        this.scope = Objects.requireNonNull(scope, "scope");
        this.root = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.minecraftJson = new MinecraftJsonSupport(scope);
    }

    @Override
    public <T> DataFile<T> json(
        Path path,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    ) {
        Objects.requireNonNull(type, "type");
        Type javaType = type.type();
        return open(path, new JsonCodec<>(javaType), defaultValue, options);
    }

    @Override
    public <T> DataFile<T> minecraftJson(
        Path path,
        TypeRef<T> type,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    ) {
        Objects.requireNonNull(type, "type");
        return openManaged(path, minecraftJson.typed(type.type()), defaultValue, options, true);
    }

    @Override
    public <T> DataFile<T> codecJson(
        Path path,
        Codec<T> codec,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    ) {
        return openManaged(path, minecraftJson.codec(codec), defaultValue, options, true);
    }

    @Override
    public synchronized <T> DataFile<T> open(
        Path path,
        DataCodec<T> codec,
        Supplier<? extends T> defaultValue,
        StorageOptions options
    ) {
        return openManaged(path, codec, defaultValue, options, false);
    }

    private synchronized <T> DataFile<T> openManaged(
        Path path,
        DataCodec<T> codec,
        Supplier<? extends T> defaultValue,
        StorageOptions options,
        boolean requiresServer
    ) {
        Path resolved = resolve(path);
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(options, "options");
        if (files.containsKey(resolved)) {
            throw new IllegalStateException("A managed data file is already open: " + resolved);
        }
        ManagedDataFile<T> file = new ManagedDataFile<>(
            this, resolved, codec, defaultValue, options, logger);
        files.put(resolved, file);
        try {
            scope.own(file);
            if (requiresServer && !serverReady) awaitingServer.add(file);
            else file.start();
            return file;
        } catch (RuntimeException exception) {
            files.remove(resolved, file);
            file.close();
            throw exception;
        }
    }

    synchronized void released(Path path, ManagedDataFile<?> file) {
        files.remove(path, file);
        awaitingServer.remove(file);
    }

    synchronized void serverReady() {
        if (serverReady) return;
        serverReady = true;
        List<ManagedDataFile<?>> pending = List.copyOf(awaitingServer);
        awaitingServer.clear();
        for (ManagedDataFile<?> file : pending) file.start();
    }

    void verifyTarget(Path target, boolean createParent) throws IOException {
        Path parent = target.getParent();
        if (createParent) Files.createDirectories(parent);
        if (Files.isSymbolicLink(target)) {
            throw new StorageException("Managed data files must not be symbolic links: " + target);
        }
        Files.createDirectories(root);
        Path realRoot = root.toRealPath();
        Path realParent = parent.toRealPath();
        if (!realParent.startsWith(realRoot)) {
            throw new StorageException("Managed storage path escapes through a symbolic link: " + target);
        }
    }

    private Path resolve(Path requested) {
        Objects.requireNonNull(requested, "path");
        if (requested.toString().isBlank()) throw new IllegalArgumentException("Storage path must not be blank");
        Path resolved = (requested.isAbsolute() ? requested : root.resolve(requested))
            .toAbsolutePath().normalize();
        if (resolved.equals(root) || !resolved.startsWith(root)) {
            throw new IllegalArgumentException("Storage path must stay inside " + root + ": " + requested);
        }
        return resolved;
    }

    private static ThreadFactory daemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.setContextClassLoader(ManagedStorageService.class.getClassLoader());
            return thread;
        };
    }

    private static ScheduledThreadPoolExecutor timer() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
            1, daemonFactory("Aerogel storage timer"));
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static final class JsonCodec<T> implements DataCodec<T> {
        private final Type type;

        private JsonCodec(Type type) {
            this.type = type;
        }

        @Override
        public byte[] encode(T value) {
            return JSON.toJson(value, type).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public T decode(byte[] encoded) {
            @SuppressWarnings("unchecked")
            T value = (T) JSON.fromJson(new String(encoded, StandardCharsets.UTF_8), type);
            if (value == null) throw new StorageException("JSON decoded to null for " + type.getTypeName());
            return value;
        }
    }

    private static final class ManagedDataFile<T> implements DataFile<T> {
        private enum State { OPEN, CLOSING, CLOSED }

        private final ManagedStorageService owner;
        private final Path path;
        private final DataCodec<T> codec;
        private final Supplier<? extends T> defaultValue;
        private final StorageOptions options;
        private final Logger logger;
        private final Object lock = new Object();
        private final List<Waiter> waiters = new ArrayList<>();
        private final CompletableFuture<T> load = new CompletableFuture<>();

        private State state = State.OPEN;
        private boolean started;
        private T value;
        private boolean loaded;
        private long revision;
        private long savedRevision;
        private boolean writerQueued;
        private ScheduledFuture<?> scheduledSave;
        private volatile Throwable lastFailure;

        private ManagedDataFile(
            ManagedStorageService owner,
            Path path,
            DataCodec<T> codec,
            Supplier<? extends T> defaultValue,
            StorageOptions options,
            Logger logger
        ) {
            this.owner = owner;
            this.path = path;
            this.codec = codec;
            this.defaultValue = defaultValue;
            this.options = options;
            this.logger = logger;
        }

        private void start() {
            synchronized (lock) {
                if (state != State.OPEN) return;
                started = true;
            }
            IO.execute(this::readInitial);
        }

        @Override
        public Path path() {
            return path;
        }

        @Override
        public CompletableFuture<T> load() {
            return load.copy();
        }

        @Override
        public boolean loaded() {
            synchronized (lock) {
                return loaded;
            }
        }

        @Override
        public T value() {
            synchronized (lock) {
                checkOpen();
                checkLoaded();
                return value;
            }
        }

        @Override
        public void set(T value) {
            synchronized (lock) {
                checkOpen();
                checkLoaded();
                this.value = Objects.requireNonNull(value, "value");
                changedLocked();
            }
        }

        @Override
        public T update(UnaryOperator<T> updater) {
            Objects.requireNonNull(updater, "updater");
            synchronized (lock) {
                checkOpen();
                checkLoaded();
                value = Objects.requireNonNull(updater.apply(value), "updated value");
                changedLocked();
                return value;
            }
        }

        @Override
        public void edit(Consumer<T> editor) {
            Objects.requireNonNull(editor, "editor");
            synchronized (lock) {
                checkOpen();
                checkLoaded();
                try {
                    editor.accept(value);
                } finally {
                    changedLocked();
                }
            }
        }

        @Override
        public boolean dirty() {
            synchronized (lock) {
                return loaded && revision > savedRevision;
            }
        }

        @Override
        public Optional<Throwable> lastFailure() {
            return Optional.ofNullable(lastFailure);
        }

        @Override
        public CompletableFuture<Void> flush() {
            synchronized (lock) {
                checkOpen();
            }
            return load.thenCompose(ignored -> requestFlush());
        }

        @Override
        public boolean active() {
            synchronized (lock) {
                return state == State.OPEN;
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (state != State.OPEN) return;
                if (!started) {
                    state = State.CLOSED;
                    load.completeExceptionally(new StorageException("Data file was closed before loading: " + path));
                    owner.released(path, this);
                    return;
                }
                state = State.CLOSING;
                cancelScheduledLocked();
            }

            CompletableFuture<Void> completion = load.handle((ignored, failure) -> {
                if (failure != null) return CompletableFuture.<Void>completedFuture(null);
                return requestFlush();
            }).thenCompose(future -> future);

            try {
                completion.get(options.closeTimeout().toNanos(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                StorageException timeout = new StorageException(
                    "Timed out flushing " + path + " during plugin unload", exception);
                lastFailure = timeout;
                logger.log(Level.SEVERE, timeout.getMessage(), timeout);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                StorageException interrupted = new StorageException(
                    "Interrupted while flushing " + path + " during plugin unload", exception);
                lastFailure = interrupted;
                logger.log(Level.SEVERE, interrupted.getMessage(), interrupted);
            } catch (java.util.concurrent.ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                lastFailure = cause;
                logger.log(Level.SEVERE, "Could not flush " + path + " during plugin unload", cause);
            } finally {
                synchronized (lock) {
                    state = State.CLOSED;
                    cancelScheduledLocked();
                }
                owner.released(path, this);
            }
        }

        private void readInitial() {
            try {
                boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
                T initial;
                if (exists) {
                    owner.verifyTarget(path, false);
                    long size = Files.size(path);
                    if (size > options.maximumBytes()) {
                        throw new StorageException("Stored file exceeds " + options.maximumBytes() + " bytes: " + path);
                    }
                    byte[] encoded = Files.readAllBytes(path);
                    if (encoded.length > options.maximumBytes()) {
                        throw new StorageException("Stored file exceeds " + options.maximumBytes() + " bytes: " + path);
                    }
                    initial = Objects.requireNonNull(codec.decode(encoded), "decoded value");
                } else {
                    initial = Objects.requireNonNull(defaultValue.get(), "default value");
                }

                synchronized (lock) {
                    value = initial;
                    loaded = true;
                    revision = exists ? 0 : 1;
                    savedRevision = 0;
                    if (!exists && state == State.OPEN) scheduleSaveLocked();
                }
                load.complete(initial);
            } catch (Exception exception) {
                StorageException failure = exception instanceof StorageException storage
                    ? storage : new StorageException("Could not load " + path, exception);
                lastFailure = failure;
                load.completeExceptionally(failure);
                logger.log(Level.SEVERE, failure.getMessage(), failure);
            }
        }

        private CompletableFuture<Void> requestFlush() {
            synchronized (lock) {
                if (!loaded) {
                    return CompletableFuture.failedFuture(
                        new StorageException("Data file has not loaded successfully: " + path));
                }
                long target = revision;
                if (savedRevision >= target) return CompletableFuture.completedFuture(null);
                CompletableFuture<Void> result = new CompletableFuture<>();
                waiters.add(new Waiter(target, result));
                cancelScheduledLocked();
                queueWriterLocked();
                return result;
            }
        }

        private void changedLocked() {
            revision++;
            scheduleSaveLocked();
        }

        private void scheduleSaveLocked() {
            if (!options.automaticSaving() || state != State.OPEN) return;
            cancelScheduledLocked();
            Duration delay = options.saveDelay();
            scheduledSave = TIMER.schedule(() -> {
                synchronized (lock) {
                    scheduledSave = null;
                    if (state == State.OPEN && loaded && revision > savedRevision) queueWriterLocked();
                }
            }, delay.toNanos(), TimeUnit.NANOSECONDS);
        }

        private void cancelScheduledLocked() {
            if (scheduledSave != null) {
                scheduledSave.cancel(false);
                scheduledSave = null;
            }
        }

        private void queueWriterLocked() {
            if (writerQueued || !loaded || revision <= savedRevision) return;
            writerQueued = true;
            IO.execute(this::writeLoop);
        }

        private void writeLoop() {
            while (true) {
                final byte[] encoded;
                final long targetRevision;
                try {
                    synchronized (lock) {
                        if (!loaded || revision <= savedRevision) {
                            writerQueued = false;
                            return;
                        }
                        targetRevision = revision;
                        encoded = Objects.requireNonNull(codec.encode(value), "encoded value");
                    }
                    if (encoded.length > options.maximumBytes()) {
                        throw new StorageException(
                            "Encoded value exceeds " + options.maximumBytes() + " bytes: " + path);
                    }
                    atomicWrite(path, encoded);
                } catch (Exception exception) {
                    failWrite(exception);
                    return;
                }

                List<CompletableFuture<Void>> completed = new ArrayList<>();
                boolean again;
                synchronized (lock) {
                    savedRevision = Math.max(savedRevision, targetRevision);
                    lastFailure = null;
                    for (int index = waiters.size() - 1; index >= 0; index--) {
                        Waiter waiter = waiters.get(index);
                        if (waiter.revision <= savedRevision) {
                            completed.add(waiter.future);
                            waiters.remove(index);
                        }
                    }
                    again = revision > savedRevision;
                    if (!again) writerQueued = false;
                }
                completed.forEach(future -> future.complete(null));
                if (!again) return;
            }
        }

        private void failWrite(Exception exception) {
            StorageException failure = exception instanceof StorageException storage
                ? storage : new StorageException("Could not save " + path, exception);
            List<CompletableFuture<Void>> failed = new ArrayList<>();
            synchronized (lock) {
                lastFailure = failure;
                writerQueued = false;
                for (Waiter waiter : waiters) failed.add(waiter.future);
                waiters.clear();
            }
            failed.forEach(future -> future.completeExceptionally(failure));
            logger.log(Level.SEVERE, failure.getMessage(), failure);
        }

        private void checkOpen() {
            if (state != State.OPEN) throw new IllegalStateException("Data file is closed: " + path);
        }

        private void checkLoaded() {
            if (!loaded) {
                if (load.isCompletedExceptionally()) {
                    throw new IllegalStateException("Data file failed to load: " + path, lastFailure);
                }
                throw new IllegalStateException("Data file is still loading: " + path);
            }
        }

        private void atomicWrite(Path target, byte[] encoded) throws IOException {
            Path parent = target.getParent();
            owner.verifyTarget(target, true);
            Path temporary = Files.createTempFile(parent, ".aerogel-", ".tmp");
            boolean moved = false;
            try {
                try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    ByteBuffer buffer = ByteBuffer.wrap(encoded);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                try {
                    Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                moved = true;
            } finally {
                if (!moved) Files.deleteIfExists(temporary);
            }
        }

        private record Waiter(long revision, CompletableFuture<Void> future) { }
    }
}
