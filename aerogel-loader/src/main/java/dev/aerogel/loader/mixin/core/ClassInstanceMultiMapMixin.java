package dev.aerogel.loader.mixin.core;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;

@Mixin(targets = "net.minecraft.util.ClassInstanceMultiMap")
abstract class ClassInstanceMultiMapMixin<T> {
    @Shadow @Final @Mutable private Map<Class<?>, List<T>> byClass;
    @Shadow @Final @Mutable private List<T> allInstances;
    @Shadow @Final private Class<T> baseClass;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void aerogel$concurrentClassCache(Class<T> baseClass, CallbackInfo callbackInfo) {
        List<T> all = new CopyOnWriteArrayList<>(this.allInstances);
        ConcurrentClassMap<T> classes = new ConcurrentClassMap<>(all);
        classes.put(this.baseClass, all);
        this.allInstances = all;
        this.byClass = classes;
    }

    @SuppressWarnings("serial")
    private static final class ConcurrentClassMap<E>
        extends ConcurrentHashMap<Class<?>, List<E>> {
        private final List<E> all;

        private ConcurrentClassMap(List<E> all) {
            this.all = all;
        }

        @Override
        public List<E> computeIfAbsent(
            Class<?> key,
            Function<? super Class<?>, ? extends List<E>> mappingFunction
        ) {
            return super.computeIfAbsent(key, type -> new FilteredView<>(all, type));
        }
    }

    private static final class FilteredView<E> extends AbstractList<E> {
        private final List<E> all;
        private final Class<?> type;

        private FilteredView(List<E> all, Class<?> type) {
            this.all = all;
            this.type = type;
        }

        @Override
        public E get(int index) {
            int current = 0;
            for (E value : all) {
                if (type.isInstance(value) && current++ == index) return value;
            }
            throw new IndexOutOfBoundsException(index);
        }

        @Override
        public int size() {
            int size = 0;
            for (E value : all) if (type.isInstance(value)) size++;
            return size;
        }

        @Override
        public Iterator<E> iterator() {
            Iterator<E> source = all.iterator();
            return new Iterator<>() {
                private E next;
                private boolean ready;

                @Override
                public boolean hasNext() {
                    while (!ready && source.hasNext()) {
                        E candidate = source.next();
                        if (type.isInstance(candidate)) {
                            next = candidate;
                            ready = true;
                        }
                    }
                    return ready;
                }

                @Override
                public E next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    E value = next;
                    next = null;
                    ready = false;
                    return value;
                }
            };
        }

        @Override public boolean add(E value) { return false; }
        @Override public boolean remove(Object value) { return false; }
    }
}
