package net.minecraft.world.level;

import net.minecraft.server.level.Ticket;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class TicketStorage {
    @FunctionalInterface
    public interface TicketPredicate {
        boolean test(Ticket ticket, long chunkKey);
    }
}
