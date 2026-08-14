package net.minecraft.network.chat;

import java.util.List;

public final class ChatTypeDecoration {
    public ChatTypeDecoration(String translationKey, List<Parameter> parameters, Style style) { }

    public enum Parameter {
        SENDER,
        TARGET,
        CONTENT
    }
}
