package dev.kiddo.visualwand.command;

import dev.kiddo.visualwand.VisualWand;
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
            sender.sendMessage(plugin.getLang().getPrefixed("player-only"));
            return true;
        }

        if (!player.hasPermission("visualwand.give")) {
            player.sendMessage(plugin.getLang().getPrefixed("no-permission"));
            return true;
        }

        player.getInventory().addItem(plugin.getWandItem().getWandItem());
        player.sendMessage(plugin.getLang().getPrefixed("wand-received"));
        
        return true;
    }
}
