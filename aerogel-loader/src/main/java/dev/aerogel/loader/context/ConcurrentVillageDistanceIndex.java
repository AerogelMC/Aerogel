package dev.aerogel.loader.context;

import net.minecraft.core.SectionPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/** Exact, concurrently readable village-center section index grouped by x/z column. */
public final class ConcurrentVillageDistanceIndex {
    private final ConcurrentHashMap<Long, ConcurrentSkipListSet<Integer>> byColumn =
        new ConcurrentHashMap<>();

    public void publish(long sectionKey, boolean center) {
        int x = SectionPos.x(sectionKey);
        int y = SectionPos.y(sectionKey);
        int z = SectionPos.z(sectionKey);
        byColumn.compute(columnKey(x, z), (ignored, sections) -> {
            if (center) {
                if (sections == null) sections = new ConcurrentSkipListSet<>();
                sections.add(y);
                return sections;
            }
            if (sections == null) return null;
            sections.remove(y);
            return sections.isEmpty() ? null : sections;
        });
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
                ConcurrentSkipListSet<Integer> sections = byColumn.get(columnKey(x, z));
                if (sections == null) continue;
                for (int y : sections.subSet(
                    originY - maximum, true, originY + maximum, true)) {
                    closest = Math.min(closest,
                        Math.max(horizontalDistance, Math.abs(y - originY)));
                    if (closest == 0) return 0;
                }
            }
        }
        return closest;
    }

    private static long columnKey(int x, int z) {
        return (x & 0xffffffffL) | ((long) z << 32);
    }
}
