package dev.kiddo.visualwand.gui;

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

import java.util.List;
import java.util.UUID;

public class EditMenuGUI extends BaseGUI {

    private final Display display;

    public EditMenuGUI(VisualWand plugin, Player player, Display display) {
        super(plugin, player);
        this.display = display;
    }

    @Override
    public UUID targetDisplayId() {
        return display.getUniqueId();
    }

    @Override
    protected void createInventory() {
        String title = "&8✦ &6Edit &8- &f" + getDisplayTypeName();
        inventory = Bukkit.createInventory(this, 45, Lang.getComponent(title));
        fillBorder();

        inventory.setItem(11, createItem(
                Material.COMPASS,
                Lang.getComponent("&6⚙ Click Transformations"),
                Lang.getComponents(List.of(
                        "&7",
                        "&fChoose one of 15 move, rotation, scale,",
                        "&for entity-orientation modes.",
                        "&7Left click increases; right click decreases.",
                        "&7Drop the wand to reopen the mode menu.",
                        "&7",
                        "&eClick to select this display and open!"))));

        inventory.setItem(13, createItem(
                Material.ENDER_EYE,
                Lang.getComponent("&d✿ Animations"),
                Lang.getComponents(List.of("&7", "&fAdd animations to the object.", "&7", "&eClick to open!"))));

        inventory.setItem(15, createItem(
                Material.WRITABLE_BOOK,
                Lang.getComponent("&b✎ Properties"),
                Lang.getComponents(List.of("&7", "&fChange object properties.", "&7", "&eClick to open!"))));

        inventory.setItem(31, createItem(
                Material.TNT,
                Lang.getComponent("&c✖ Delete"),
                Lang.getComponents(List.of("&7", "&fDeletes this object permanently!", "&7", "&cClick to delete!"))));

        inventory.setItem(36, getBackButton());
        inventory.setItem(44, getCloseButton());
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 11 -> {
                if (plugin.getEditorManager().select(player, display)) {
                    player.closeInventory();
                    new TransformMenuGUI(plugin, player, display).open();
                }
            }
            case 13 -> {
                player.closeInventory();
                new AnimationMenuGUI(plugin, player, display).open();
            }
            case 15 -> {
                player.closeInventory();
                new PropertiesMenuGUI(plugin, player, display).open();
            }
            case 31 -> {
                player.closeInventory();
                plugin.getEditorManager().clearEditorsOf(display.getUniqueId());
                plugin.getAnimationManager().stopAnimation(display);
                display.remove();
                player.sendMessage(Lang.getPrefixed("&cDeleted display object!"));
            }
            case 36 -> {
                player.closeInventory();
                new MainMenuGUI(plugin, player).open();
            }
            case 44 -> player.closeInventory();
            default -> {
            }
        }
    }

    private String getDisplayTypeName() {
        if (display instanceof BlockDisplay) {
            return "Block Display";
        }
        if (display instanceof ItemDisplay) {
            return "Item Display";
        }
        if (display instanceof TextDisplay) {
            return "Text Display";
        }
        return "Display";
    }
}
