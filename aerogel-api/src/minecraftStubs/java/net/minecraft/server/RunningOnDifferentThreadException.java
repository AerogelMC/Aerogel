package net.minecraft.server;

public class RunningOnDifferentThreadException extends RuntimeException {
    public static final RunningOnDifferentThreadException RUNNING_ON_DIFFERENT_THREAD =
        new RunningOnDifferentThreadException();
}
