package dev.aerogel.api.scoreboard;

public interface ScoreboardService {
    Scoreboard main();

    /**
     * Creates a hidden, plugin-owned display board for one connected player.
     * Configure it, then call show(). Create, read and mutate on the server
     * scheduler; the board uses vanilla's mutable scoreboard data structures.
     */
    PlayerScoreboard create(net.minecraft.server.level.ServerPlayer player);
}
