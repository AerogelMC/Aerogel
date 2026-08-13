package dev.aerogel.loader.runtime;

import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** One child-first namespace for Minecraft, its libraries, and dependency-ordered plugins. */
public final class TransformingClassLoader extends URLClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private static final String[] PARENT_FIRST = {
        "java.", "javax.", "jdk.", "sun.", "com.sun.",
        "org.spongepowered.asm.", "org.objectweb.asm."
    };
    private static final Set<String> AEROGEL_PARENT_CLASSES = Set.of(
        "dev.aerogel.loader.AerogelMain",
        "dev.aerogel.loader.BuildInfo",
        "dev.aerogel.loader.runtime.AerogelServerBootstrap",
        "dev.aerogel.loader.runtime.TransformingClassLoader",
        "dev.aerogel.loader.mixin.AerogelMixinService",
        "dev.aerogel.loader.mixin.AerogelPropertyService",
        "dev.aerogel.loader.mixin.MixinBootstrapper",
        "dev.aerogel.loader.mixin.MixinRuntimeAccess"
    );

    private final Set<String> definedClasses = ConcurrentHashMap.newKeySet();
    private final Set<String> invalidClasses = ConcurrentHashMap.newKeySet();
    private volatile IMixinTransformer transformer;

    public TransformingClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    public void installTransformer(IMixinTransformer transformer) {
        if (this.transformer != null) {
            throw new IllegalStateException("Transformer already installed");
        }
        this.transformer = transformer;
    }

    public URL[] urls() {
        return getURLs();
    }

    public boolean isClassDefined(String name) {
        return definedClasses.contains(name);
    }

    public void registerInvalidClass(String name) {
        invalidClasses.add(name);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (parentFirst(name)) {
                    loaded = super.loadClass(name, false);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notFound) {
                        loaded = super.loadClass(name, false);
                    }
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (invalidClasses.contains(name)) {
            throw new ClassNotFoundException("Class was rejected by Mixin: " + name);
        }
        URL resource = findResource(name.replace('.', '/') + ".class");
        byte[] bytes;
        if (resource == null) {
            IMixinTransformer current = transformer;
            bytes = current == null ? null : current.transformClassBytes(name, name, null);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
        } else {
            try (InputStream input = resource.openStream()) {
                bytes = input.readAllBytes();
            } catch (IOException exception) {
                throw new ClassNotFoundException("Cannot read " + name, exception);
            }
            IMixinTransformer current = transformer;
            if (current != null) {
                bytes = current.transformClassBytes(name, name, bytes);
            }
        }
        CodeSource codeSource = new CodeSource(resource, (Certificate[]) null);
        Class<?> defined = defineClass(name, bytes, 0, bytes.length, codeSource);
        definedClasses.add(name);
        return defined;
    }

    public byte[] classBytes(String name, boolean runTransformers) throws IOException, ClassNotFoundException {
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) {
            resource = getParent().getResource(name.replace('.', '/') + ".class");
        }
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        byte[] bytes;
        try (InputStream input = resource.openStream()) {
            bytes = input.readAllBytes();
        }
        // Mixin asks this provider for metadata. The flag refers to other delegated
        // transformers; Aerogel has none, and reapplying Mixin here would recurse.
        return bytes;
    }

    @Override
    public URL getResource(String name) {
        URL resource = findResource(name);
        return resource != null ? resource : getParent().getResource(name);
    }

    private static boolean parentFirst(String name) {
        if (AEROGEL_PARENT_CLASSES.contains(name)
            || name.startsWith("dev.aerogel.loader.cli.")
            || name.startsWith("dev.aerogel.loader.install.")) {
            return true;
        }
        for (String prefix : PARENT_FIRST) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
