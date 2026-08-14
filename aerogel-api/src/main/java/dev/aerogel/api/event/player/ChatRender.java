package dev.aerogel.api.event.player;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * An immutable signed-chat layout composed of independently styled components around the message.
 * Keeping {@link #message()} unchanged preserves the signed body exactly.
 */
public final class ChatRender {
    private final List<Component> prefix;
    private final Component message;
    private final List<Component> suffix;

    private ChatRender(List<Component> prefix, Component message, List<Component> suffix) {
        this.prefix = List.copyOf(prefix);
        this.message = Objects.requireNonNull(message, "message");
        this.suffix = List.copyOf(suffix);
    }

    public static Builder builder(Component message) {
        return new Builder(message);
    }

    public List<Component> prefix() { return prefix; }
    public Component message() { return message; }
    public List<Component> suffix() { return suffix; }

    public static final class Builder {
        private final Component message;
        private final List<Component> prefix = new ArrayList<>();
        private final List<Component> suffix = new ArrayList<>();

        private Builder(Component message) {
            this.message = Objects.requireNonNull(message, "message");
        }

        public Builder prefix(Component... components) {
            append(prefix, components);
            return this;
        }

        public Builder suffix(Component... components) {
            append(suffix, components);
            return this;
        }

        public ChatRender build() {
            return new ChatRender(prefix, message, suffix);
        }

        private static void append(List<Component> target, Component[] components) {
            Objects.requireNonNull(components, "components");
            Arrays.stream(components)
                .map(component -> Objects.requireNonNull(component, "component"))
                .forEach(target::add);
        }
    }
}
