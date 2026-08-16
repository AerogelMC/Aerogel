package net.minecraft.world.entity.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;

public abstract class Player extends LivingEntity {
    public GameProfile getGameProfile() { return null; }
    public PlayerTeam getTeam() { return null; }
    public double blockInteractionRange() { return 0.0; }
    public void causeFoodExhaustion(float amount) { }
    public Inventory getInventory() { return null; }

    public enum BedSleepingProblem {
        OTHER_PROBLEM
    }
}
