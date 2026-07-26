package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gui.BaseGUI;
import dev.kiddo.visualwand.gui.TransformMenuGUI;
import dev.kiddo.visualwand.util.Lang;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    private final VisualWand plugin;

    public GUIListener(VisualWand plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof BaseGUI gui) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }
            
            if (event.getCurrentItem() != null) {
                if (blocksLockedInteraction(gui, event.getSlot(), (Player) event.getWhoClicked())) {
                    return;
                }
                gui.handleClick(event.getSlot(), event.getCurrentItem(), event.getClick());
            }
        }
    }

    private boolean blocksLockedInteraction(BaseGUI gui, int slot, Player player) {
        if (gui.targetDisplayId() == null || gui.allowsLockedClick(slot)) {
            return false;
        }

        Entity resolved = plugin.getServer().getEntity(gui.targetDisplayId());
        if (!(resolved instanceof Display display)
                || !plugin.getEditorManager().isLocked(display)) {
            return false;
        }

        player.sendMessage(Lang.getPrefixed(
                "&eThis display is locked. Use the redstone torch to unlock it."));
        if (!(gui instanceof TransformMenuGUI)) {
            new TransformMenuGUI(plugin, player, display).open();
        }
        return true;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        
        if (holder instanceof BaseGUI gui) {
            gui.handleClose();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Check if player is in text input mode
        if (plugin.getEditorManager().isAwaitingInput(player)) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.message());

            // Handle the input on the main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getEditorManager().handleChatInput(player, message));
        }
    }
}
