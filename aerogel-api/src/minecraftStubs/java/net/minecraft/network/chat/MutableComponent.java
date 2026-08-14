package net.minecraft.network.chat;

/** Compile-time shape stub matching vanilla's concrete final class. */
public final class MutableComponent implements Component {
    public MutableComponent append(Component component) { return this; }
    public MutableComponent append(String text) { return this; }
    public MutableComponent withStyle(Style style) { return this; }

    @Override public String getString() { return ""; }
    @Override public <T> java.util.Optional<T> visit(
        FormattedText.StyledContentConsumer<T> consumer, Style style
    ) { return java.util.Optional.empty(); }
}
