package dev.aerogel.loader.api;

import dev.aerogel.api.Registration;
import dev.aerogel.api.dialog.DialogCallback;
import dev.aerogel.api.dialog.DialogResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DialogCallbackRegistry {
    private static final Map<String, Entry> CALLBACKS = new ConcurrentHashMap<>();

    private DialogCallbackRegistry() {}

    static Registration register(String id, String action, DialogCallback callback, Logger logger) {
        Entry entry = new Entry(action, callback, logger);
        if (CALLBACKS.putIfAbsent(id, entry) != null) throw new IllegalStateException("Duplicate dialog callback: " + id);
        return new Registration() {
            private final AtomicBoolean active = new AtomicBoolean(true);
            @Override public boolean active() { return active.get(); }
            @Override public void close() {
                if (active.compareAndSet(true, false)) CALLBACKS.remove(id, entry);
            }
        };
    }

    public static boolean dispatch(String id, Object player, Object payload) {
        Entry entry = CALLBACKS.get(id);
        if (entry == null) return false;
        try { entry.callback.accept(new DialogResult(
            dev.aerogel.loader.event.EventHooks.cast(player), entry.action,
            dev.aerogel.loader.event.EventHooks.cast(payload))); }
        catch (Exception exception) { entry.logger.log(Level.SEVERE, "Dialog callback failed: " + id, exception); }
        return true;
    }

    private record Entry(String action, DialogCallback callback, Logger logger) {}
}
