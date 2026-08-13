package dev.aerogel.loader.restart;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class RestartAddressRegistry {
    private static final Map<Object, Address> ADDRESSES = Collections.synchronizedMap(new WeakHashMap<>());

    private RestartAddressRegistry() {
    }

    public static void remember(Object connection, String host, int port) {
        if (connection == null || host == null || host.isBlank() || port < 1 || port > 65535) {
            return;
        }
        int forwardingSeparator = host.indexOf('\0');
        String cleanHost = forwardingSeparator < 0 ? host : host.substring(0, forwardingSeparator);
        if (!cleanHost.isBlank()) {
            ADDRESSES.put(connection, new Address(cleanHost, port));
        }
    }

    static Address address(Object connection) {
        return ADDRESSES.get(connection);
    }

    record Address(String host, int port) {
    }
}
