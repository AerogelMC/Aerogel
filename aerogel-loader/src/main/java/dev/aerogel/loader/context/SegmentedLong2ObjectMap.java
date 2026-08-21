package dev.aerogel.loader.context;

import com.sun.management.HotSpotDiagnosticMXBean;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.lang.management.ManagementFactory;

/**
 * A primitive map whose cloned backing arrays remain below G1's humongous-object
 * boundary. Light-engine snapshots still copy exactly the same key/value state,
 * but do so as independently collectible segments instead of multi-megabyte arrays.
 */
public final class SegmentedLong2ObjectMap<V> extends Long2ObjectOpenHashMap<V> {
    private static final float LOAD_FACTOR = 0.75F;
    private static final int MAX_ENTRIES_PER_SEGMENT = maxEntriesPerSegment();

    private Long2ObjectOpenHashMap<V>[] segments;
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
        Long2ObjectOpenHashMap<V>[] segments, int entryCount, V defaultValue
    ) {
        super(0);
        this.segments = segments;
        this.entryCount = entryCount;
        super.defaultReturnValue(defaultValue);
        for (Long2ObjectOpenHashMap<V> segment : segments) {
            segment.defaultReturnValue(defaultValue);
        }
    }

    public static <V> Long2ObjectOpenHashMap<V> wrap(Long2ObjectOpenHashMap<V> source) {
        return source instanceof SegmentedLong2ObjectMap<V> ? source
            : new SegmentedLong2ObjectMap<>(source);
    }

    @Override
    public V get(long key) {
        return segment(key).get(key);
    }

    @Override
    public V getOrDefault(long key, V defaultValue) {
        return segment(key).getOrDefault(key, defaultValue);
    }

    @Override
    public boolean containsKey(long key) {
        return segment(key).containsKey(key);
    }

    @Override
    public V put(long key, V value) {
        Long2ObjectOpenHashMap<V> segment = segment(key);
        boolean present = segment.containsKey(key);
        if (!present && segment.size() >= MAX_ENTRIES_PER_SEGMENT) {
            split();
            segment = segment(key);
        }
        V previous = segment.put(key, value);
        if (!present) entryCount++;
        return previous;
    }

    @Override
    public V remove(long key) {
        Long2ObjectOpenHashMap<V> segment = segment(key);
        if (!segment.containsKey(key)) return defaultReturnValue();
        V previous = segment.remove(key);
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
        for (Long2ObjectOpenHashMap<V> segment : segments) {
            segment.defaultReturnValue(value);
        }
    }

    @Override
    public SegmentedLong2ObjectMap<V> clone() {
        Long2ObjectOpenHashMap<V>[] copies = newSegments(segments.length);
        for (int index = 0; index < segments.length; index++) {
            copies[index] = segments[index].clone();
        }
        return new SegmentedLong2ObjectMap<>(copies, entryCount, defaultReturnValue());
    }

    int segmentCount() {
        return segments.length;
    }

    static int maximumEntriesPerSegment() {
        return MAX_ENTRIES_PER_SEGMENT;
    }

    private Long2ObjectOpenHashMap<V> segment(long key) {
        long mixed = key;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        mixed ^= mixed >>> 31;
        return segments[(int) mixed & (segments.length - 1)];
    }

    private void split() {
        Long2ObjectOpenHashMap<V>[] previous = segments;
        segments = newSegments(Math.multiplyExact(previous.length, 2));
        entryCount = 0;
        for (Long2ObjectOpenHashMap<V> segment : previous) {
            LongIterator keys = segment.keySet().iterator();
            while (keys.hasNext()) {
                long key = keys.nextLong();
                put(key, segment.get(key));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Long2ObjectOpenHashMap<V>[] newSegments(int count) {
        Long2ObjectOpenHashMap<V>[] result =
            (Long2ObjectOpenHashMap<V>[]) new Long2ObjectOpenHashMap<?>[count];
        V defaultValue = defaultReturnValue();
        for (int index = 0; index < count; index++) {
            result[index] = new Long2ObjectOpenHashMap<>(0);
            result[index].defaultReturnValue(defaultValue);
        }
        return result;
    }

    private static int maxEntriesPerSegment() {
        long regionBytes = g1RegionBytes();
        if (regionBytes <= 0L) return Integer.MAX_VALUE;
        long largestSafeLongArray = Math.max(1L,
            (regionBytes / 2L - 64L) / Long.BYTES);
        int tableSlots = Integer.highestOneBit((int) Math.min(
            largestSafeLongArray, 1L << 30));
        return Math.min(tableSlots - 1, (int) Math.ceil(tableSlots * LOAD_FACTOR));
    }

    private static long g1RegionBytes() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(
                HotSpotDiagnosticMXBean.class);
            return Long.parseLong(bean.getVMOption("G1HeapRegionSize").getValue());
        } catch (RuntimeException unavailable) {
            return -1L;
        }
    }
}
