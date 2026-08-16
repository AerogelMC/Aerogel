package dev.aerogel.loader.api;

import dev.aerogel.api.Registration;
import dev.aerogel.api.dialog.Dialog;
import dev.aerogel.api.dialog.DialogCallback;
import dev.aerogel.api.dialog.DialogResult;
import dev.aerogel.api.dialog.DialogService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.StaticAction;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectDialogService implements DialogService {
    private final PluginApiScope scope;
    DirectDialogService(PluginApiScope scope) { this.scope = scope; }

    @Override public Dialog notice(Component title, List<Component> body, Component closeButton) {
        return scope.own(new DialogImpl(
            new NoticeDialog(common(title, body), button(closeButton, null)), List.of()));
    }

    @Override public Dialog confirmation(Component title, List<Component> body, Component yes, Component no,
                                         DialogCallback onYes, DialogCallback onNo) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String yesAction = scope.pluginId() + "/dialog/yes_" + token;
        String noAction = scope.pluginId() + "/dialog/no_" + token;
        Registration yesRegistration = callback(yesAction, "yes", onYes);
        Registration noRegistration = callback(noAction, "no", onNo);
        return scope.own(new DialogImpl(new ConfirmationDialog(
            common(title, body), button(yes, yesAction), button(no, noAction)),
            List.of(yesRegistration, noRegistration)));
    }

    @Override public Dialog nativeDialog(net.minecraft.server.dialog.Dialog vanillaDialog) {
        return scope.own(new DialogImpl(java.util.Objects.requireNonNull(vanillaDialog, "vanillaDialog"), List.of()));
    }

    private Registration callback(String id, String action, DialogCallback callback) {
        return DialogCallbackRegistry.register("aerogel:" + id, action, callback, scope.logger());
    }

    private CommonDialogData common(Component title, List<Component> body) {
        List<DialogBody> bodies = new ArrayList<>();
        for (Component line : body) bodies.add(new PlainMessage(line, 310));
        return new CommonDialogData(
            title, Optional.empty(), true, false, DialogAction.CLOSE,
            List.copyOf(bodies), List.of());
    }

    private ActionButton button(Component text, String actionId) {
        CommonButtonData data = new CommonButtonData(text, Optional.empty(), 150);
        Optional<Action> action = Optional.empty();
        if (actionId != null) {
            Identifier identifier = Identifier.fromNamespaceAndPath("aerogel", actionId);
            ClickEvent click = new ClickEvent.Custom(identifier, Optional.empty());
            action = Optional.of(new StaticAction(click));
        }
        return new ActionButton(data, action);
    }

    private final class DialogImpl implements Dialog {
        private final net.minecraft.server.dialog.Dialog handle;
        private final List<Registration> callbacks;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private DialogImpl(net.minecraft.server.dialog.Dialog handle, List<Registration> callbacks) {
            this.handle = handle; this.callbacks = callbacks;
        }
        @Override public net.minecraft.server.dialog.Dialog vanilla() {
            return handle;
        }
        @Override public void show(ServerPlayer player) {
            if (!active()) throw new IllegalStateException("Dialog is closed");
            player.openDialog(Holder.direct(handle));
        }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            for (Registration callback : callbacks) callback.close();
        }
    }
}
