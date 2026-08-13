package dev.aerogel.api.bossbar;

import net.minecraft.network.chat.Component;

public interface BossBarService {
    BossBar create(Component name);
    BossBar create(Component name, BossBarColor color, BossBarOverlay overlay);
}
