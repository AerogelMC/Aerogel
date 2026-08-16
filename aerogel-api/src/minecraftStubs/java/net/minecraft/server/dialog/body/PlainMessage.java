package net.minecraft.server.dialog.body;

import net.minecraft.network.chat.Component;

public record PlainMessage(Component contents, int width) implements DialogBody { }
