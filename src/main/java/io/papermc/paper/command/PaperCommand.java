package io.papermc.paper.command;

import io.papermc.paper.command.subcommands.EntityCommand;
import io.papermc.paper.command.subcommands.FixLightCommand;
import io.papermc.paper.command.subcommands.HeapDumpCommand;
import io.papermc.paper.command.subcommands.ReloadCommand;
import io.papermc.paper.command.subcommands.VersionCommand;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

@DefaultQualifier(NonNull.class)
public final class PaperCommand extends Command {
    static final String BASE_PERM = "bukkit.command.paper.";
    // subcommand label -> subcommand
    private static final Map<String, PaperSubcommand> SUBCOMMANDS = Util.make(() -> {
        final Map<Set<String>, PaperSubcommand> commands = new HashMap<>();

        commands.put(Set.of("heap"), new HeapDumpCommand());
        commands.put(Set.of("entity"), new EntityCommand());
        commands.put(Set.of("reload"), new ReloadCommand());
        commands.put(Set.of("version"), new VersionCommand());
        commands.put(Set.of("fixlight"), new FixLightCommand());

        return commands.entrySet().stream()
            .flatMap(entry -> entry.getKey().stream().map(s -> Map.entry(s, entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    });
    // alias -> subcommand label
    private static final Map<String, String> ALIASES = Util.make(() -> {
        final Map<String, Set<String>> aliases = new HashMap<>();

        aliases.put("version", Set.of("ver"));

        return aliases.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream().map(s -> Map.entry(s, entry.getKey())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    });

    public PaperCommand(final String name) {
        super(name);
        this.description = "Paper related commands";
        this.usageMessage = "/paper [" + String.join(" | ", SUBCOMMANDS.keySet()) + "]";
        final List<String> permissions = new ArrayList<>();
        permissions.add("bukkit.command.paper");
        permissions.addAll(SUBCOMMANDS.keySet().stream().map(s -> BASE_PERM + s).toList());
        this.setPermission(String.join(";", permissions));
        final PluginManager pluginManager = Bukkit.getServer().getPluginManager();
        for (final String perm : permissions) {
            pluginManager.addPermission(new Permission(perm, PermissionDefault.OP));
        }
    }

    private static boolean testPermission(final CommandSender sender, final String permission) {
        if (sender.hasPermission(BASE_PERM + permission) || sender.hasPermission("bukkit.command.paper")) {
            return true;
        }
        sender.sendMessage(text("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", RED));
        return false;
    }

    @Override
    public List<String> tabComplete(
        final CommandSender sender,
        final String alias,
        final String[] args,
        final @Nullable Location location
    ) throws IllegalArgumentException {
        if (args.length <= 1) {
            return CommandUtil.getListMatchingLast(sender, args, SUBCOMMANDS.keySet());
        }

        final @Nullable Pair<String, PaperSubcommand> subCommand = resolveCommand(args[0]);
        if (subCommand != null) {
            return subCommand.second().tabComplete(sender, subCommand.first(), Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }

    @Override
    public boolean execute(
        final CommandSender sender,
        final String commandLabel,
        final String[] args
    ) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(text("Usage: " + this.usageMessage, RED));
            return false;
        }
        final @Nullable Pair<String, PaperSubcommand> subCommand = resolveCommand(args[0]);

        if (subCommand == null) {
            sender.sendMessage(text("Usage: " + this.usageMessage, RED));
            return false;
        }

        if (!testPermission(sender, subCommand.first())) {
            return true;
        }
        final String[] choppedArgs = Arrays.copyOfRange(args, 1, args.length);
        return subCommand.second().execute(sender, subCommand.first(), choppedArgs);
    }

    private static @Nullable Pair<String, PaperSubcommand> resolveCommand(String label) {
        label = label.toLowerCase(Locale.ENGLISH);
        @Nullable PaperSubcommand subCommand = SUBCOMMANDS.get(label);
        if (subCommand == null) {
            final @Nullable String command = ALIASES.get(label);
            if (command != null) {
                label = command;
                subCommand = SUBCOMMANDS.get(command);
            }
        }

        if (subCommand != null) {
            return Pair.of(label, subCommand);
        }

        return null;
    }
}