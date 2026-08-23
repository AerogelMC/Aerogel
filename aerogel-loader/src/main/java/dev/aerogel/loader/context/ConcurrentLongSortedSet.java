package dev.aerogel.loader.context;

import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongConsumer;

/** Lock-free persistent primitive AVL set used by the entity-section index. */
public final class ConcurrentLongSortedSet extends LongAVLTreeSet {
    private final AtomicReference<Node> root;
    private final boolean hasLower;
    private final long lower;
    private final boolean hasUpper;
    private final long upper;

    public ConcurrentLongSortedSet() {
        this(new AtomicReference<>(), false, 0L, false, 0L);
    }

    private ConcurrentLongSortedSet(
        AtomicReference<Node> root, boolean hasLower, long lower, boolean hasUpper, long upper
    ) {
        this.root = root;
        this.hasLower = hasLower;
        this.lower = lower;
        this.hasUpper = hasUpper;
        this.upper = upper;
    }

    @Override public boolean add(long value) {
        if (!inRange(value)) throw new IllegalArgumentException("Value outside sorted-set view");
        for (;;) {
            Node current = root.get(), next = insert(current, value);
            if (next == current) return false;
            if (root.compareAndSet(current, next)) return true;
        }
    }

    @Override public boolean remove(long value) {
        if (!inRange(value)) return false;
        for (;;) {
            Node current = root.get(), next = delete(current, value);
            if (next == current) return false;
            if (root.compareAndSet(current, next)) return true;
        }
    }

    @Override public boolean contains(long value) {
        if (!inRange(value)) return false;
        Node node = root.get();
        while (node != null) {
            if (value == node.value) return true;
            node = value < node.value ? node.left : node.right;
        }
        return false;
    }

    @Override public boolean isEmpty() { return size() == 0; }

    @Override public int size() {
        Node snapshot = root.get();
        int from = hasLower ? countLessThan(snapshot, lower) : 0;
        int to = hasUpper ? countLessThan(snapshot, upper) : size(snapshot);
        return to - from;
    }

    @Override public void forEach(LongConsumer action) {
        LongBidirectionalIterator iterator = iterator();
        while (iterator.hasNext()) action.accept(iterator.nextLong());
    }

    @Override public LongSortedSet subSet(long from, long to) {
        if (from > to || !withinViewBoundary(from, true) || !withinViewBoundary(to, false)) {
            throw new IllegalArgumentException("Range outside sorted-set view");
        }
        return new ConcurrentLongSortedSet(root, true, from, true, to);
    }

    @Override public LongBidirectionalIterator iterator() {
        return new SnapshotIterator(root.get(), hasLower, lower, hasUpper, upper);
    }

    private boolean inRange(long value) {
        return (!hasLower || value >= lower) && (!hasUpper || value < upper);
    }

    private boolean withinViewBoundary(long value, boolean lowerBoundary) {
        if (hasLower && value < lower) return false;
        if (!hasUpper) return true;
        return lowerBoundary ? value < upper : value <= upper;
    }

    private static Node insert(Node node, long value) {
        if (node == null) return new Node(value, null, null);
        if (value == node.value) return node;
        if (value < node.value) {
            Node child = insert(node.left, value);
            return child == node.left ? node : balance(new Node(node.value, child, node.right));
        }
        Node child = insert(node.right, value);
        return child == node.right ? node : balance(new Node(node.value, node.left, child));
    }

    private static Node delete(Node node, long value) {
        if (node == null) return null;
        if (value < node.value) {
            Node child = delete(node.left, value);
            return child == node.left ? node : balance(new Node(node.value, child, node.right));
        }
        if (value > node.value) {
            Node child = delete(node.right, value);
            return child == node.right ? node : balance(new Node(node.value, node.left, child));
        }
        if (node.left == null) return node.right;
        if (node.right == null) return node.left;
        Node successor = node.right;
        while (successor.left != null) successor = successor.left;
        return balance(new Node(successor.value, node.left, delete(node.right, successor.value)));
    }

    private static Node balance(Node node) {
        int factor = height(node.left) - height(node.right);
        if (factor > 1) {
            Node left = node.left;
            if (height(left.left) < height(left.right)) left = rotateLeft(left);
            return rotateRight(new Node(node.value, left, node.right));
        }
        if (factor < -1) {
            Node right = node.right;
            if (height(right.right) < height(right.left)) right = rotateRight(right);
            return rotateLeft(new Node(node.value, node.left, right));
        }
        return node;
    }

    private static Node rotateLeft(Node node) {
        Node pivot = node.right;
        return new Node(pivot.value, new Node(node.value, node.left, pivot.left), pivot.right);
    }

    private static Node rotateRight(Node node) {
        Node pivot = node.left;
        return new Node(pivot.value, pivot.left, new Node(node.value, pivot.right, node.right));
    }

    private static int countLessThan(Node node, long value) {
        int count = 0;
        while (node != null) {
            if (value <= node.value) node = node.left;
            else { count += 1 + size(node.left); node = node.right; }
        }
        return count;
    }

    private static int height(Node node) { return node == null ? 0 : node.height; }
    private static int size(Node node) { return node == null ? 0 : node.size; }

    private static final class Node {
        final long value;
        final Node left, right;
        final int height, size;
        Node(long value, Node left, Node right) {
            this.value = value;
            this.left = left;
            this.right = right;
            this.height = 1 + Math.max(height(left), height(right));
            this.size = 1 + size(left) + size(right);
        }
    }

    private static final class SnapshotIterator implements LongBidirectionalIterator {
        private final Node root;
        private final boolean hasUpper;
        private final long upper;
        private final Node[] forward;
        private int forwardSize;
        private boolean hasPrevious;
        private long previous;

        SnapshotIterator(Node root, boolean hasLower, long lower, boolean hasUpper, long upper) {
            this.root = root;
            this.hasUpper = hasUpper;
            this.upper = upper;
            this.forward = new Node[height(root) + 1];
            if (hasLower) pushCeiling(root, lower);
            else pushLeft(root);
        }

        @Override public boolean hasNext() {
            return forwardSize != 0 && (!hasUpper || forward[forwardSize - 1].value < upper);
        }
        @Override public long nextLong() {
            if (!hasNext()) throw new NoSuchElementException();
            Node node = forward[--forwardSize];
            pushLeft(node.right);
            long result = node.value;
            previous = result;
            hasPrevious = true;
            return result;
        }
        @Override public Long next() { return nextLong(); }
        @Override public boolean hasPrevious() { return hasPrevious; }
        @Override public long previousLong() {
            if (!hasPrevious) throw new NoSuchElementException();
            long result = previous;
            forwardSize = 0;
            pushCeiling(root, result);
            Node predecessor = lower(root, result);
            hasPrevious = predecessor != null;
            if (hasPrevious) previous = predecessor.value;
            return result;
        }

        private void pushLeft(Node node) {
            while (node != null) {
                forward[forwardSize++] = node;
                node = node.left;
            }
        }

        private void pushCeiling(Node node, long value) {
            while (node != null) {
                if (node.value >= value) {
                    forward[forwardSize++] = node;
                    node = node.left;
                }
                else node = node.right;
            }
        }
        private static Node lower(Node node, long value) {
            Node candidate = null;
            while (node != null) {
                if (node.value < value) { candidate = node; node = node.right; }
                else node = node.left;
            }
            return candidate;
        }
    }
}
