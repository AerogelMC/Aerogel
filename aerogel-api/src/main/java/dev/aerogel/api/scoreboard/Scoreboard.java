package dev.aerogel.api.scoreboard;

import java.util.Collection;
import java.util.Optional;
import net.minecraft.network.chat.Component;

public interface Scoreboard {
    net.minecraft.world.scores.Scoreboard vanilla();
    Objective objective(String name, Component displayName);
    Optional<Objective> findObjective(String name);
    Collection<Objective> objectives();
    Team team(String name);
    Optional<Team> findTeam(String name);
    Collection<Team> teams();
}
