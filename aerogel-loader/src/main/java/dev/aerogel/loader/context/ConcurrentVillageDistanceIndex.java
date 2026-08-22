package dev.aerogel.loader.context;

import net.minecraft.core.SectionPos;

import java.util.Arrays;

/** Exact, concurrently readable village-center section index grouped by x/z column. */
public final class ConcurrentVillageDistanceIndex {
    private final ConcurrentLong2ObjectMap<Sections> byColumn =
        new ConcurrentLong2ObjectMap<>();

    public void publish(long sectionKey, boolean center) {
        int x = SectionPos.x(sectionKey);
        int y = SectionPos.y(sectionKey);
        int z = SectionPos.z(sectionKey);
        long column = columnKey(x, z);
        if (center) {
            byColumn.computeIfAbsent(column, ignored -> new Sections()).add(y);
            return;
        }
        Sections sections = byColumn.get(column);
        if (sections != null) sections.remove(y);
    }

    public int distance(long originKey, int maximum) {
        int originX = SectionPos.x(originKey);
        int originY = SectionPos.y(originKey);
        int originZ = SectionPos.z(originKey);
        int closest = maximum + 1;
        for (int x = originX - maximum; x <= originX + maximum; x++) {
            int xDistance = Math.abs(x - originX);
            if (xDistance >= closest) continue;
            for (int z = originZ - maximum; z <= originZ + maximum; z++) {
                int horizontalDistance = Math.max(xDistance, Math.abs(z - originZ));
                if (horizontalDistance >= closest) continue;
                Sections sections = byColumn.get(columnKey(x, z));
                if (sections == null) continue;
                closest = sections.closestDistance(
                    originY, maximum, horizontalDistance, closest);
                if (closest == 0) return 0;
            }
        }
        return closest;
    }

    private static long columnKey(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }

    /** Copy-on-write because village-center mutations are rare and reads are hot. */
    private static final class Sections {
        private volatile int[] values = new int[0];

        private synchronized void add(int value) {
            int[] current = values;
            int index = Arrays.binarySearch(current, value);
            if (index >= 0) return;
            int insertion = -index - 1;
            int[] next = new int[current.length + 1];
            System.arraycopy(current, 0, next, 0, insertion);
            next[insertion] = value;
            System.arraycopy(current, insertion, next, insertion + 1,
                current.length - insertion);
            values = next;
        }

        private synchronized void remove(int value) {
            int[] current = values;
            int index = Arrays.binarySearch(current, value);
            if (index < 0) return;
            int[] next = new int[current.length - 1];
            System.arraycopy(current, 0, next, 0, index);
            System.arraycopy(current, index + 1, next, index,
                current.length - index - 1);
            values = next;
        }

        private int closestDistance(
            int origin, int maximum, int horizontal, int closest
        ) {
            int[] snapshot = values;
            int lower = origin - maximum;
            int upper = origin + maximum;
            int index = Arrays.binarySearch(snapshot, lower);
            if (index < 0) index = -index - 1;
            while (index < snapshot.length && snapshot[index] <= upper) {
                closest = Math.min(closest,
                    Math.max(horizontal, Math.abs(snapshot[index] - origin)));
                if (closest == 0) return 0;
                index++;
            }
            return closest;
        }
    }
}
