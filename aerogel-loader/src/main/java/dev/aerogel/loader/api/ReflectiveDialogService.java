package dev.aerogel.loader.api;

import dev.aerogel.api.Registration;
import dev.aerogel.api.dialog.Dialog;
import dev.aerogel.api.dialog.DialogCallback;
import dev.aerogel.api.dialog.DialogResult;
import dev.aerogel.api.dialog.DialogService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class ReflectiveDialogService implements DialogService {
    private final PluginApiScope scope;
    ReflectiveDialogService(PluginApiScope scope) { this.scope = scope; }

    @Override public Dialog notice(Component title, List<Component> body, Component closeButton) {
        Object data = common(title, body);
        Object button = button(closeButton, null);
        Object dialog = Reflect.construct(Reflect.type(scope.loader(), "net.minecraft.server.dialog.NoticeDialog"),
            data, button);
        return scope.own(new DialogImpl(dialog, List.of()));
    }

    @Override public Dialog confirmation(Component title, List<Component> body, Component yes, Component no,
                                         DialogCallback onYes, DialogCallback onNo) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String yesAction = scope.pluginId() + "/dialog/yes_" + token;
        String noAction = scope.pluginId() + "/dialog/no_" + token;
        Registration yesRegistration = callback(yesAction, "yes", onYes);
        Registration noRegistration = callback(noAction, "no", onNo);
        Object dialog = Reflect.construct(Reflect.type(scope.loader(), "net.minecraft.server.dialog.ConfirmationDialog"),
            common(title, body), button(yes, yesAction), button(no, noAction));
        return scope.own(new DialogImpl(dialog, List.of(yesRegistration, noRegistration)));
    }

    @Override public Dialog nativeDialog(net.minecraft.server.dialog.Dialog vanillaDialog) {
        return scope.own(new DialogImpl(java.util.Objects.requireNonNull(vanillaDialog, "vanillaDialog"), List.of()));
    }

    private Registration callback(String id, String action, DialogCallback callback) {
        return DialogCallbackRegistry.register("aerogel:" + id, action, callback, scope.logger());
    }

    private Object common(Component title, List<Component> body) {
        ClassLoader loader = scope.loader();
        List<Object> bodies = new ArrayList<>();
        Class<?> plain = Reflect.type(loader, "net.minecraft.server.dialog.body.PlainMessage");
        for (Component line : body) bodies.add(Reflect.construct(plain, line, 310));
        Object close = Reflect.staticField(Reflect.type(loader, "net.minecraft.server.dialog.DialogAction"), "CLOSE");
        return Reflect.construct(Reflect.type(loader, "net.minecraft.server.dialog.CommonDialogData"),
            title, Optional.empty(), true, false, close, List.copyOf(bodies), List.of());
    }

    private Object button(Component text, String actionId) {
        ClassLoader loader = scope.loader();
        Object data = Reflect.construct(Reflect.type(loader, "net.minecraft.server.dialog.CommonButtonData"),
            text, Optional.empty(), 150);
        Optional<Object> action = Optional.empty();
        if (actionId != null) {
            Object identifier = Reflect.invokeStatic(Reflect.type(loader, "net.minecraft.resources.Identifier"),
                "fromNamespaceAndPath", "aerogel", actionId);
            Object click = Reflect.construct(Reflect.type(loader, "net.minecraft.network.chat.ClickEvent$Custom"),
                identifier, Optional.empty());
            action = Optional.of(Reflect.construct(Reflect.type(loader,
                "net.minecraft.server.dialog.action.StaticAction"), click));
        }
        return Reflect.construct(Reflect.type(loader, "net.minecraft.server.dialog.ActionButton"), data, action);
    }

    private final class DialogImpl implements Dialog {
        private final Object handle;
        private final List<Registration> callbacks;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private DialogImpl(Object handle, List<Registration> callbacks) {
            this.handle = handle; this.callbacks = callbacks;
        }
        @Override public net.minecraft.server.dialog.Dialog vanilla() {
            return (net.minecraft.server.dialog.Dialog) handle;
        }
        @Override public void show(ServerPlayer player) {
            if (!active()) throw new IllegalStateException("Dialog is closed");
            Object holder = handle.getClass().getName().startsWith("net.minecraft.core.Holder$")
                ? handle : Reflect.invokeStatic(Reflect.type(scope.loader(), "net.minecraft.core.Holder"), "direct", handle);
            Reflect.invoke(player, "openDialog", holder);
        }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            for (Registration callback : callbacks) callback.close();
        }
    }
}
