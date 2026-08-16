package net.minecraft.server.dialog;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.body.DialogBody;

public record CommonDialogData(
    Component title, Optional<Component> externalTitle, boolean canCloseWithEscape,
    boolean pause, DialogAction afterAction, List<DialogBody> body, List<Input> inputs
) { }
