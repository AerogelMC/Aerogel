package net.minecraft.world.entity.player;

import com.mojang.authlib.GameProfile;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;

public abstract class Player extends LivingEntity {
    public GameProfile getGameProfile() { return null; }
    public PlayerTeam getTeam() { return null; }
}
