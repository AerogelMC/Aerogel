package net.minecraft.network.chat;

import java.util.Optional;

public interface Component {
    static Component literal(String value) { return null; }
    static Component translatable(String key, Object... arguments) { return null; }
    String getString();
    <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style);
}
