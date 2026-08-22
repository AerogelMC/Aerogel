package dev.aerogel.loader.context;

import dev.aerogel.loader.internal.PathNavigationBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConcurrentNavigationSetTest {
    @Test
    void indexesOnlyChunksIntersectingVanillasRemainingPathSphere() {
        FakeMob mob = new FakeMob(0.0D, 64.0D, 0.0D);
        mob.navigation.path = new FakePath(8, 3, node(20, 64, 0));
        ConcurrentNavigationSet set = new ConcurrentNavigationSet();

        assertTrue(set.add(mob));
        Iterator<Mob> near = set.candidates(new BlockPos(9, 64, 0));
        assertTrue(near.hasNext());
        assertSame(mob, near.next());
        assertFalse(set.candidates(new BlockPos(64, 64, 0)).hasNext());
    }

    @Test
    void transitionFallbackCannotMissConcurrentPathMovement() {
        FakeMob mob = new FakeMob(0.0D, 64.0D, 0.0D);
        mob.navigation.path = new FakePath(2, 0, node(0, 64, 0));
        ConcurrentNavigationSet set = new ConcurrentNavigationSet();
        set.add(mob);

        set.beginUpdate(mob);
        mob.x = 160.0D;
        mob.navigation.path = new FakePath(2, 0, node(160, 64, 0));
        assertTrue(set.candidates(new BlockPos(160, 64, 0)).hasNext());
        set.finishUpdate(mob);

        assertFalse(set.candidates(new BlockPos(0, 64, 0)).hasNext());
        assertTrue(set.candidates(new BlockPos(160, 64, 0)).hasNext());
    }

    private static Node node(int x, int y, int z) {
        Node node = new Node();
        node.x = x;
        node.y = y;
        node.z = z;
        return node;
    }

    private static final class FakeMob extends Mob {
        private double x;
        private final FakeNavigation navigation = new FakeNavigation();

        private FakeMob(double x, double y, double z) {
            this.x = x;
        }

        @Override public double getX() { return x; }
        @Override public double getY() { return 64.0D; }
        @Override public double getZ() { return 0.0D; }
        @Override public PathNavigation getNavigation() { return navigation; }
    }

    private static final class FakeNavigation extends PathNavigation
        implements PathNavigationBridge {
        private Path path;
        @Override public boolean aerogel$hasDelayedRecomputation() { return false; }
        @Override public Path aerogel$path() { return path; }
    }

    private static final class FakePath extends Path {
        private final int count;
        private final int next;
        private final Node end;

        private FakePath(int count, int next, Node end) {
            this.count = count;
            this.next = next;
            this.end = end;
        }

        @Override public boolean isDone() { return false; }
        @Override public int getNodeCount() { return count; }
        @Override public int getNextNodeIndex() { return next; }
        @Override public Node getEndNode() { return end; }
    }
}
