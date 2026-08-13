package dev.aerogel.loader.mixin;

import dev.aerogel.loader.plugin.PluginDescriptor;
import dev.aerogel.loader.runtime.TransformingClassLoader;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MixinBootstrapper {
    private MixinBootstrapper() {
    }

    public static void initialize(TransformingClassLoader classLoader, List<PluginDescriptor> plugins) {
        MixinRuntimeAccess.attach(classLoader);
        System.setProperty("mixin.service", AerogelMixinService.class.getName());
        System.setProperty("mixin.env.remapRefMap", "false");
        MixinBootstrap.init();

        Set<String> configurations = new HashSet<>();
        configurations.add("aerogel-core.mixins.json");
        Mixins.addConfiguration("aerogel-core.mixins.json");
        for (PluginDescriptor plugin : plugins) {
            for (String configuration : plugin.mixins()) {
                if (!configurations.add(configuration)) {
                    throw new IllegalStateException("Duplicate Mixin configuration name: " + configuration);
                }
                try {
                    Mixins.addConfiguration(configuration);
                } catch (Throwable throwable) {
                    throw new IllegalStateException(
                        "Cannot register Mixin configuration " + configuration + " from plugin " + plugin.id(), throwable
                    );
                }
            }
        }

        gotoPhase(MixinEnvironment.Phase.INIT);
        gotoPhase(MixinEnvironment.Phase.DEFAULT);
        classLoader.installTransformer(MixinRuntimeAccess.transformer());
    }

    private static void gotoPhase(MixinEnvironment.Phase phase) {
        try {
            Method method = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            method.setAccessible(true);
            method.invoke(null, phase);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException("Mixin phase API is incompatible", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("Mixin failed while entering " + phase, exception.getCause());
        }
    }
}
