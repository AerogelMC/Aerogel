package net.minecraft.network.chat;

import java.util.Optional;

public interface FormattedText {
    @FunctionalInterface
    interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String text);
    }
}
