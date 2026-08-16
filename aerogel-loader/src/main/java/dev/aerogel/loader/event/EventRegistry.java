package dev.aerogel.loader.event;

import dev.aerogel.api.event.AerogelEvent;
import dev.aerogel.api.event.CancellableEvent;
import dev.aerogel.api.event.EventBus;
import dev.aerogel.api.event.EventPriority;
import dev.aerogel.api.event.EventRegistration;
import dev.aerogel.loader.plugin.PluginFailures;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EventRegistry {
    private static final Comparator<Binding> ORDER = Comparator
        .comparing((Binding binding) -> binding.priority().ordinal())
        .thenComparingLong(Binding::sequence);

    private final ConcurrentHashMap<Class<?>, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Class<?>, Binding[]> dispatchCache = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public OwnedEventBus owner(String pluginId, Logger logger) {
        return new OwnedEventBus(this, pluginId, logger);
    }

    public <E extends AerogelEvent> E post(E event) {
        Objects.requireNonNull(event, "event");
        Class<?> eventClass = event.getClass();
        Binding[] bindings = dispatchCache.computeIfAbsent(eventClass, this::resolveBindings);
        CancellableEvent cancellable = event instanceof CancellableEvent value ? value : null;
        for (Binding binding : bindings) {
            if (!binding.registration().active()) {
                continue;
            }
            boolean cancelledBefore = cancellable != null && cancellable.isCancelled();
            if (cancelledBefore && !binding.receiveCancelled() && binding.priority() != EventPriority.MONITOR) {
                continue;
            }
            try {
                binding.invoker().invoke(event);
                if (cancellable != null && binding.priority() == EventPriority.MONITOR
                    && cancellable.isCancelled() != cancelledBefore) {
                    cancellable.setCancelled(cancelledBefore);
                    throw new IllegalStateException("MONITOR listeners cannot change cancellation state");
                }
            } catch (Throwable throwable) {
                PluginFailures.rethrowFatal(throwable);
                binding.logger().log(Level.SEVERE,
                    "Plugin " + binding.pluginId() + " listener failed for " + eventClass.getName(),
                    throwable);
            }
        }
        return event;
    }

    /**
     * Returns whether posting {@code eventType} can reach at least one active listener.
     * Hot vanilla hooks use this before collecting old state or allocating an event.
     */
    public boolean hasListeners(Class<? extends AerogelEvent> eventType) {
        Objects.requireNonNull(eventType, "eventType");
        return dispatchCache.computeIfAbsent(eventType, this::resolveBindings).length != 0;
    }

    private EventRegistration add(
        OwnedEventBus owner,
        Class<? extends AerogelEvent> eventType,
        EventPriority priority,
        boolean receiveCancelled,
        Invoker invoker
    ) {
        Bucket bucket = buckets.computeIfAbsent(eventType, ignored -> new Bucket());
        Registration registration = new Registration(owner, bucket);
        Binding binding = new Binding(owner.pluginId, owner.logger, priority, receiveCancelled,
            sequence.getAndIncrement(), invoker, registration);
        registration.binding = binding;
        owner.registrations.add(registration);
        bucket.add(binding);
        dispatchCache.clear();
        if (owner.closed.get()) {
            registration.close();
            throw new IllegalStateException("Plugin event scope is already closed: " + owner.pluginId);
        }
        return registration;
    }

    private Binding[] resolveBindings(Class<?> eventType) {
        return buckets.entrySet().stream()
            .filter(entry -> entry.getKey().isAssignableFrom(eventType))
            .flatMap(entry -> Arrays.stream(entry.getValue().snapshot()))
            .sorted(ORDER)
            .toArray(Binding[]::new);
    }

    @FunctionalInterface
    interface Invoker {
        void invoke(AerogelEvent event) throws Throwable;
    }

    public static final class OwnedEventBus implements EventBus, AutoCloseable {
        private final EventRegistry registry;
        private final String pluginId;
        private final Logger logger;
        private final Set<Registration> registrations = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean closed = new AtomicBoolean();

        private OwnedEventBus(EventRegistry registry, String pluginId, Logger logger) {
            this.registry = registry;
            this.pluginId = pluginId;
            this.logger = logger;
        }

        @Override
        public <E extends AerogelEvent> EventRegistration listen(
            Class<E> eventType,
            EventPriority priority,
            boolean receiveCancelled,
            Consumer<? super E> listener
        ) {
            Objects.requireNonNull(listener, "listener");
            return register(eventType, priority, receiveCancelled, event -> listener.accept(eventType.cast(event)));
        }

        public EventRegistration registerMethod(
            Class<? extends AerogelEvent> eventType,
            EventPriority priority,
            boolean receiveCancelled,
            MethodHandle handle
        ) {
            Objects.requireNonNull(handle, "handle");
            MethodHandle exact = handle.asType(MethodType.methodType(void.class, AerogelEvent.class));
            return register(eventType, priority, receiveCancelled, event -> {
                exact.invokeExact(event);
            });
        }

        private EventRegistration register(
            Class<? extends AerogelEvent> eventType,
            EventPriority priority,
            boolean receiveCancelled,
            Invoker invoker
        ) {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(priority, "priority");
            if (closed.get()) {
                throw new IllegalStateException("Plugin event scope is already closed: " + pluginId);
            }
            return registry.add(this, eventType, priority, receiveCancelled, invoker);
        }

        @Override
        public <E extends AerogelEvent> E post(E event) {
            if (closed.get()) {
                throw new IllegalStateException("Plugin event scope is already closed: " + pluginId);
            }
            return registry.post(event);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            for (Registration registration : registrations.toArray(Registration[]::new)) {
                registration.close();
            }
        }
    }

    private static final class Bucket {
        private volatile Binding[] snapshot = new Binding[0];

        synchronized void add(Binding binding) {
            Binding[] next = Arrays.copyOf(snapshot, snapshot.length + 1);
            next[next.length - 1] = binding;
            Arrays.sort(next, ORDER);
            snapshot = next;
        }

        synchronized void remove(Binding binding) {
            snapshot = Arrays.stream(snapshot)
                .filter(candidate -> candidate != binding)
                .toArray(Binding[]::new);
        }

        Binding[] snapshot() {
            return snapshot;
        }
    }

    private static final class Registration implements EventRegistration {
        private final OwnedEventBus owner;
        private final Bucket bucket;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private Binding binding;

        private Registration(OwnedEventBus owner, Bucket bucket) {
            this.owner = owner;
            this.bucket = bucket;
        }

        @Override
        public boolean active() {
            return active.get();
        }

        @Override
        public void close() {
            if (!active.compareAndSet(true, false)) {
                return;
            }
            bucket.remove(binding);
            owner.registrations.remove(this);
            owner.registry.dispatchCache.clear();
        }
    }

    private record Binding(
        String pluginId,
        Logger logger,
        EventPriority priority,
        boolean receiveCancelled,
        long sequence,
        Invoker invoker,
        Registration registration
    ) {
    }
}
