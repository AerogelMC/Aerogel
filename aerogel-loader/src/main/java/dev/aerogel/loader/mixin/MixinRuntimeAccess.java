package dev.aerogel.loader.mixin;

import dev.aerogel.loader.runtime.TransformingClassLoader;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

public final class MixinRuntimeAccess {
    private static volatile TransformingClassLoader classLoader;
    private static volatile IMixinTransformer transformer;

    private MixinRuntimeAccess() {
    }

    public static void attach(TransformingClassLoader loader) {
        if (classLoader != null) {
            throw new IllegalStateException("Aerogel Mixin runtime is already attached");
        }
        classLoader = loader;
    }

    static TransformingClassLoader classLoader() {
        TransformingClassLoader current = classLoader;
        if (current == null) {
            throw new IllegalStateException("Aerogel transforming class loader is not attached");
        }
        return current;
    }

    static void transformer(IMixinTransformer value) {
        transformer = value;
    }

    public static IMixinTransformer transformer() {
        IMixinTransformer current = transformer;
        if (current == null) {
            throw new IllegalStateException("Mixin did not provide a transformer");
        }
        return current;
    }
}
