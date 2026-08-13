package dev.aerogel.api.scoreboard;

import dev.aerogel.api.Registration;
import java.util.Collection;
import net.minecraft.network.chat.Component;

public interface Team extends Registration {
    String name();
    net.minecraft.world.scores.PlayerTeam vanilla();
    Team displayName(Component value);
    Team prefix(Component value);
    Team suffix(Component value);
    Team friendlyFire(boolean value);
    Team seeFriendlyInvisible(boolean value);
    Team add(String holder);
    Team remove(String holder);
    Collection<String> members();
}
