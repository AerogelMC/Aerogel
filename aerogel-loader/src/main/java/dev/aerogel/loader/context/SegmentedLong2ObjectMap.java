package dev.aerogel.loader.context;

import com.sun.management.HotSpotDiagnosticMXBean;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;

/**
 * A primitive copy-on-write map for light-engine snapshots. Clones share immutable
 * segments, and the updating image copies only the segments it subsequently changes.
 * This preserves the exact shallow-copy semantics of Long2ObjectOpenHashMap.clone()
 * without copying the entire light-section index on every published snapshot.
 */
public final class SegmentedLong2ObjectMap<V> extends Long2ObjectOpenHashMap<V> {
    private static final float LOAD_FACTOR = 0.75F;
    private static final int MAX_ENTRIES_PER_SEGMENT = maxEntriesPerSegment();

    private Segment<V>[] segments;
    private int entryCount;

    public SegmentedLong2ObjectMap(Long2ObjectOpenHashMap<V> source) {
        super(0);
        segments = newSegments(1);
        defaultReturnValue(source.defaultReturnValue());
        LongIterator keys = source.keySet().iterator();
        while (keys.hasNext()) {
            long key = keys.nextLong();
            put(key, source.get(key));
        }
    }

    private SegmentedLong2ObjectMap(
        Segment<V>[] segments, int entryCount, V defaultValue
    ) {
        super(0);
        this.segments = segments;
        this.entryCount = entryCount;
        super.defaultReturnValue(defaultValue);
        for (Segment<V> segment : segments) {
            segment.values.defaultReturnValue(defaultValue);
        }
    }

    public static <V> Long2ObjectOpenHashMap<V> wrap(Long2ObjectOpenHashMap<V> source) {
        return source instanceof SegmentedLong2ObjectMap<V> ? source
            : new SegmentedLong2ObjectMap<>(source);
    }

    @Override
    public V get(long key) {
        return segment(key).values.get(key);
    }

    @Override
    public V getOrDefault(long key, V defaultValue) {
        return segment(key).values.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(long key) {
        return segment(key).values.containsKey(key);
    }

    @Override
    public V put(long key, V value) {
        int index = segmentIndex(key);
        Segment<V> segment = segments[index];
        boolean present = segment.values.containsKey(key);
        if (!present && segment.values.size() >= MAX_ENTRIES_PER_SEGMENT) {
            split();
            index = segmentIndex(key);
            segment = segments[index];
        }
        segment = writableSegment(index, segment);
        V previous = segment.values.put(key, value);
        if (!present) entryCount++;
        return previous;
    }

    @Override
    public V remove(long key) {
        int index = segmentIndex(key);
        Segment<V> segment = segments[index];
        if (!segment.values.containsKey(key)) return defaultReturnValue();
        segment = writableSegment(index, segment);
        V previous = segment.values.remove(key);
        entryCount--;
        return previous;
    }

    @Override
    public int size() {
        return entryCount;
    }

    @Override
    public boolean isEmpty() {
        return entryCount == 0;
    }

    @Override
    public void clear() {
        segments = newSegments(1);
        entryCount = 0;
    }

    @Override
    public void defaultReturnValue(V value) {
        super.defaultReturnValue(value);
        for (int index = 0; index < segments.length; index++) {
            Segment<V> segment = writableSegment(index, segments[index]);
            segment.values.defaultReturnValue(value);
        }
    }

    @Override
    public SegmentedLong2ObjectMap<V> clone() {
        Segment<V>[] copies = newSegmentArray(segments.length);
        for (int index = 0; index < segments.length; index++) {
            Segment<V> segment = segments[index];
            segment.shared = true;
            copies[index] = segment;
        }
        return new SegmentedLong2ObjectMap<>(copies, entryCount, defaultReturnValue());
    }

    int segmentCount() {
        return segments.length;
    }

    static int maximumEntriesPerSegment() {
        return MAX_ENTRIES_PER_SEGMENT;
    }

    private Segment<V> segment(long key) {
        return segments[segmentIndex(key)];
    }

    private int segmentIndex(long key) {
        long mixed = key;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return (int) mixed & (segments.length - 1);
    }

    private void split() {
        Segment<V>[] previous = segments;
        segments = newSegments(Math.multiplyExact(previous.length, 2));
        entryCount = 0;
        for (Segment<V> segment : previous) {
            LongIterator keys = segment.values.keySet().iterator();
            while (keys.hasNext()) {
                long key = keys.nextLong();
                put(key, segment.values.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Segment<V>[] newSegments(int count) {
        Segment<V>[] result = newSegmentArray(count);
        V defaultValue = defaultReturnValue();
        for (int index = 0; index < count; index++) {
            Long2ObjectOpenHashMap<V> values = new Long2ObjectOpenHashMap<>(0);
            values.defaultReturnValue(defaultValue);
            result[index] = new Segment<>(values);
        }
        return result;
    }

    private Segment<V> writableSegment(int index, Segment<V> segment) {
        if (!segment.shared) return segment;
        Long2ObjectOpenHashMap<V> values = segment.values.clone();
        values.defaultReturnValue(defaultReturnValue());
        Segment<V> writable = new Segment<>(values);
        segments[index] = writable;
        return writable;
    }

    @SuppressWarnings("unchecked")
    private Segment<V>[] newSegmentArray(int count) {
        return (Segment<V>[]) new Segment<?>[count];
    }

    private static final class Segment<V> {
        private final Long2ObjectOpenHashMap<V> values;
        private volatile boolean shared;

        private Segment(Long2ObjectOpenHashMap<V> values) {
            this.values = values;
        }
    }

    private static int maxEntriesPerSegment() {
        long regionBytes = collectorRegionBytes();
        if (regionBytes <= 0L) return Integer.MAX_VALUE;
        long largestSafeLongArray = Math.max(1L,
            (regionBytes / 2L - 64L) / Long.BYTES);
        // A light update is parallel work. Keep each copy-on-write slice to one
        // worker's share of a collector region so simultaneous copies do not
        // converge on one region-sized allocation stream.
        largestSafeLongArray = Math.max(2L, largestSafeLongArray
            / Runtime.getRuntime().availableProcessors());
        int tableSlots = Integer.highestOneBit((int) Math.min(
            largestSafeLongArray, 1L << 30));
        return Math.min(tableSlots - 1, (int) Math.ceil(tableSlots * LOAD_FACTOR));
    }

    private static long collectorRegionBytes() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(
                HotSpotDiagnosticMXBean.class);
            long g1Region = Long.parseLong(
                bean.getVMOption("G1HeapRegionSize").getValue());
            if (g1Region > 0L) return g1Region;
        } catch (RuntimeException unavailable) {
            // The selected collector may not expose a region-size VM option.
        }
        if (isShenandoah()) return shenandoahRegionBytes();
        return -1L;
    }

    private static boolean isShenandoah() {
        for (GarbageCollectorMXBean collector
            : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (collector.getName().contains("Shenandoah")) return true;
        }
        return false;
    }

    private static long shenandoahRegionBytes() {
        // HotSpot Shenandoah chooses a power-of-two region size targeting 2048
        // heap regions, bounded by its 256 KiB and 32 MiB region limits.
        long target = Math.max(256L << 10,
            Runtime.getRuntime().maxMemory() >>> 11);
        long region = Long.highestOneBit(target);
        if (region < target) region <<= 1;
        return Math.min(32L << 20, region);
    }
}
