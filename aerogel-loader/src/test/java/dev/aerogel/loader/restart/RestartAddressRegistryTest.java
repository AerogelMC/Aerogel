package dev.aerogel.loader.restart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RestartAddressRegistryTest {
    @Test
    void preservesOriginalHostAndRemovesForwardingPayload() {
        Object connection = new Object();
        RestartAddressRegistry.remember(connection, "play.example.net\0forwarded-data", 25565);

        RestartAddressRegistry.Address address = RestartAddressRegistry.address(connection);
        assertEquals("play.example.net", address.host());
        assertEquals(25565, address.port());
    }

    @Test
    void rejectsAnAddressThatCannotBeTransferred() {
        Object connection = new Object();
        RestartAddressRegistry.remember(connection, "", 25565);
        assertNull(RestartAddressRegistry.address(connection));
    }
}
