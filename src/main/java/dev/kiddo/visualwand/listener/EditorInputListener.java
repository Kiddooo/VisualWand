package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditDirection;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.editor.EditorSession;
import dev.kiddo.visualwand.gui.TransformMenuGUI;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/**
 * Routes vanilla main-hand clicks into active editor modes before normal wand controls.
 */
public final class EditorInputListener implements Listener {

    private final VisualWand plugin;
    private final EditorManager manager;

    public EditorInputListener(VisualWand plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.manager = plugin.getEditorManager();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        EditDirection direction = switch (event.getAction()) {
            case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> EditDirection.POSITIVE;
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> EditDirection.NEGATIVE;
            default -> null;
        };
        if (direction == null || !manager.applyInput(event.getPlayer(), direction)) {
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (manager.applyInput(player, EditDirection.POSITIVE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        consumeEntityInteraction(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        consumeEntityInteraction(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent event) {
        if (manager.shouldConsume(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (manager.shouldConsume(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropWand(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getWandItem().isWand(event.getItemDrop().getItemStack())) {
            return;
        }

        EditorSession selected = manager.session(player);
        if (selected == null) {
            return;
        }
        Display display = manager.selectedDisplay(player);
        if (display == null) {
            return;
        }

        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Display current = manager.selectedDisplay(player);
            if (current != null) {
                new TransformMenuGUI(plugin, player, current).open();
            }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        manager.clear(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (manager.session(player) != null) {
            manager.clear(player, "Display editing cleared after changing worlds.");
        }
    }

    private void consumeEntityInteraction(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (manager.applyInput(event.getPlayer(), EditDirection.NEGATIVE)) {
            event.setCancelled(true);
        }
    }
}
