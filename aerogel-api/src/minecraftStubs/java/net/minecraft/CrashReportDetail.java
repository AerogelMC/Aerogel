package net.minecraft;

/** Compile-time name stub. Not included in the Aerogel API JAR. */
@FunctionalInterface
public interface CrashReportDetail<T> {
    T call() throws Exception;
}
