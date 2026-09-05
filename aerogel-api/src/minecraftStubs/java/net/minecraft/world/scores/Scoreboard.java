package net.minecraft.world.scores;

public class Scoreboard {
    public Objective getDisplayObjective(DisplaySlot slot) { return null; }
    public java.util.Collection<PlayerScoreEntry> listPlayerScores(Objective objective) { return null; }
    public void onObjectiveAdded(Objective objective) { }
    public void onObjectiveChanged(Objective objective) { }
    public void onObjectiveRemoved(Objective objective) { }
    protected void onScoreChanged(ScoreHolder holder, Objective objective, Score score) { }
    public void onPlayerRemoved(ScoreHolder holder) { }
    public void onPlayerScoreRemoved(ScoreHolder holder, Objective objective) { }
    public void onTeamAdded(PlayerTeam team) { }
    public void onTeamChanged(PlayerTeam team) { }
    public void onTeamRemoved(PlayerTeam team) { }
    public Objective getObjective(String name) { return null; }
    public Objective addObjective(
        String name, net.minecraft.world.scores.criteria.ObjectiveCriteria criteria,
        net.minecraft.network.chat.Component displayName,
        net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType renderType,
        boolean displayAutoUpdate,
        net.minecraft.network.chat.numbers.NumberFormat numberFormat
    ) { return null; }
    public java.util.Collection<Objective> getObjectives() { return null; }
    public void removeObjective(Objective objective) { }
    public void setDisplayObjective(DisplaySlot slot, Objective objective) { }
    public ScoreAccess getOrCreatePlayerScore(ScoreHolder holder, Objective objective) { return null; }
    public void resetSinglePlayerScore(ScoreHolder holder, Objective objective) { }
    public PlayerTeam getPlayerTeam(String name) { return null; }
    public PlayerTeam addPlayerTeam(String name) { return null; }
    public java.util.Collection<PlayerTeam> getPlayerTeams() { return null; }
    public void removePlayerTeam(PlayerTeam team) { }
    public boolean addPlayerToTeam(String playerName, PlayerTeam team) { return false; }
    public void removePlayerFromTeam(String playerName, PlayerTeam team) { }
}
