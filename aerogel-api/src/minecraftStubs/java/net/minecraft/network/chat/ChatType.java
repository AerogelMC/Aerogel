package net.minecraft.network.chat;

import net.minecraft.core.Holder;
import java.util.Optional;

public final class ChatType {
    public ChatType(ChatTypeDecoration chat, ChatTypeDecoration narration) { }

    public static final class Bound {
        public Bound(Holder<ChatType> chatType, Component name, Optional<Component> targetName) { }
    }
}
