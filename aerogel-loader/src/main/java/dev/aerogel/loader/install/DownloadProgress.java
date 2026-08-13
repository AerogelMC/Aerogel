package dev.aerogel.loader.install;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

final class DownloadProgress {
    private static final int BUFFER_SIZE = 64 * 1024;

    private DownloadProgress() {
    }

    static long copy(InputStream input, OutputStream output, long expectedSize, Listener listener)
        throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long transferred = 0;
        listener.onProgress(0, expectedSize);
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            output.write(buffer, 0, read);
            transferred += read;
            listener.onProgress(transferred, expectedSize);
        }
        return transferred;
    }

    @FunctionalInterface
    interface Listener {
        void onProgress(long transferred, long expectedSize);
    }

    static final class Printer implements Listener {
        private static final long INTERACTIVE_INTERVAL_NANOS = 100_000_000L;
        private static final long LOG_INTERVAL_NANOS = 5_000_000_000L;

        private final PrintStream output;
        private final boolean inline;
        private int lastPercent = -1;
        private int lastWidth;
        private long lastPrintedAt;
        private long lastTransferred = -1;
        private boolean lineOpen;

        Printer(PrintStream output, boolean inline) {
            this.output = output;
            this.inline = inline;
        }

        @Override
        public void onProgress(long transferred, long expectedSize) {
            int percent = percentage(transferred, expectedSize);
            long now = System.nanoTime();
            boolean finished = expectedSize > 0 && transferred >= expectedSize;
            boolean percentageStep = percent >= lastPercent + 10;
            long interval = inline ? INTERACTIVE_INTERVAL_NANOS : LOG_INTERVAL_NANOS;
            if (lastPercent >= 0 && !finished && !percentageStep && now - lastPrintedAt < interval) {
                return;
            }
            printLine(transferred, expectedSize, percent);
            lastPercent = percent;
            lastTransferred = transferred;
            lastPrintedAt = now;
        }

        void finish(long transferred, long expectedSize) {
            if (lastTransferred != transferred) {
                printLine(transferred, expectedSize, percentage(transferred, expectedSize));
            }
            if (inline && lineOpen) {
                output.println();
                output.flush();
                lineOpen = false;
            }
        }

        void abort() {
            if (inline && lineOpen) {
                output.println();
                output.flush();
                lineOpen = false;
            }
        }

        private void printLine(long transferred, long expectedSize, int percent) {
            String line;
            if (expectedSize > 0) {
                line = String.format(Locale.ROOT, "[Aerogel] Downloading server.jar: %3d%% (%s / %s)",
                    percent, formatBytes(transferred), formatBytes(expectedSize));
            } else {
                line = String.format(Locale.ROOT, "[Aerogel] Downloading server.jar: %s",
                    formatBytes(transferred));
            }
            if (inline) {
                int padding = Math.max(0, lastWidth - line.length());
                output.print('\r');
                output.print(line);
                if (padding > 0) {
                    output.print(" ".repeat(padding));
                }
                output.flush();
                lastWidth = line.length();
                lineOpen = true;
            } else {
                output.println(line);
            }
        }

        private static int percentage(long transferred, long expectedSize) {
            if (expectedSize <= 0) {
                return 0;
            }
            return (int) Math.min(100, transferred * 100 / expectedSize);
        }

        private static String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            }
            String[] units = {"KiB", "MiB", "GiB", "TiB"};
            double value = bytes;
            int unit = -1;
            do {
                value /= 1024.0;
                unit++;
            } while (value >= 1024.0 && unit < units.length - 1);
            return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
        }
    }
}
