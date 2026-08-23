package net.minecraft.server.level;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
public class TicketType {
    public static final long NO_TIMEOUT = 0L;
    public static final int FLAG_LOADING = 2;
    public TicketType(long timeout, int flags) { }
    public boolean shouldKeepDimensionActive() { return false; }
    public boolean hasTimeout() { return false; }
    public boolean doesLoad() { return false; }
    public boolean canExpireIfUnloaded() { return false; }
}
