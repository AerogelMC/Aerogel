package dev.aerogel.loader.event;

import dev.aerogel.api.PluginContext;
import dev.aerogel.api.event.AerogelEvent;
import dev.aerogel.api.event.EventHandler;
import dev.aerogel.loader.plugin.PluginDescriptor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PluginEventScanner {
    private static final String HANDLER_DESCRIPTOR = Type.getDescriptor(EventHandler.class);

    public int register(
        PluginDescriptor plugin,
        ClassLoader classLoader,
        PluginContext context,
        EventRegistry.OwnedEventBus events,
        Map<String, Object> existingInstances
    ) throws Exception {
        Set<String> listenerClasses = findListenerClasses(plugin);
        Map<Class<?>, Object> automaticInstances = new HashMap<>();
        int registered = 0;
        for (String className : listenerClasses) {
            Class<?> type = Class.forName(className, false, classLoader);
            for (Method method : type.getDeclaredMethods()) {
                EventHandler annotation = method.getAnnotation(EventHandler.class);
                if (annotation == null) {
                    continue;
                }
                validate(plugin, method);
                @SuppressWarnings("unchecked")
                Class<? extends AerogelEvent> eventType =
                    (Class<? extends AerogelEvent>) method.getParameterTypes()[0];
                MethodHandle handle = MethodHandles.privateLookupIn(type, MethodHandles.lookup())
                    .unreflect(method);
                if (!Modifier.isStatic(method.getModifiers())) {
                    Object instance = existingInstances.get(className);
                    if (instance == null) {
                        instance = automaticInstances.computeIfAbsent(type, ignored -> {
                            try {
                                return instantiate(type, context);
                            } catch (ReflectiveOperationException exception) {
                                throw new ListenerInstantiationException(exception);
                            }
                        });
                    }
                    handle = handle.bindTo(instance);
                }
                events.registerMethod(eventType, annotation.priority(), annotation.receiveCancelled(), handle);
                registered++;
            }
        }
        return registered;
    }

    private static Set<String> findListenerClasses(PluginDescriptor plugin) throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        try (JarFile jar = new JarFile(plugin.jar().toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                    || entry.getName().startsWith("META-INF/versions/")) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    ClassReader reader = new ClassReader(input);
                    HandlerClassVisitor visitor = new HandlerClassVisitor();
                    reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    if (visitor.hasHandler) {
                        classes.add(reader.getClassName().replace('/', '.'));
                    }
                }
            }
        }
        return classes;
    }

    private static Object instantiate(Class<?> type, PluginContext context) throws ReflectiveOperationException {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new ReflectiveOperationException("Listener class must be concrete: " + type.getName());
        }
        Constructor<?> constructor;
        try {
            constructor = type.getDeclaredConstructor(PluginContext.class);
            constructor.setAccessible(true);
            return constructor.newInstance(context);
        } catch (NoSuchMethodException ignored) {
            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        }
    }

    private static void validate(PluginDescriptor plugin, Method method) {
        List<String> problems = new ArrayList<>();
        if (Modifier.isAbstract(method.getModifiers())) {
            problems.add("must not be abstract");
        }
        if (method.getReturnType() != void.class) {
            problems.add("must return void");
        }
        if (method.getParameterCount() != 1
            || !AerogelEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
            problems.add("must accept exactly one AerogelEvent parameter");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid @EventHandler in plugin " + plugin.id() + ": "
                + method.toGenericString() + " (" + String.join(", ", problems) + ")");
        }
    }

    private static final class HandlerClassVisitor extends ClassVisitor {
        private boolean hasHandler;

        private HandlerClassVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (HANDLER_DESCRIPTOR.equals(descriptor)) {
                        hasHandler = true;
                    }
                    return null;
                }
            };
        }
    }

    private static final class ListenerInstantiationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ListenerInstantiationException(ReflectiveOperationException cause) {
            super(cause);
        }
    }
}
