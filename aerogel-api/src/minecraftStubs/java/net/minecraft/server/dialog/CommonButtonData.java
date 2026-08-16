package net.minecraft.server.dialog;

import java.util.Optional;
import net.minecraft.network.chat.Component;

public record CommonButtonData(Component label, Optional<Component> tooltip, int width) { }
