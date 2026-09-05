package dev.aerogel.loader.context;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Sparse owner index whose buckets cannot be detached during publication. */
final class ContextRegistrationIndex<K, E, V> {
    private final ConcurrentHashMap<K, Bucket<E, V>> buckets = new ConcurrentHashMap<>();

    void put(K owner, E entity, V registration) {
        while (true) {
            Bucket<E, V> bucket = buckets.computeIfAbsent(owner, ignored -> new Bucket<>());
            if (!bucket.acquire()) {
                Thread.onSpinWait();
                continue;
            }
            try {
                bucket.entries.put(entity, registration);
                return;
            } finally {
                bucket.publishers.decrementAndGet();
                retireEmpty(owner, bucket);
            }
        }
    }

    void remove(K owner, E entity, V registration) {
        while (true) {
            Bucket<E, V> bucket = buckets.get(owner);
            if (bucket == null) return;
            if (!bucket.acquire()) {
                Thread.onSpinWait();
                continue;
            }
            try {
                bucket.entries.remove(entity, registration);
                return;
            } finally {
                bucket.publishers.decrementAndGet();
                retireEmpty(owner, bucket);
            }
        }
    }

    private void retireEmpty(K owner, Bucket<E, V> bucket) {
        if (!bucket.entries.isEmpty() || !bucket.publishers.compareAndSet(0, -1)) return;
        // The first empty observation can race an entire acquire/put/release.
        // Exclude publishers before checking again, so that ABA cannot detach
        // a bucket containing a newly published registration.
        if (!bucket.entries.isEmpty()) {
            bucket.publishers.set(0);
            return;
        }
        // Bucket uses identity equality: an old bucket cannot remove a replacement.
        buckets.remove(owner, bucket);
    }

    ConcurrentHashMap<E, V> get(K owner) {
        Bucket<E, V> bucket = buckets.get(owner);
        return bucket == null ? null : bucket.entries;
    }

    ArrayList<Map.Entry<K, ConcurrentHashMap<E, V>>> snapshot() {
        ArrayList<Map.Entry<K, ConcurrentHashMap<E, V>>> result = new ArrayList<>(buckets.size());
        buckets.forEach((owner, bucket) -> result.add(Map.entry(owner, bucket.entries)));
        return result;
    }

    boolean isEmpty() { return buckets.isEmpty(); }

    /** Called only after the scheduler has stopped admitting owner work. */
    void clear() { buckets.clear(); }

    private static final class Bucket<E, V> {
        private final ConcurrentHashMap<E, V> entries = new ConcurrentHashMap<>();
        // Nonnegative: active publishers. -1: retirement excludes new publishers.
        private final AtomicInteger publishers = new AtomicInteger();

        private boolean acquire() {
            int observed = publishers.get();
            while (observed >= 0) {
                if (publishers.compareAndSet(observed, observed + 1)) return true;
                observed = publishers.get();
            }
            return false;
        }
    }
}
