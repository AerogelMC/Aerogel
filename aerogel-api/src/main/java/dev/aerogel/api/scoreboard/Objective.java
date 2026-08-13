package dev.aerogel.api.scoreboard;

import dev.aerogel.api.Registration;
import net.minecraft.network.chat.Component;

public interface Objective extends Registration {
    String name();
    net.minecraft.world.scores.Objective vanilla();
    Objective displayName(Component value);
    Objective renderType(ObjectiveRenderType value);
    Objective display(DisplaySlot slot);
    Objective score(String holder, int value);
    int score(String holder);
    Objective reset(String holder);
}
