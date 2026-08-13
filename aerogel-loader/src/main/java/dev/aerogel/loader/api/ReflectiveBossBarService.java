package dev.aerogel.loader.api;

import dev.aerogel.api.bossbar.BossBar;
import dev.aerogel.api.bossbar.BossBarColor;
import dev.aerogel.api.bossbar.BossBarOverlay;
import dev.aerogel.api.bossbar.BossBarService;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class ReflectiveBossBarService implements BossBarService {
    private final PluginApiScope scope;
    ReflectiveBossBarService(PluginApiScope scope) { this.scope = scope; }

    @Override public BossBar create(Component name) {
        return create(name, BossBarColor.PURPLE, BossBarOverlay.PROGRESS);
    }

    @Override public BossBar create(Component name, BossBarColor color, BossBarOverlay overlay) {
        ClassLoader loader = scope.loader();
        Object vanillaColor = Reflect.staticField(Reflect.type(loader,
            "net.minecraft.world.BossEvent$BossBarColor"), color.name());
        Object vanillaOverlay = Reflect.staticField(Reflect.type(loader,
            "net.minecraft.world.BossEvent$BossBarOverlay"), overlay.name());
        Object handle = Reflect.construct(Reflect.type(loader, "net.minecraft.server.level.ServerBossEvent"),
            UUID.randomUUID(), name, vanillaColor, vanillaOverlay);
        return scope.own(new Bar(handle));
    }

    private final class Bar implements BossBar {
        private final Object handle;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private Bar(Object handle) { this.handle = handle; }
        @Override public ServerBossEvent vanilla() { return (ServerBossEvent) handle; }
        @Override public BossBar name(Component value) { call("setName", value); return this; }
        @Override public BossBar progress(float value) {
            if (value < 0 || value > 1 || Float.isNaN(value)) throw new IllegalArgumentException("Boss bar progress must be 0..1");
            call("setProgress", value); return this;
        }
        @Override public BossBar color(BossBarColor value) {
            call("setColor", Reflect.staticField(Reflect.type(scope.loader(),
                "net.minecraft.world.BossEvent$BossBarColor"), value.name())); return this;
        }
        @Override public BossBar overlay(BossBarOverlay value) {
            call("setOverlay", Reflect.staticField(Reflect.type(scope.loader(),
                "net.minecraft.world.BossEvent$BossBarOverlay"), value.name())); return this;
        }
        @Override public BossBar darkenScreen(boolean value) { call("setDarkenScreen", value); return this; }
        @Override public BossBar playMusic(boolean value) { call("setPlayBossMusic", value); return this; }
        @Override public BossBar worldFog(boolean value) { call("setCreateWorldFog", value); return this; }
        @Override public BossBar visible(boolean value) { call("setVisible", value); return this; }
        @Override public BossBar add(ServerPlayer player) { call("addPlayer", player); return this; }
        @Override public BossBar remove(ServerPlayer player) { call("removePlayer", player); return this; }
        @Override public BossBar clearViewers() { call("removeAllPlayers"); return this; }
        @Override @SuppressWarnings("unchecked") public Collection<ServerPlayer> viewers() {
            check(); return java.util.List.copyOf((Collection<ServerPlayer>) Reflect.invoke(handle, "getPlayers"));
        }
        private void call(String method, Object... args) { check(); Reflect.invoke(handle, method, args); }
        private void check() { if (!active()) throw new IllegalStateException("Boss bar is closed"); }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            Reflect.invoke(handle, "removeAllPlayers");
            Reflect.invoke(handle, "setVisible", false);
        }
    }
}
