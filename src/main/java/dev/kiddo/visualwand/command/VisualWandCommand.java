package dev.kiddo.visualwand.command;

import dev.kiddo.visualwand.VisualWand;
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
                    sender.sendMessage(plugin.getLang().getPrefixed("no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(plugin.getLang().getPrefixed("reload-success"));
            }
            case "lang" -> {
                if (!sender.hasPermission("visualwand.admin")) {
                    sender.sendMessage(plugin.getLang().getPrefixed("no-permission"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLang().getPrefixed("lang-invalid"));
                    return true;
                }
                String langCode = args[1].toLowerCase();
                // Map "ang" to "en" for convenience
                if (langCode.equals("ang")) {
                    langCode = "en";
                }
                if (plugin.getLang().setLanguage(langCode)) {
                    sender.sendMessage(plugin.getLang().getPrefixed("lang-changed"));
                } else {
                    sender.sendMessage(plugin.getLang().getPrefixed("lang-invalid"));
                }
            }
            case "wand", "give" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getLang().getPrefixed("player-only"));
                    return true;
                }
                if (!sender.hasPermission("visualwand.give")) {
                    sender.sendMessage(plugin.getLang().getPrefixed("no-permission"));
                    return true;
                }
                player.getInventory().addItem(plugin.getWandItem().getWandItem());
                player.sendMessage(plugin.getLang().getPrefixed("wand-received"));
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        String lang = plugin.getLang().getCurrentLanguage();
        boolean isPl = lang.equals("pl");
        
        sender.sendMessage("");
        sender.sendMessage(plugin.getLang().getColored("&6&l✦ VisualWand Help ✦"));
        sender.sendMessage("");
        sender.sendMessage(plugin.getLang().getColored("&e/vw wand &8- &7" + 
            (isPl ? "Otrzymaj Różdżkę Architekta" : "Get the Architect's Wand")));
        sender.sendMessage(plugin.getLang().getColored("&e/vw lang <pl/en> &8- &7" + 
            (isPl ? "Zmień język" : "Change language")));
        sender.sendMessage(plugin.getLang().getColored("&e/vw reload &8- &7" + 
            (isPl ? "Przeładuj konfigurację" : "Reload configuration")));
        sender.sendMessage(plugin.getLang().getColored("&e/vw help &8- &7" + 
            (isPl ? "Wyświetl tę pomoc" : "Show this help")));
        sender.sendMessage("");
        sender.sendMessage(plugin.getLang().getColored("&7" + 
            (isPl ? "Użyj &eRóżdżki Architekta &7aby tworzyć i edytować obiekty!" 
                  : "Use the &eArchitect's Wand &7to create and edit objects!")));
        sender.sendMessage("");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = List.of("help", "wand", "give", "lang", "reload");
            String input = args[0].toLowerCase();
            for (String sub : subCommands) {
                if (sub.startsWith(input)) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("lang")) {
            List<String> languages = List.of("pl", "en");
            String input = args[1].toLowerCase();
            for (String lang : languages) {
                if (lang.startsWith(input)) {
                    completions.add(lang);
                }
            }
        }
        
        return completions;
    }
}
