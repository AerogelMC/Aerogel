package it.unimi.dsi.fastutil.longs;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public final class Long2ObjectMaps {
    private Long2ObjectMaps() {
    }

    public static <V> Iterable<Long2ObjectMap.Entry<V>> fastIterable(
        Long2ObjectMap<V> map
    ) {
        return map.long2ObjectEntrySet();
    }
}
