package dev.aerogel.loader.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConsoleLoggingTest {
    @Test
    void configuresLog4jWithTheDetectedConsoleCharset() {
        String previous = System.getProperty("aerogel.console.charset");
        try {
            ConsoleLogging.configure(ConsoleLoggingTest.class.getClassLoader());

            String configured = System.getProperty("aerogel.console.charset");
            assertNotNull(configured);
            assertEquals(ConsoleLogging.consoleCharset(), Charset.forName(configured));
        } finally {
            if (previous == null) {
                System.clearProperty("aerogel.console.charset");
            } else {
                System.setProperty("aerogel.console.charset", previous);
            }
        }
    }
}
