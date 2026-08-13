package dev.aerogel.loader.command;

import dev.aerogel.loader.restart.RestartCoordinator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

/** Registers Aerogel's full-process /restart command against the vanilla dispatcher. */
public final class RestartCommand {
    private RestartCommand() {
    }

    public static void register(Object minecraftServer) {
        try {
            ClassLoader loader = minecraftServer.getClass().getClassLoader();
            Class<?> commandsType = Class.forName("net.minecraft.commands.Commands", true, loader);
            Class<?> commandType = Class.forName("com.mojang.brigadier.Command", true, loader);
            Class<?> literalBuilderType = Class.forName(
                "com.mojang.brigadier.builder.LiteralArgumentBuilder", true, loader);
            Class<?> permissionCheckType = Class.forName(
                "net.minecraft.server.permissions.PermissionCheck", true, loader);

            Object commands = minecraftServer.getClass().getMethod("getCommands").invoke(minecraftServer);
            Object dispatcher = commandsType.getMethod("getDispatcher").invoke(commands);
            Object root = commandsType.getMethod("literal", String.class).invoke(null, "restart");
            Object owners = commandsType.getField("LEVEL_OWNERS").get(null);
            Object permission = commandsType.getMethod("hasPermission", permissionCheckType)
                .invoke(null, owners);
            literalBuilderType.getMethod("requires", Predicate.class).invoke(root, permission);
            literalBuilderType.getMethod("executes", commandType)
                .invoke(root, commandProxy(commandType, minecraftServer, loader));
            dispatcher.getClass().getMethod("register", literalBuilderType).invoke(dispatcher, root);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot register /restart for Minecraft 26.2", exception);
        }
    }

    private static Object commandProxy(Class<?> type, Object server, ClassLoader loader) {
        InvocationHandler handler = (proxy, method, arguments) -> {
            if (method.getName().equals("run")) {
                Object context = arguments[0];
                if (!RestartCoordinator.available()) {
                    sendFailure(context, loader, "commands.aerogel.restart.unavailable",
                        "Automatic restart is unavailable because Aerogel is not running under its launcher.");
                    return 0;
                }
                if (RestartCoordinator.requested()) {
                    sendFailure(context, loader, "commands.aerogel.restart.already",
                        "A server restart is already in progress.");
                    return 0;
                }
                if (!RestartCoordinator.request(server)) {
                    sendFailure(context, loader, "commands.aerogel.restart.prepare_failed",
                        "The server could not prepare for restart. See the console for details.");
                    return 0;
                }
                return 1;
            }
            return switch (method.getName()) {
                case "toString" -> "Aerogel restart command";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            };
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static void sendFailure(Object context, ClassLoader loader, String key, String fallback)
        throws ReflectiveOperationException {
        Object source = context.getClass().getMethod("getSource").invoke(context);
        String language = sourceLanguage(source);
        String localized = CommandTranslations.fallback(language, key, fallback);
        Class<?> componentType = Class.forName("net.minecraft.network.chat.Component", true, loader);
        Object component = componentType.getMethod(
                "translatableWithFallback", String.class, String.class, Object[].class)
            .invoke(null, key, localized, new Object[0]);
        source.getClass().getMethod("sendFailure", componentType).invoke(source, component);
    }

    private static String sourceLanguage(Object source) {
        try {
            Object player = source.getClass().getMethod("getPlayer").invoke(source);
            if (player == null) return "en_us";
            Object information = player.getClass().getMethod("clientInformation").invoke(player);
            return (String) information.getClass().getMethod("language").invoke(information);
        } catch (ReflectiveOperationException ignored) {
            return "en_us";
        }
    }
}
