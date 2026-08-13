package dev.aerogel.loader.install;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadProgressTest {
    @Test
    void copiesTheDownloadAndReportsItsFinalSize() throws Exception {
        byte[] source = new byte[180_000];
        for (int index = 0; index < source.length; index++) {
            source[index] = (byte) index;
        }
        ByteArrayOutputStream destination = new ByteArrayOutputStream();
        List<Long> updates = new ArrayList<>();

        long transferred = DownloadProgress.copy(new ByteArrayInputStream(source), destination,
            source.length, (current, expected) -> updates.add(current));

        assertEquals(source.length, transferred);
        assertArrayEquals(source, destination.toByteArray());
        assertEquals(0L, updates.getFirst());
        assertEquals((long) source.length, updates.getLast());
    }

    @Test
    void printsReadableProgressForRedirectedOutput() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DownloadProgress.Printer printer = new DownloadProgress.Printer(
            new PrintStream(bytes, true, StandardCharsets.UTF_8), false);

        printer.onProgress(0, 1024);
        printer.onProgress(512, 1024);
        printer.onProgress(1024, 1024);
        printer.finish(1024, 1024);

        String output = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("  0% (0 B / 1.0 KiB)"));
        assertTrue(output.contains(" 50% (512 B / 1.0 KiB)"));
        assertTrue(output.contains("100% (1.0 KiB / 1.0 KiB)"));
    }
}
