package dev.aerogel.api.bossbar;

import dev.aerogel.api.Registration;
import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;

public interface BossBar extends Registration {
    ServerBossEvent vanilla();
    BossBar name(Component value);
    BossBar progress(float value);
    BossBar color(BossBarColor value);
    BossBar overlay(BossBarOverlay value);
    BossBar darkenScreen(boolean value);
    BossBar playMusic(boolean value);
    BossBar worldFog(boolean value);
    BossBar visible(boolean value);
    BossBar add(ServerPlayer vanillaPlayer);
    BossBar remove(ServerPlayer vanillaPlayer);
    BossBar clearViewers();
    Collection<ServerPlayer> viewers();
}
