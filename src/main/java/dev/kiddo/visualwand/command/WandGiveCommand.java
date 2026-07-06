package dev.kiddo.visualwand.command;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WandGiveCommand implements CommandExecutor {

    private final VisualWand plugin;

    public WandGiveCommand(VisualWand plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.getPrefixed("&cThis command is only available for players!"));
            return true;
        }

        if (!player.hasPermission("visualwand.give")) {
            player.sendMessage(Lang.getPrefixed("&cYou don't have permission for this action!"));
            return true;
        }

        player.getInventory().addItem(plugin.getWandItem().getWandItem());
        player.sendMessage(Lang.getPrefixed("&aYou received the &eArchitect's Wand&a!"));
        
        return true;
    }
}
