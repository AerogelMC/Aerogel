package net.minecraft.server.dialog;

import java.util.Optional;
import net.minecraft.server.dialog.action.Action;

public record ActionButton(CommonButtonData button, Optional<Action> action) { }
