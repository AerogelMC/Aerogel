package dev.aerogel.loader.command;

import dev.aerogel.loader.plugin.PluginManager;
import dev.aerogel.loader.runtime.AerogelRuntime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Reflection boundary that keeps Minecraft and Brigadier classes out of Aerogel's distributable API. */
public final class PluginsCommand {
    private static final String ROOT = "commands.aerogel.plugins.";
    private static volatile Object server;

    private PluginsCommand() {
    }

    public static void register(Object minecraftServer) {
        try {
            ClassLoader loader = minecraftServer.getClass().getClassLoader();
            Class<?> commandsType = Class.forName("net.minecraft.commands.Commands", true, loader);
            Class<?> commandType = Class.forName("com.mojang.brigadier.Command", true, loader);
            Class<?> argumentBuilderType = Class.forName("com.mojang.brigadier.builder.ArgumentBuilder", true, loader);
            Class<?> literalBuilderType = Class.forName(
                "com.mojang.brigadier.builder.LiteralArgumentBuilder", true, loader);
            Class<?> requiredBuilderType = Class.forName(
                "com.mojang.brigadier.builder.RequiredArgumentBuilder", true, loader);
            Class<?> argumentType = Class.forName("com.mojang.brigadier.arguments.ArgumentType", true, loader);
            Class<?> stringArgumentType = Class.forName(
                "com.mojang.brigadier.arguments.StringArgumentType", true, loader);
            Class<?> suggestionProviderType = Class.forName(
                "com.mojang.brigadier.suggestion.SuggestionProvider", true, loader);

            Object commands = minecraftServer.getClass().getMethod("getCommands").invoke(minecraftServer);
            Object dispatcher = commandsType.getMethod("getDispatcher").invoke(commands);
            Object root = commandsType.getMethod("literal", String.class).invoke(null, "plugins");
            Object list = commandsType.getMethod("literal", String.class).invoke(null, "list");
            Object reload = commandsType.getMethod("literal", String.class).invoke(null, "reload");
            Object tps = commandsType.getMethod("literal", String.class).invoke(null, "tps");
            Object word = stringArgumentType.getMethod("word").invoke(null);
            Object plugin = commandsType.getMethod("argument", String.class, argumentType)
                .invoke(null, "plugin", word);

            Object permissionCheck = commandsType.getField("LEVEL_GAMEMASTERS").get(null);
            Class<?> permissionCheckType = Class.forName(
                "net.minecraft.server.permissions.PermissionCheck", true, loader);
            Object permission = commandsType.getMethod("hasPermission", permissionCheckType)
                .invoke(null, permissionCheck);
            literalBuilderType.getMethod("requires", Predicate.class).invoke(root, permission);

            literalBuilderType.getMethod("executes", commandType)
                .invoke(list, commandProxy(commandType, context -> list(context, loader)));
            literalBuilderType.getMethod("executes", commandType)
                .invoke(reload, commandProxy(commandType, context -> reloadAll(context, loader)));
            requiredBuilderType.getMethod("suggests", suggestionProviderType)
                .invoke(plugin, suggestionProxy(suggestionProviderType));
            requiredBuilderType.getMethod("executes", commandType)
                .invoke(plugin, commandProxy(commandType, context -> reloadOne(context, loader)));
            literalBuilderType.getMethod("then", argumentBuilderType).invoke(reload, plugin);
            literalBuilderType.getMethod("then", argumentBuilderType).invoke(root, list);
            literalBuilderType.getMethod("then", argumentBuilderType).invoke(root, reload);
            dispatcher.getClass().getMethod("register", literalBuilderType).invoke(dispatcher, root);

            Object allCheck = commandsType.getField("LEVEL_ALL").get(null);
            Object allPermission = commandsType.getMethod("hasPermission", permissionCheckType)
                .invoke(null, allCheck);
            literalBuilderType.getMethod("requires", Predicate.class).invoke(tps, allPermission);
            literalBuilderType.getMethod("executes", commandType)
                .invoke(tps, commandProxy(commandType, context -> tps(context, loader, minecraftServer)));
            dispatcher.getClass().getMethod("register", literalBuilderType).invoke(dispatcher, tps);
            server = minecraftServer;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot register /plugins for Minecraft 26.2", exception);
        }
    }

    public static List<String> complete(String input, int cursor) {
        Object current = server;
        if (current == null) {
            return List.of();
        }
        try {
            String command = input.startsWith("/") ? input.substring(1) : input;
            int commandCursor = input.startsWith("/") ? Math.max(0, cursor - 1) : cursor;
            Object commands = current.getClass().getMethod("getCommands").invoke(current);
            Object dispatcher = commands.getClass().getMethod("getDispatcher").invoke(commands);
            Object source = current.getClass().getMethod("createCommandSourceStack").invoke(current);
            Method parse = findMethod(dispatcher.getClass(), "parse", String.class, Object.class);
            Object results = parse.invoke(dispatcher, command, source);
            Method completion = findMethod(dispatcher.getClass(), "getCompletionSuggestions",
                results.getClass(), int.class);
            Object suggestions = ((CompletableFuture<?>) completion.invoke(dispatcher, results, commandCursor))
                .get(750, TimeUnit.MILLISECONDS);
            @SuppressWarnings("unchecked")
            List<Object> values = (List<Object>) suggestions.getClass().getMethod("getList").invoke(suggestions);
            List<String> completed = new ArrayList<>();
            for (Object value : values) {
                completed.add((String) value.getClass().getMethod("getText").invoke(value));
            }
            return completed;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static int list(Object context, ClassLoader loader) throws ReflectiveOperationException {
        List<PluginManager.PluginInfo> plugins = AerogelRuntime.pluginManager().pluginInfos();
        sendSuccess(context, loader, ROOT + "list", "Plugins (%s): %s",
            plugins.size(), displayPlugins(context, loader, plugins));
        return plugins.size();
    }

    private static int reloadAll(Object context, ClassLoader loader) throws ReflectiveOperationException {
        sendSuccess(context, loader, ROOT + "reload_all.starting", "Reloading all plugins...");
        long started = System.nanoTime();
        PluginManager.ReloadResult result = AerogelRuntime.pluginManager().reloadAll();
        String elapsed = elapsedSeconds(started);
        if (result.successful()) {
            sendSuccess(context, loader, ROOT + "reload_all.success",
                "Loaded or reloaded %s plugin(s), unloaded %s in %s seconds",
                result.reloaded().size(), result.unloaded().size(), elapsed);
        } else {
            sendFailure(context, loader, ROOT + "reload_partial",
                "Loaded or reloaded %s plugin(s), unloaded %s in %s seconds; failed: %s",
                result.reloaded().size(), result.unloaded().size(), elapsed,
                String.join(", ", result.failures().keySet()));
        }
        sendMixinNotice(context, loader, result.mixinRestartRequired());
        return result.reloaded().size() + result.unloaded().size();
    }

    private static int reloadOne(Object context, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> stringArgument = Class.forName("com.mojang.brigadier.arguments.StringArgumentType", true, loader);
        String id = (String) stringArgument.getMethod("getString", context.getClass(), String.class)
            .invoke(null, context, "plugin");
        sendSuccess(context, loader, ROOT + "reload_one.starting", "Reloading plugin %s...", id);
        long started = System.nanoTime();
        Optional<PluginManager.ReloadResult> optional = AerogelRuntime.pluginManager().reload(id);
        if (optional.isEmpty()) {
            sendFailure(context, loader, ROOT + "unknown", "Unknown plugin: %s", id);
            return 0;
        }
        PluginManager.ReloadResult result = optional.get();
        String elapsed = elapsedSeconds(started);
        if (!result.successful()) {
            sendFailure(context, loader, ROOT + "reload_failed",
                "Could not reload plugin %s after %s seconds", id, elapsed);
            sendMixinNotice(context, loader, result.mixinRestartRequired());
            return 0;
        }
        if (result.unloaded().contains(id)) {
            sendSuccess(context, loader, ROOT + "reload_one.unloaded",
                "Unloaded plugin %s in %s seconds", id, elapsed);
            sendMixinNotice(context, loader, result.mixinRestartRequired());
            return 1;
        }
        sendSuccess(context, loader, ROOT + "reload_one.success",
            "Reloaded plugin %s in %s seconds", id, elapsed);
        sendMixinNotice(context, loader, result.mixinRestartRequired());
        return 1;
    }

    private static void sendMixinNotice(Object context, ClassLoader loader, List<String> pluginIds)
        throws ReflectiveOperationException {
        if (pluginIds.isEmpty()) return;
        sendWarning(context, loader, ROOT + "mixin_notice",
            "Some Mixin changes for %s may not be applied until the server restarts",
            String.join(", ", pluginIds));
    }

    private static int tps(Object context, ClassLoader loader, Object minecraftServer)
        throws ReflectiveOperationException {
        TpsMonitor.Snapshot snapshot = TpsMonitor.snapshot();
        long averageNanos = (long) minecraftServer.getClass().getMethod("getAverageTickTimeNanos")
            .invoke(minecraftServer);
        sendSuccess(context, loader, "commands.aerogel.tps",
            "TPS (1m, 5m, 15m): %s, %s, %s | MSPT: %s",
            decimal(snapshot.oneMinute()), decimal(snapshot.fiveMinutes()),
            decimal(snapshot.fifteenMinutes()), decimal(averageNanos / 1_000_000.0));
        return 1;
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String elapsedSeconds(long started) {
        return decimal((System.nanoTime() - started) / 1_000_000_000.0);
    }

    private static Object displayPlugins(Object context, ClassLoader loader, List<PluginManager.PluginInfo> plugins)
        throws ReflectiveOperationException {
        if (!plugins.isEmpty()) {
            Class<?> componentType = Class.forName("net.minecraft.network.chat.Component", true, loader);
            Class<?> mutableType = Class.forName("net.minecraft.network.chat.MutableComponent", true, loader);
            Class<?> formattingType = Class.forName("net.minecraft.ChatFormatting", true, loader);
            Object gray = formattingType.getField("GRAY").get(null);
            Object red = formattingType.getField("RED").get(null);
            Object result = componentType.getMethod("empty").invoke(null);
            Method appendString = mutableType.getMethod("append", String.class);
            Method appendComponent = mutableType.getMethod("append", componentType);
            Method withStyle = mutableType.getMethod("withStyle", formattingType);
            for (int index = 0; index < plugins.size(); index++) {
                PluginManager.PluginInfo plugin = plugins.get(index);
                if (index > 0) {
                    appendString.invoke(result, ", ");
                }
                if (plugin.name().equals(plugin.id())) {
                    Object name = componentType.getMethod("literal", String.class)
                        .invoke(null, plugin.id());
                    if (!plugin.enabled()) {
                        withStyle.invoke(name, red);
                    }
                    appendComponent.invoke(result, name);
                } else {
                    Object name = componentType.getMethod("literal", String.class)
                        .invoke(null, plugin.name());
                    if (!plugin.enabled()) {
                        withStyle.invoke(name, red);
                    }
                    appendComponent.invoke(result, name);
                    Object id = componentType.getMethod("literal", String.class)
                        .invoke(null, " <" + plugin.id() + ">");
                    withStyle.invoke(id, gray);
                    appendComponent.invoke(result, id);
                }
                if (!plugin.enabled()) {
                    Object source = context.getClass().getMethod("getSource").invoke(context);
                    String fallback = CommandTranslations.fallback(
                        sourceLanguage(source), ROOT + "disabled", "Disabled");
                    Object status = componentType.getMethod(
                            "translatableWithFallback", String.class, String.class, Object[].class)
                        .invoke(null, ROOT + "disabled", fallback, new Object[0]);
                    withStyle.invoke(status, red);
                    appendString.invoke(result, " — ");
                    appendComponent.invoke(result, status);
                }
            }
            return result;
        }
        Object source = context.getClass().getMethod("getSource").invoke(context);
        return CommandTranslations.fallback(sourceLanguage(source), ROOT + "none", "None");
    }

    private static Object suggestionProxy(Class<?> type) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (!method.getName().equals("getSuggestions")) {
                return objectMethod(proxy, method, args);
            }
            Object builder = args[1];
            String remaining = ((String) builder.getClass().getMethod("getRemainingLowerCase").invoke(builder))
                .toLowerCase(Locale.ROOT);
            Method suggest = builder.getClass().getMethod("suggest", String.class);
            for (String id : AerogelRuntime.pluginManager().reloadablePluginIds()) {
                if (id.startsWith(remaining)) {
                    suggest.invoke(builder, id);
                }
            }
            return builder.getClass().getMethod("buildFuture").invoke(builder);
        });
    }

    private static Object commandProxy(Class<?> type, CommandAction action) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getName().equals("run")) {
                return action.run(args[0]);
            }
            return objectMethod(proxy, method, args);
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static void sendSuccess(Object context, ClassLoader loader, String key, String fallback, Object... args)
        throws ReflectiveOperationException {
        Object source = context.getClass().getMethod("getSource").invoke(context);
        Object component = component(loader, source, key, fallback, args);
        source.getClass().getMethod("sendSuccess", Supplier.class, boolean.class)
            .invoke(source, (Supplier<Object>) () -> component, false);
    }

    private static void sendFailure(Object context, ClassLoader loader, String key, String fallback, Object... args)
        throws ReflectiveOperationException {
        Object source = context.getClass().getMethod("getSource").invoke(context);
        Class<?> componentType = Class.forName("net.minecraft.network.chat.Component", true, loader);
        Object component = component(loader, source, key, fallback, args);
        source.getClass().getMethod("sendFailure", componentType).invoke(source, component);
    }

    private static void sendWarning(Object context, ClassLoader loader, String key, String fallback, Object... args)
        throws ReflectiveOperationException {
        Object source = context.getClass().getMethod("getSource").invoke(context);
        Object component = component(loader, source, key, fallback, args);
        Class<?> mutableType = Class.forName("net.minecraft.network.chat.MutableComponent", true, loader);
        Class<?> formattingType = Class.forName("net.minecraft.ChatFormatting", true, loader);
        Object yellow = formattingType.getField("YELLOW").get(null);
        mutableType.getMethod("withStyle", formattingType).invoke(component, yellow);
        source.getClass().getMethod("sendSuccess", Supplier.class, boolean.class)
            .invoke(source, (Supplier<Object>) () -> component, false);
    }

    private static Object component(ClassLoader loader, Object source, String key, String fallback, Object[] args)
        throws ReflectiveOperationException {
        String language = sourceLanguage(source);
        String localizedFallback = CommandTranslations.fallback(language, key, fallback);
        Class<?> componentType = Class.forName("net.minecraft.network.chat.Component", true, loader);
        return componentType.getMethod("translatableWithFallback", String.class, String.class, Object[].class)
            .invoke(null, key, localizedFallback, args);
    }

    private static String sourceLanguage(Object source) {
        try {
            Object player = source.getClass().getMethod("getPlayer").invoke(source);
            if (player == null) {
                return "en_us";
            }
            Object information = player.getClass().getMethod("clientInformation").invoke(player);
            return (String) information.getClass().getMethod("language").invoke(information);
        } catch (ReflectiveOperationException ignored) {
            return "en_us";
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... approximateParameters)
        throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == approximateParameters.length) {
                boolean matches = true;
                Class<?>[] actual = method.getParameterTypes();
                for (int index = 0; index < actual.length; index++) {
                    if (approximateParameters[index] != Object.class
                        && !actual[index].isAssignableFrom(approximateParameters[index])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "toString" -> "Aerogel " + proxy.getClass().getInterfaces()[0].getSimpleName();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> null;
        };
    }

    @FunctionalInterface
    private interface CommandAction {
        int run(Object context) throws Exception;
    }
}
