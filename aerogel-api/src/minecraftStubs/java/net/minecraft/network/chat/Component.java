package net.minecraft.network.chat;

import java.util.Optional;

public interface Component {
    static MutableComponent empty() { return null; }
    static MutableComponent literal(String value) { return null; }
    static MutableComponent translatable(String key, Object... arguments) { return null; }
    static MutableComponent translatableWithFallback(String key, String fallback, Object... arguments) { return null; }
    String getString();
    <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style);
}
