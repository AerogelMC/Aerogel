package it.unimi.dsi.fastutil.longs;

import it.unimi.dsi.fastutil.objects.ObjectSet;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface Long2ByteMap {
    interface Entry {
        long getLongKey();
        byte getByteValue();
    }
    ObjectSet<Entry> long2ByteEntrySet();
}
