package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.editor.EditorSession;
import dev.kiddo.visualwand.gizmo.GizmoManager;
import dev.kiddo.visualwand.gizmo.GizmoMode;
import dev.kiddo.visualwand.gizmo.GizmoSession;
import dev.kiddo.visualwand.gui.MainMenuGUI;
import dev.kiddo.visualwand.gui.EditMenuGUI;
import dev.kiddo.visualwand.util.RayTraceUtil;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WandListener implements Listener {

    private final VisualWand plugin;
    private final double maxDistance;
    private final Map<UUID, Location> originalLocations = new HashMap<>();

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

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR  || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        GizmoManager gizmoManager = plugin.getGizmoManager();
        EditorManager editorManager = plugin.getEditorManager();
        GizmoSession gizmoSession = gizmoManager.getSession(player);
        EditorSession editorSession = editorManager.getSession(player);

        if (editorSession != null && editorSession.isTransforming() && rightClick) {
            event.setCancelled(true);

            if (player.isSneaking()) {
                cancelTransformation(player, editorSession);
            } else {
                confirmTransformation(player, editorSession);
            }
            return;
        }

        if (editorSession != null && editorSession.isTransforming() && leftClick) {
            event.setCancelled(true);
            return;
        }

        if (gizmoSession != null && leftClick) {
            event.setCancelled(true);
            gizmoManager.handleClick(player);
            editorManager.removeSession(player);
            originalLocations.remove(player.getUniqueId());
            return;
        }

        if (gizmoSession != null && action == Action.RIGHT_CLICK_AIR && !player.isSneaking()) {
            event.setCancelled(true);
            startTransformation(player, gizmoSession);
            return;
        }

         // Handle right-click
        if (rightClick) {
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
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        EditorSession editorSession = plugin.getEditorManager().getSession(player);
        if  (editorSession == null || !editorSession.isTransforming()) {
            return;
        }

        Display display = editorSession.getDisplay();
        if (display == null || !display.isValid()) {
            plugin.getEditorManager().removeSession(player);
            originalLocations.remove(player.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        if (Float.compare(from.getYaw(), to.getYaw()) == 0 && Float.compare(from.getPitch(), to.getPitch()) == 0) {
            return;
        }

        editorSession.updateTransformation(from, to);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        EditorSession editorSession = plugin.getEditorManager().getSession(player);
        if (editorSession != null && editorSession.isTransforming()) {
            cancelTransformation(player, editorSession);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        EditorSession editorSession = plugin.getEditorManager().getSession(player);

        if (editorSession != null && editorSession.isTransforming()) {
            cancelTransformation(player, editorSession, false);
        } else {
            plugin.getEditorManager().removeSession(player);
            originalLocations.remove(player.getUniqueId());
        }

        plugin.getGizmoManager().stopGizmo(player);
    }

    public void startTransformation(Player player, GizmoSession gizmoSession) {
        Display display = gizmoSession.getDisplay();
        if (display == null || !display.isValid()) {
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-display-invalid"));
            plugin.getGizmoManager().stopGizmo(player);
            plugin.getEditorManager().removeSession(player);
            originalLocations.remove(player.getUniqueId());
            return;
        }

        EditorManager editorManager = plugin.getEditorManager();
        EditorSession editorSession = editorManager.getSession(player);
        if (editorSession == null || editorSession.getDisplay() != display) {
            editorManager.removeSession(player);
            editorSession = editorManager.createSession(player, display);
        }

        GizmoMode mode = gizmoSession.getMode();
        originalLocations.put(player.getUniqueId(), display.getLocation().clone());
        editorSession.startTransformation(mode);

        player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-started", "mode", getLocalizedModeName(mode)));
    }

    private void confirmTransformation(Player player, EditorSession editorSession) {
        editorSession.confirmTransformation();
        plugin.getEditorManager().removeSession(player);
        originalLocations.remove(player.getUniqueId());
        player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-confirmed"));
    }

    private void cancelTransformation(Player player, EditorSession editorSession) {
        cancelTransformation(player, editorSession, true);
    }

    private void cancelTransformation(
            Player player,
            EditorSession editorSession,
            boolean sendMessage
    ) {
        editorSession.cancelTransformation();

        Location originalLocation = originalLocations.remove(player.getUniqueId());
        Display display = editorSession.getDisplay();
        if (originalLocation != null && display != null && display.isValid()) {
            display.teleport(originalLocation);
        }

        plugin.getEditorManager().removeSession(player);

        if (sendMessage) {
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-cancelled"));
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

    private String getDisplayTypeName(Display display) {
        return switch (display) {
            case org.bukkit.entity.BlockDisplay ignored -> "Block Display";
            case org.bukkit.entity.ItemDisplay ignored -> "Item Display";
            case org.bukkit.entity.TextDisplay ignored -> "Text Display";
            default -> "Display";
        };
    }

    private String getLocalizedModeName(GizmoMode mode) {
        if (mode == GizmoMode.MOVE) {
            return plugin.getLang().get("gizmo-mode-name-move");
        }
        if (mode == GizmoMode.ROTATE) {
            return plugin.getLang().get("gizmo-mode-name-rotate");
        }
        return plugin.getLang().get("gizmo-mode-name-scale");
    }
}
