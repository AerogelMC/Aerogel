package net.minecraft.world.scores;

import net.minecraft.network.chat.Component;
import java.util.Optional;

public class PlayerTeam extends Team {
    public PlayerTeam(Scoreboard scoreboard, String name) { }
    public String getName() { return null; }
    public Component getDisplayName() { return null; }
    public void setDisplayName(Component name) { }
    public void setPlayerPrefix(Component prefix) { }
    public void setPlayerSuffix(Component suffix) { }
    public Component getPlayerPrefix() { return null; }
    public Component getPlayerSuffix() { return null; }
    public Visibility getNameTagVisibility() { return null; }
    public void setNameTagVisibility(Visibility visibility) { }
    public boolean isAllowFriendlyFire() { return false; }
    public void setAllowFriendlyFire(boolean allow) { }
    public boolean canSeeFriendlyInvisibles() { return false; }
    public void setSeeFriendlyInvisibles(boolean see) { }
    public CollisionRule getCollisionRule() { return null; }
    public void setCollisionRule(CollisionRule rule) { }
    public Visibility getDeathMessageVisibility() { return null; }
    public void setDeathMessageVisibility(Visibility visibility) { }
    public java.util.Collection<String> getPlayers() { return null; }
    public void setColor(Optional<TeamColor> color) { }
}
