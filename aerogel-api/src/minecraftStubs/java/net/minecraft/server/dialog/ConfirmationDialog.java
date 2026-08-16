package net.minecraft.server.dialog;

public record ConfirmationDialog(
    CommonDialogData common, ActionButton yesButton, ActionButton noButton
) implements Dialog { }
