package dev.aerogel.api.dialog;

import java.util.List;
import net.minecraft.network.chat.Component;

public interface DialogService {
    Dialog notice(Component title, List<Component> body, Component closeButton);
    Dialog confirmation(Component title, List<Component> body, Component yes, Component no,
                        DialogCallback onYes, DialogCallback onNo);
    Dialog nativeDialog(net.minecraft.server.dialog.Dialog vanillaDialog);
}
