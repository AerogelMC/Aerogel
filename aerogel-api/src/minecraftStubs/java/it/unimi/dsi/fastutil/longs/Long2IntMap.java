package it.unimi.dsi.fastutil.longs;

import it.unimi.dsi.fastutil.objects.ObjectSet;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public interface Long2IntMap {
    interface Entry {
        long getLongKey();
        int getIntValue();
    }
    ObjectSet<Entry> long2IntEntrySet();
}
