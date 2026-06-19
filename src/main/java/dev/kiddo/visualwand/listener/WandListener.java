package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gui.MainMenuGUI;
import dev.kiddo.visualwand.gui.EditMenuGUI;
import dev.kiddo.visualwand.util.RayTraceUtil;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class WandListener implements Listener {

    private final VisualWand plugin;
    private final double maxDistance;

    public WandListener(VisualWand plugin) {
        this.plugin = plugin;
        this.maxDistance = plugin.getConfig().getDouble("editor.max-distance", 50);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        
        // Check if player is holding the wand
        if (!plugin.getWandItem().isWand(player.getInventory().getItemInMainHand())) {
            return;
        }

        // Check permission
        if (!player.hasPermission("visualwand.use")) {
            player.sendMessage(plugin.getLang().getPrefixed("no-permission"));
            return;
        }

        // Handle right-click
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            
            // Check if player is sneaking (shift + right click = delete)
            if (player.isSneaking()) {
                handleDeleteDisplay(player);
                return;
            }

            // Try to find a display entity the player is looking at
            Display targetDisplay = RayTraceUtil.rayTraceDisplay(player, maxDistance);
            
            if (targetDisplay != null) {
                // Open edit menu for existing display
                openEditMenu(player, targetDisplay);
            } else {
                // Open create menu
                openCreateMenu(player);
            }
        }
        
        // Handle left-click for gizmo interaction
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            handleGizmoInteraction(player);
        }
    }

    private void openCreateMenu(Player player) {
        new MainMenuGUI(plugin, player).open();
    }

    private void openEditMenu(Player player, Display display) {
        player.sendMessage(plugin.getLang().getPrefixed("editor-opened", 
            "type", getDisplayTypeName(display)));
        new EditMenuGUI(plugin, player, display).open();
    }

    private void handleDeleteDisplay(Player player) {
        Display targetDisplay = RayTraceUtil.rayTraceDisplay(player, maxDistance);
        
        if (targetDisplay != null) {
            // Stop any animations on this display
            plugin.getAnimationManager().stopAnimation(targetDisplay);
            // Remove from storage
            plugin.getDisplayStorage().removeDisplay(targetDisplay);
            // Remove the entity
            targetDisplay.remove();
            player.sendMessage(plugin.getLang().getPrefixed("display-deleted"));
        } else {
            player.sendMessage(plugin.getLang().getPrefixed("display-not-found"));
        }
    }

    private void handleGizmoInteraction(Player player) {
        // Check if player has an active gizmo session
        if (plugin.getGizmoManager().hasActiveGizmo(player)) {
            plugin.getGizmoManager().handleClick(player);
        }
    }

    private String getDisplayTypeName(Display display) {
        return switch (display) {
            case org.bukkit.entity.BlockDisplay ignored -> "Block Display";
            case org.bukkit.entity.ItemDisplay ignored -> "Item Display";
            case org.bukkit.entity.TextDisplay ignored -> "Text Display";
            default -> "Display";
        };
    }
}
