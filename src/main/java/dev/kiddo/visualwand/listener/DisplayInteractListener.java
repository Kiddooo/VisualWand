package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditorSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class DisplayInteractListener implements Listener {

    private final VisualWand plugin;

    public DisplayInteractListener(VisualWand plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        EditorSession session = plugin.getEditorManager().getSession(player);
        
        if (session != null && session.isTransforming()) {
            // Update transformation based on mouse movement
            session.updateTransformation(event.getFrom(), event.getTo());
        }
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        EditorSession session = plugin.getEditorManager().getSession(player);
        
        if (session != null && event.isSneaking()) {
            // Cancel current transformation
            session.cancelTransformation();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        // Clean up editor session
        plugin.getEditorManager().removeSession(player);
        
        // Clean up gizmo
        plugin.getGizmoManager().stopGizmo(player);
    }
}
