package dk.arko.api.velocity.command;

import com.velocitypowered.api.command.*;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dk.arko.api.common.text.TextUtil;
import dk.arko.api.velocity.command.annotation.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Annotation-based command manager for Velocity.
 * Mirrors the Paper command system for consistent API across platforms.
 *
 * Usage:
 *   @VCommand(name = "server", aliases = {"sv"})
 *   public class ServerCommand {
 *       @VDefault
 *       public void defaultCmd(Player sender) { ... }
 *
 *       @VSubCommand(name = "list")
 *       public void listServers(CommandSource sender) { ... }
 *   }
 */
public class VelocityCommandManager {

    private final ProxyServer proxy;
    private final Object plugin;
    private final Map<Class<?>, BiFunction<CommandSource, String, ?>> parsers = new HashMap<>();

    public VelocityCommandManager(ProxyServer proxy, Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
        registerDefaultParsers();
    }

    public void registerCommand(Object commandObject) {
        Class<?> clazz = commandObject.getClass();
        VCommand cmdAnnotation = clazz.getAnnotation(VCommand.class);
        if (cmdAnnotation == null) throw new IllegalArgumentException("Missing @VCommand on " + clazz.getName());

        Map<String, Method> subCommands = new LinkedHashMap<>();
        Method defaultMethod = null;

        for (Method method : clazz.getDeclaredMethods()) {
            method.setAccessible(true);
            if (method.isAnnotationPresent(VDefault.class)) defaultMethod = method;
            VSubCommand sub = method.getAnnotation(VSubCommand.class);
            if (sub != null) subCommands.put(sub.name().toLowerCase(), method);
        }

        final Method finalDefault = defaultMethod;

        SimpleCommand simpleCommand = new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                CommandSource source = invocation.source();
                String[] args = invocation.arguments();

                // Permission check
                if (!cmdAnnotation.permission().isEmpty() && !source.hasPermission(cmdAnnotation.permission())) {
                    source.sendMessage(TextUtil.parse("<red>Du har ikke tilladelse til denne kommando."));
                    return;
                }

                // Player-only check
                if (cmdAnnotation.playerOnly() && !(source instanceof Player)) {
                    source.sendMessage(TextUtil.parse("<red>Denne kommando kan kun bruges af spillere."));
                    return;
                }

                Method method;
                String[] methodArgs;

                if (args.length > 0 && subCommands.containsKey(args[0].toLowerCase())) {
                    method = subCommands.get(args[0].toLowerCase());
                    methodArgs = Arrays.copyOfRange(args, 1, args.length);

                    // Sub-command permission
                    VPermission perm = method.getAnnotation(VPermission.class);
                    if (perm != null && !source.hasPermission(perm.value())) {
                        source.sendMessage(TextUtil.parse("<red>Du har ikke tilladelse."));
                        return;
                    }
                } else if (finalDefault != null) {
                    method = finalDefault;
                    methodArgs = args;
                } else {
                    source.sendMessage(TextUtil.parse("<gold>/" + cmdAnnotation.name() + " underkommandoer:"));
                    subCommands.forEach((name, m) -> {
                        VSubCommand sub = m.getAnnotation(VSubCommand.class);
                        String desc = sub != null && !sub.description().isEmpty() ? " <gray>- " + sub.description() : "";
                        source.sendMessage(TextUtil.parse("  <yellow>/" + cmdAnnotation.name() + " " + name + desc));
                    });
                    return;
                }

                try {
                    Object[] parsedArgs = parseArguments(method, source, methodArgs);
                    if (parsedArgs != null) method.invoke(commandObject, parsedArgs);
                } catch (Exception e) {
                    source.sendMessage(TextUtil.parse("<red>Der opstod en fejl."));
                    e.printStackTrace();
                }
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    String prefix = args.length == 1 ? args[0].toLowerCase() : "";
                    return CompletableFuture.completedFuture(
                            subCommands.keySet().stream()
                                    .filter(s -> s.startsWith(prefix))
                                    .sorted().collect(Collectors.toList()));
                }
                // Player name completion for Player-type args
                if (args.length > 1 && subCommands.containsKey(args[0].toLowerCase())) {
                    Method method = subCommands.get(args[0].toLowerCase());
                    Parameter[] params = method.getParameters();
                    int paramIdx = args.length - 1;
                    if (paramIdx < params.length && Player.class.isAssignableFrom(params[paramIdx].getType())) {
                        String prefix = args[args.length - 1].toLowerCase();
                        return CompletableFuture.completedFuture(
                                proxy.getAllPlayers().stream()
                                        .map(Player::getUsername)
                                        .filter(n -> n.toLowerCase().startsWith(prefix))
                                        .sorted().collect(Collectors.toList()));
                    }
                }
                return CompletableFuture.completedFuture(List.of());
            }
        };

        CommandMeta meta = proxy.getCommandManager().metaBuilder(cmdAnnotation.name())
                .aliases(cmdAnnotation.aliases())
                .plugin(plugin)
                .build();
        proxy.getCommandManager().register(meta, simpleCommand);
    }

    public <T> void registerParser(Class<T> type, BiFunction<CommandSource, String, T> parser) {
        parsers.put(type, parser);
    }

    private Object[] parseArguments(Method method, CommandSource source, String[] args) {
        Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        int argIndex = 0;

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (i == 0 && CommandSource.class.isAssignableFrom(type)) {
                result[i] = source;
                continue;
            }
            if (argIndex >= args.length) {
                result[i] = null;
                continue;
            }
            BiFunction<CommandSource, String, ?> parser = parsers.get(type);
            result[i] = parser != null ? parser.apply(source, args[argIndex]) : args[argIndex];
            argIndex++;
        }
        return result;
    }

    private void registerDefaultParsers() {
        parsers.put(String.class, (s, v) -> v);
        parsers.put(int.class, (s, v) -> { try { return Integer.parseInt(v); } catch (Exception e) { return 0; } });
        parsers.put(Integer.class, (s, v) -> { try { return Integer.parseInt(v); } catch (Exception e) { return null; } });
        parsers.put(long.class, (s, v) -> { try { return Long.parseLong(v); } catch (Exception e) { return 0L; } });
        parsers.put(double.class, (s, v) -> { try { return Double.parseDouble(v); } catch (Exception e) { return 0.0; } });
        parsers.put(boolean.class, (s, v) -> v.equalsIgnoreCase("true") || v.equals("1"));
        parsers.put(Player.class, (s, v) -> proxy.getPlayer(v).orElse(null));
        parsers.put(UUID.class, (s, v) -> { try { return UUID.fromString(v); } catch (Exception e) { return null; } });
    }
}
