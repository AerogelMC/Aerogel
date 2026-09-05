package dev.aerogel.api.scoreboard;

import dev.aerogel.api.Registration;
import net.minecraft.server.level.ServerPlayer;

/**
 * A client display, not a replacement for server gameplay scores or team rules.
 * One board is visible per player. Switching boards hides the previous board;
 * hiding/closing the visible board restores the current server scoreboard.
 * Disconnect closes the board; respawn retains it. Plugin unload closes it.
 * Use the server scheduler for access and mutation; close may be called anywhere.
 */
public interface PlayerScoreboard extends Scoreboard, Registration {
    ServerPlayer player();
    PlayerScoreboard show();
    PlayerScoreboard hide();
    boolean visible();
}
