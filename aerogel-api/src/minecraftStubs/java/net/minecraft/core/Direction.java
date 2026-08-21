package net.minecraft.core;

import java.util.Iterator;
import java.util.List;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public enum Direction {
    DOWN, UP, NORTH, SOUTH, WEST, EAST;

    public enum Plane implements Iterable<Direction> {
        HORIZONTAL;

        @Override
        public Iterator<Direction> iterator() {
            return List.of(NORTH, SOUTH, WEST, EAST).iterator();
        }
    }
}
