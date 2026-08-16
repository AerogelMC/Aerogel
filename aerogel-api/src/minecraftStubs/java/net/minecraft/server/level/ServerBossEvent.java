package net.minecraft.server.level;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;

public class ServerBossEvent {
    public ServerBossEvent(UUID id, Component name, BossEvent.BossBarColor color,
                           BossEvent.BossBarOverlay overlay) { }
    public void setName(Component name) { }
    public void setProgress(float progress) { }
    public void setColor(BossEvent.BossBarColor color) { }
    public void setOverlay(BossEvent.BossBarOverlay overlay) { }
    public void setDarkenScreen(boolean value) { }
    public void setPlayBossMusic(boolean value) { }
    public void setCreateWorldFog(boolean value) { }
    public void setVisible(boolean value) { }
    public void addPlayer(ServerPlayer player) { }
    public void removePlayer(ServerPlayer player) { }
    public void removeAllPlayers() { }
    public Collection<ServerPlayer> getPlayers() { return null; }
}
