package net.minecraft.server.dialog.action;

import net.minecraft.network.chat.ClickEvent;

public record StaticAction(ClickEvent value) implements Action { }
