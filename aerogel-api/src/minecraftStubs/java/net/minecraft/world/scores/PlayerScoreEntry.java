package net.minecraft.world.scores;

public record PlayerScoreEntry(String owner, int value,
    net.minecraft.network.chat.Component display,
    net.minecraft.network.chat.numbers.NumberFormat numberFormatOverride) { }
