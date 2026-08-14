package net.minecraft.world.scores;

import net.minecraft.network.chat.Component;

public class PlayerTeam extends Team {
    public void setPlayerPrefix(Component prefix) { }
    public void setPlayerSuffix(Component suffix) { }
    public void setNameTagVisibility(Visibility visibility) { }
    public boolean isAllowFriendlyFire() { return false; }
    public void setAllowFriendlyFire(boolean allow) { }
    public boolean canSeeFriendlyInvisibles() { return false; }
    public void setSeeFriendlyInvisibles(boolean see) { }
    public CollisionRule getCollisionRule() { return null; }
    public void setCollisionRule(CollisionRule rule) { }
    public Visibility getDeathMessageVisibility() { return null; }
    public void setDeathMessageVisibility(Visibility visibility) { }
}
