package net.minecraft.world.scores;

public interface ScoreHolder {
    String getScoreboardName();
    static ScoreHolder forNameOnly(String name) { return null; }
}
