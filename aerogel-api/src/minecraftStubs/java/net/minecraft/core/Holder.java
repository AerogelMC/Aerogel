package net.minecraft.core;
public interface Holder<T> {
    static <T> Holder<T> direct(T value) { return null; }

    abstract class Reference<T> implements Holder<T> {
    }
}
