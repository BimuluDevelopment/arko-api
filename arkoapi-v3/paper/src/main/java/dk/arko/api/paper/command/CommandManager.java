package dk.arko.api.paper.command;

import dk.arko.api.common.text.TextUtil;
import dk.arko.api.paper.command.annotation.*;
import dk.arko.api.paper.command.annotation.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Annotation-based command framework supporting:
 * - @Command, @SubCommand, @Permission, @Cooldown
 * - Automatic argument parsing (Player, int, double, String, UUID, etc.)
 * - Tab completion
 * - Async execution
 * - Cooldowns
 * - Sub-commands with inheritance
 *
 * Usage:
 *   @Command(name = "rank", description = "Rank commands", aliases = {"r"})
 *   public class RankCommand {
 *       @SubCommand(name = "set")
 *       @Permission("rank.set")
 *       public void setRank(Player sender, @Arg("player") Player target, @Arg("rank") String rank) {
 *           // ...
 *       }
 *   }
 *
 *   commandManager.registerCommand(new RankCommand());
 */
public class CommandManager {

    private final JavaPlugin plugin;
    private final Map<String, RegisteredCommand> commands = new ConcurrentHashMap<>();
    private final Map<Class<?>, BiFunction<CommandSender, String, ?>> parsers = new ConcurrentHashMap<>();
    private final dk.arko.api.common.cooldown.CooldownManager cooldownManager = new dk.arko.api.common.cooldown.CooldownManager();

    public CommandManager(JavaPlugin plugin) {
        this.plugin = plugin;
        registerDefaultParsers();
    }

    // ─── Registration ──────────────────────────────────────────

    /**
     * Register an annotated command class.
     */
    public void registerCommand(Object commandObject) {
        Class<?> clazz = commandObject.getClass();
        Command cmdAnnotation = clazz.getAnnotation(Command.class);
        if (cmdAnnotation == null) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " is not annotated with @Command");
        }

        RegisteredCommand registered = new RegisteredCommand(commandObject, cmdAnnotation);

        // Find all methods with @SubCommand or @Default
        for (Method method : clazz.getDeclaredMethods()) {
            method.setAccessible(true);

            if (method.isAnnotationPresent(Default.class)) {
                registered.defaultMethod = method;
            }

            SubCommand subCmd = method.getAnnotation(SubCommand.class);
            if (subCmd != null) {
                registered.subCommands.put(subCmd.name().toLowerCase(), new SubCommandEntry(method, subCmd));
            }
        }

        // Register with Bukkit's command map
        BukkitCommand bukkitCommand = new BukkitCommand(cmdAnnotation.name()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                handleCommand(registered, sender, args);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return handleTabComplete(registered, sender, args);
            }
        };
        bukkitCommand.setDescription(cmdAnnotation.description());
        bukkitCommand.setAliases(Arrays.asList(cmdAnnotation.aliases()));

        if (!cmdAnnotation.permission().isEmpty()) {
            bukkitCommand.setPermission(cmdAnnotation.permission());
        }

        getCommandMap().register(plugin.getName().toLowerCase(), bukkitCommand);
        commands.put(cmdAnnotation.name().toLowerCase(), registered);
    }

    /**
     * Register a custom argument parser.
     */
    public <T> void registerParser(Class<T> type, BiFunction<CommandSender, String, T> parser) {
        parsers.put(type, parser);
    }

    // ─── Command Execution ─────────────────────────────────────

    private void handleCommand(RegisteredCommand cmd, CommandSender sender, String[] args) {
        // Permission check
        if (!cmd.annotation.permission().isEmpty() && !sender.hasPermission(cmd.annotation.permission())) {
            sender.sendMessage(TextUtil.parse("<red>Du har ikke tilladelse til denne kommando."));
            return;
        }

        // Player-only check
        if (cmd.annotation.playerOnly() && !(sender instanceof Player)) {
            sender.sendMessage(TextUtil.parse("<red>Denne kommando kan kun bruges af spillere."));
            return;
        }

        // Find sub-command or default
        Method method;
        String[] methodArgs;

        if (args.length > 0 && cmd.subCommands.containsKey(args[0].toLowerCase())) {
            SubCommandEntry sub = cmd.subCommands.get(args[0].toLowerCase());
            method = sub.method;
            methodArgs = Arrays.copyOfRange(args, 1, args.length);

            // Sub-command permission
            Permission perm = method.getAnnotation(Permission.class);
            if (perm != null && !sender.hasPermission(perm.value())) {
                sender.sendMessage(TextUtil.parse("<red>Du har ikke tilladelse til denne underkommando."));
                return;
            }

            // Sub-command player-only
            if (sub.annotation.playerOnly() && !(sender instanceof Player)) {
                sender.sendMessage(TextUtil.parse("<red>Denne underkommando kan kun bruges af spillere."));
                return;
            }
        } else if (cmd.defaultMethod != null) {
            method = cmd.defaultMethod;
            methodArgs = args;
        } else {
            // Show usage / sub-command list
            sendUsage(sender, cmd);
            return;
        }

        // Cooldown check
        Cooldown cooldown = method.getAnnotation(Cooldown.class);
        if (cooldown != null && sender instanceof Player player) {
            String cooldownKey = cmd.annotation.name() + "." + method.getName();
            if (!cooldownManager.tryUse(player.getUniqueId(), cooldownKey,
                    java.time.Duration.ofMillis(cooldown.value()))) {
                long remaining = cooldownManager.getRemainingSeconds(player.getUniqueId(), cooldownKey);
                sender.sendMessage(TextUtil.parse("<red>Vent " + remaining + " sekunder."));
                return;
            }
        }

        // Parse arguments and invoke
        try {
            Object[] parsedArgs = parseArguments(method, sender, methodArgs);
            if (parsedArgs == null) return; // Parsing failed, error sent

            Async asyncAnnotation = method.getAnnotation(Async.class);
            if (asyncAnnotation != null) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try { method.invoke(cmd.instance, parsedArgs); }
                    catch (Exception e) { e.printStackTrace(); }
                });
            } else {
                method.invoke(cmd.instance, parsedArgs);
            }
        } catch (Exception e) {
            sender.sendMessage(TextUtil.parse("<red>Der opstod en fejl ved udførelse af kommandoen."));
            e.printStackTrace();
        }
    }

    private Object[] parseArguments(Method method, CommandSender sender, String[] args) {
        Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        int argIndex = 0;

        for (int i = 0; i < params.length; i++) {
            Parameter param = params[i];
            Class<?> type = param.getType();

            // First param: sender
            if (i == 0 && (CommandSender.class.isAssignableFrom(type))) {
                result[i] = sender;
                continue;
            }

            // Check for @Arg annotation
            Arg argAnnotation = param.getAnnotation(Arg.class);
            boolean optional = param.isAnnotationPresent(Optional.class) ||
                    (argAnnotation != null && !argAnnotation.defaultValue().isEmpty());

            if (argIndex >= args.length) {
                if (optional) {
                    if (argAnnotation != null && !argAnnotation.defaultValue().isEmpty()) {
                        result[i] = parseValue(type, sender, argAnnotation.defaultValue());
                    } else {
                        result[i] = null;
                    }
                } else {
                    sender.sendMessage(TextUtil.parse("<red>Mangler argument: <yellow>" +
                            (argAnnotation != null ? argAnnotation.value() : param.getName())));
                    return null;
                }
                continue;
            }

            // Varargs / remaining string
            if (type == String.class && param.isAnnotationPresent(Remaining.class)) {
                result[i] = String.join(" ", Arrays.copyOfRange(args, argIndex, args.length));
                argIndex = args.length;
                continue;
            }

            // Parse the argument
            Object parsed = parseValue(type, sender, args[argIndex]);
            if (parsed == null && !optional) {
                sender.sendMessage(TextUtil.parse("<red>Ugyldigt argument: <yellow>" + args[argIndex]));
                return null;
            }
            result[i] = parsed;
            argIndex++;
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Object parseValue(Class<?> type, CommandSender sender, String value) {
        BiFunction<CommandSender, String, ?> parser = parsers.get(type);
        if (parser != null) return parser.apply(sender, value);
        if (type == String.class) return value;
        return null;
    }

    // ─── Tab Completion ────────────────────────────────────────

    private List<String> handleTabComplete(RegisteredCommand cmd, CommandSender sender, String[] args) {
        if (args.length == 1) {
            // Complete sub-command names
            List<String> completions = new ArrayList<>(cmd.subCommands.keySet());
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // If in a sub-command, check for @TabComplete on specific args
        if (args.length > 1 && cmd.subCommands.containsKey(args[0].toLowerCase())) {
            SubCommandEntry sub = cmd.subCommands.get(args[0].toLowerCase());
            Method method = sub.method;
            Parameter[] params = method.getParameters();
            int paramIndex = args.length - 1; // -1 for subcommand name, +1 for sender offset adjusts to args.length -1

            if (paramIndex < params.length) {
                Parameter param = params[paramIndex];
                Class<?> type = param.getType();

                // Online player names
                if (type == Player.class || type == org.bukkit.OfflinePlayer.class) {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList());
                }

                // Check for @TabComplete values
                TabComplete tabAnnotation = param.getAnnotation(TabComplete.class);
                if (tabAnnotation != null) {
                    return Arrays.stream(tabAnnotation.value())
                            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                            .sorted()
                            .collect(Collectors.toList());
                }
            }
        }

        return List.of();
    }

    // ─── Usage ─────────────────────────────────────────────────

    private void sendUsage(CommandSender sender, RegisteredCommand cmd) {
        sender.sendMessage(TextUtil.parse("<gold>/" + cmd.annotation.name() + " kommandoer:"));
        cmd.subCommands.forEach((name, entry) -> {
            String desc = entry.annotation.description().isEmpty() ? "" : " <gray>- " + entry.annotation.description();
            sender.sendMessage(TextUtil.parse("  <yellow>/" + cmd.annotation.name() + " " + name + desc));
        });
    }

    // ─── Default Parsers ───────────────────────────────────────

    private void registerDefaultParsers() {
        parsers.put(int.class, (s, v) -> { try { return Integer.parseInt(v); } catch (Exception e) { return null; } });
        parsers.put(Integer.class, (s, v) -> { try { return Integer.parseInt(v); } catch (Exception e) { return null; } });
        parsers.put(long.class, (s, v) -> { try { return Long.parseLong(v); } catch (Exception e) { return null; } });
        parsers.put(Long.class, (s, v) -> { try { return Long.parseLong(v); } catch (Exception e) { return null; } });
        parsers.put(double.class, (s, v) -> { try { return Double.parseDouble(v); } catch (Exception e) { return null; } });
        parsers.put(Double.class, (s, v) -> { try { return Double.parseDouble(v); } catch (Exception e) { return null; } });
        parsers.put(float.class, (s, v) -> { try { return Float.parseFloat(v); } catch (Exception e) { return null; } });
        parsers.put(boolean.class, (s, v) -> v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1"));
        parsers.put(Boolean.class, (s, v) -> v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes") || v.equals("1"));
        parsers.put(Player.class, (s, v) -> Bukkit.getPlayerExact(v));
        parsers.put(UUID.class, (s, v) -> { try { return UUID.fromString(v); } catch (Exception e) { return null; } });
        parsers.put(org.bukkit.OfflinePlayer.class, (s, v) -> Bukkit.getOfflinePlayer(v));
    }

    private CommandMap getCommandMap() {
        return Bukkit.getCommandMap();
    }

    // ─── Inner Classes ─────────────────────────────────────────

    private static class RegisteredCommand {
        final Object instance;
        final Command annotation;
        Method defaultMethod;
        final Map<String, SubCommandEntry> subCommands = new LinkedHashMap<>();

        RegisteredCommand(Object instance, Command annotation) {
            this.instance = instance;
            this.annotation = annotation;
        }
    }

    private record SubCommandEntry(Method method, SubCommand annotation) {}
}
