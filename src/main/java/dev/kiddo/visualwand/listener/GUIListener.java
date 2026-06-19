package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gui.BaseGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    private final VisualWand plugin;

    public GUIListener(VisualWand plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof BaseGUI gui) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() != null) {
                gui.handleClick(event.getSlot(), event.getCurrentItem(), event.getClick());
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof BaseGUI gui) {
            gui.handleClose();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        
        // Check if player is in text input mode
        if (plugin.getEditorManager().isAwaitingInput(player)) {
            event.setCancelled(true);
            String message = event.getMessage();
            
            // Handle the input on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getEditorManager().handleChatInput(player, message);
            });
        }
    }
}
