package dev.kiddo.visualwand.gui;

import java.util.List;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class EditMenuGUI extends BaseGUI {

    private final Display display;

    public EditMenuGUI(VisualWand plugin, Player player, Display display) {
        super(plugin, player);
        this.display = display;
    }

    @Override
    protected void createInventory() {
        String typeName = getDisplayTypeName();
        String title = "&8✦ &6Edit &8- &f" + typeName;
        inventory = Bukkit.createInventory(this, 45, Lang.colorize(title));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Transformations button
        inventory.setItem(11, createItem(
            Material.COMPASS,
            "&6⚙ Transformations",
            Lang.colorizeList(List.of("&7", "&fMove, rotate and scale the object.", "&7", "&eClick to open!"))
        ));
        
        // Animations button
        inventory.setItem(13, createItem(
            Material.ENDER_EYE,
            "&d✿ Animations",
            Lang.colorizeList(List.of("&7", "&fAdd animations to the object.", "&7", "&eClick to open!"))
        ));
        
        // Properties button
        inventory.setItem(15, createItem(
            Material.WRITABLE_BOOK,
            "&b✎ Properties",
            Lang.colorizeList(List.of("&7", "&fChange object properties.", "&7", "&eClick to open!"))
        ));
        
        // Gizmo toggle
        boolean gizmoActive = plugin.getGizmoManager().hasActiveGizmo(player);
        String gizmoStatus = gizmoActive ? 
            "&aEnabled" : 
            "&cDisabled";
        
        inventory.setItem(22, createItem(
            gizmoActive ? Material.GLOWSTONE : Material.REDSTONE_LAMP,
            "&6✦ Toggle Gizmo",
            Lang.colorizeList(List.of("&7", "&fEnables/disables visual gizmo.", "&7Currently: &e{status}", "&7", "&eClick to toggle!")).stream()
                .map(line -> line.replace("{status}", gizmoStatus))
                .toList()
        ));
        
        // Delete button
        inventory.setItem(31, createItem(
            Material.TNT,
            "&c✖ Delete",
            Lang.colorizeList(List.of("&7", "&fDeletes this object permanently!", "&7", "&cClick to delete!"))
        ));
        
        // Back button
        inventory.setItem(36, getBackButton());
        
        // Close button
        inventory.setItem(44, getCloseButton());
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 11 -> {
                // Open transformations menu
                player.closeInventory();
                new TransformMenuGUI(plugin, player, display).open();
            }
            case 13 -> {
                // Open animations menu
                player.closeInventory();
                new AnimationMenuGUI(plugin, player, display).open();
            }
            case 15 -> {
                // Open properties menu
                player.closeInventory();
                new PropertiesMenuGUI(plugin, player, display).open();
            }
            case 22 -> {
                // Toggle gizmo
                if (plugin.getGizmoManager().hasActiveGizmo(player)) {
                    plugin.getGizmoManager().stopGizmo(player);
                } else {
                    plugin.getGizmoManager().startGizmo(player, display);
                }
                // Refresh GUI
                createInventory();
            }
            case 31 -> {
                // Delete display
                player.closeInventory();
                plugin.getAnimationManager().stopAnimation(display);
                plugin.getDisplayStorage().removeDisplay(display);
                plugin.getGizmoManager().stopGizmo(player);
                display.remove();
                player.sendMessage(Lang.getPrefixed("&cDeleted display object!"));
            }
            case 36 -> {
                // Back to main menu
                player.closeInventory();
                new MainMenuGUI(plugin, player).open();
            }
            case 44 -> player.closeInventory();
        }
    }

    private String getDisplayTypeName() {
        if (display instanceof BlockDisplay) {
            return "Block Display";
        } else if (display instanceof ItemDisplay) {
            return "Item Display";
        } else if (display instanceof TextDisplay) {
            return "Text Display";
        }
        return "Display";
    }
}
