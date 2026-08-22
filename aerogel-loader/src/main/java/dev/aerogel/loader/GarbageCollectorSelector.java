package dev.aerogel.loader;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Selects Aerogel's low-pause collector without silently falling back to G1. */
final class GarbageCollectorSelector {
    private static final List<String> SHENANDOAH = List.of(
        "-XX:+UseShenandoahGC",
        "-XX:ShenandoahGCMode=generational");
    private static final List<String> ZGC = List.of("-XX:+UseZGC");

    private GarbageCollectorSelector() {
    }

    static Selection select(
        Path javaExecutable,
        List<String> inheritedArguments,
        List<String> requestedArguments
    ) throws IOException {
        List<String> configured = new ArrayList<>(inheritedArguments.size()
            + requestedArguments.size());
        configured.addAll(inheritedArguments);
        configured.addAll(requestedArguments);
        if (configured.stream().anyMatch(GarbageCollectorSelector::selectsCollector)) {
            return new Selection(List.of(), "user-selected", true);
        }
        return select(arguments -> supported(javaExecutable, arguments));
    }

    static Selection select(Predicate<List<String>> supported) throws IOException {
        if (supported.test(SHENANDOAH)) {
            return new Selection(SHENANDOAH, "Generational Shenandoah", false);
        }
        if (supported.test(ZGC)) {
            return new Selection(ZGC, "Generational ZGC", false);
        }
        throw new IOException(
            "This Java runtime supports neither Shenandoah GC nor ZGC. "
                + "Aerogel will not silently fall back to a stop-the-world collector.");
    }

    private static boolean selectsCollector(String argument) {
        return argument.matches("-XX:\\+Use[A-Za-z0-9]+GC");
    }

    private static boolean supported(Path javaExecutable, List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 2);
        command.add(javaExecutable.toString());
        command.addAll(arguments);
        command.add("-version");
        try {
            Process process = new ProcessBuilder(command)
                .redirectOutput(Redirect.DISCARD)
                .redirectError(Redirect.DISCARD)
                .start();
            return process.waitFor() == 0;
        } catch (IOException exception) {
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    record Selection(List<String> arguments, String displayName, boolean explicit) {
        Selection {
            arguments = List.copyOf(arguments);
        }
    }
}
