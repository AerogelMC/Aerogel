package dev.aerogel.loader.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("dev.aerogel.ServerTickTiming")
@Label("Aerogel Server Tick")
@Category({"Aerogel", "Tick"})
@StackTrace(false)
public final class ServerTickTimingEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(ServerTickTimingEvent.class);

    @Label("Server Tick")
    public long serverTick;

    public static boolean enabled() {
        return TYPE.isEnabled();
    }
}
