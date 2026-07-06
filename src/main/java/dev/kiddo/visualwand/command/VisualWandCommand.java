package dev.kiddo.visualwand.command;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VisualWandCommand implements CommandExecutor, TabCompleter {

    private final VisualWand plugin;

    public VisualWandCommand(VisualWand plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
                            @NotNull String label, @NotNull String[] args) {
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "help" -> sendHelp(sender);
            case "reload" -> {
                if (!sender.hasPermission("visualwand.admin")) {
                    sender.sendMessage(Lang.getPrefixed("&cYou don't have permission for this action!"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(Lang.getPrefixed("&aConfiguration reloaded!"));
            }
            case "wand", "give" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Lang.getPrefixed("&cThis command is only available for players!"));
                    return true;
                }
                if (!sender.hasPermission("visualwand.give")) {
                    sender.sendMessage(Lang.getPrefixed("&cYou don't have permission for this action!"));
                    return true;
                }
                player.getInventory().addItem(plugin.getWandItem().getWandItem());
                player.sendMessage(Lang.getPrefixed("&aYou received the &eArchitect's Wand&a!"));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(Lang.colorize("&6&l✦ VisualWand Help ✦"));
        sender.sendMessage("");
        sender.sendMessage(Lang.colorize("&e/vw wand &8- &7Get the Architect's Wand"));
        sender.sendMessage(Lang.colorize("&e/vw reload &8- &7Reload configuration"));
        sender.sendMessage(Lang.colorize("&e/vw help &8- &7Show this help"));
        sender.sendMessage("");
        sender.sendMessage(Lang.colorize("&7Use the &eArchitect's Wand &7to create and edit objects!"));
        sender.sendMessage("");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = List.of("help", "wand", "give", "reload");
            String input = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        }
        
        return completions;
    }
}
