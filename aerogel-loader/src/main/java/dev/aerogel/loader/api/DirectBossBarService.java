package dev.aerogel.loader.api;

import dev.aerogel.api.bossbar.BossBar;
import dev.aerogel.api.bossbar.BossBarColor;
import dev.aerogel.api.bossbar.BossBarOverlay;
import dev.aerogel.api.bossbar.BossBarService;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

final class DirectBossBarService implements BossBarService {
    private final PluginApiScope scope;
    DirectBossBarService(PluginApiScope scope) { this.scope = scope; }

    @Override public BossBar create(Component name) {
        return create(name, BossBarColor.PURPLE, BossBarOverlay.PROGRESS);
    }

    @Override public BossBar create(Component name, BossBarColor color, BossBarOverlay overlay) {
        ServerBossEvent handle = new ServerBossEvent(
            UUID.randomUUID(), name, BossEvent.BossBarColor.valueOf(color.name()),
            BossEvent.BossBarOverlay.valueOf(overlay.name()));
        return scope.own(new Bar(handle));
    }

    private final class Bar implements BossBar {
        private final ServerBossEvent handle;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private Bar(ServerBossEvent handle) { this.handle = handle; }
        @Override public ServerBossEvent vanilla() { return handle; }
        @Override public BossBar name(Component value) { check(); handle.setName(value); return this; }
        @Override public BossBar progress(float value) {
            if (value < 0 || value > 1 || Float.isNaN(value)) throw new IllegalArgumentException("Boss bar progress must be 0..1");
            check(); handle.setProgress(value); return this;
        }
        @Override public BossBar color(BossBarColor value) {
            check(); handle.setColor(BossEvent.BossBarColor.valueOf(value.name())); return this;
        }
        @Override public BossBar overlay(BossBarOverlay value) {
            check(); handle.setOverlay(BossEvent.BossBarOverlay.valueOf(value.name())); return this;
        }
        @Override public BossBar darkenScreen(boolean value) { check(); handle.setDarkenScreen(value); return this; }
        @Override public BossBar playMusic(boolean value) { check(); handle.setPlayBossMusic(value); return this; }
        @Override public BossBar worldFog(boolean value) { check(); handle.setCreateWorldFog(value); return this; }
        @Override public BossBar visible(boolean value) { check(); handle.setVisible(value); return this; }
        @Override public BossBar add(ServerPlayer player) { check(); handle.addPlayer(player); return this; }
        @Override public BossBar remove(ServerPlayer player) { check(); handle.removePlayer(player); return this; }
        @Override public BossBar clearViewers() { check(); handle.removeAllPlayers(); return this; }
        @Override public Collection<ServerPlayer> viewers() {
            check(); return java.util.List.copyOf(handle.getPlayers());
        }
        private void check() { if (!active()) throw new IllegalStateException("Boss bar is closed"); }
        @Override public boolean active() { return active.get(); }
        @Override public void close() {
            if (!active.compareAndSet(true, false)) return;
            handle.removeAllPlayers();
            handle.setVisible(false);
        }
    }
}
