package net.minecraft.network.protocol;

public interface Packet<T> {
    default boolean isTerminal() { return false; }
    default void handle(T listener) { }
}
