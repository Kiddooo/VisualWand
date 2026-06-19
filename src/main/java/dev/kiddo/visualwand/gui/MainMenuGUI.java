package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import dev.kiddo.visualwand.util.RayTraceUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class MainMenuGUI extends BaseGUI {

    public MainMenuGUI(VisualWand plugin, Player player) {
        super(plugin, player);
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 27, Lang.colorize(plugin.getLang().get("gui-main-title")));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Block Display
        inventory.setItem(11, createItem(
            Material.BRICKS,
            plugin.getLang().get("gui-create-block"),
            plugin.getLang().getColoredList("gui-create-block-lore")
        ));
        
        // Item Display
        inventory.setItem(13, createItem(
            Material.DIAMOND,
            plugin.getLang().get("gui-create-item"),
            plugin.getLang().getColoredList("gui-create-item-lore")
        ));
        
        // Text Display
        inventory.setItem(15, createItem(
            Material.OAK_SIGN,
            plugin.getLang().get("gui-create-text"),
            plugin.getLang().getColoredList("gui-create-text-lore")
        ));
        
        // Close button
        inventory.setItem(22, getCloseButton());
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 11 -> {
                player.closeInventory();
                new BlockSelectGUI(plugin, player).open();
            }
            case 13 -> {
                player.closeInventory();
                new ItemSelectGUI(plugin, player).open();
            }
            case 15 -> {
                player.closeInventory();
                createTextDisplay();
            }
            case 22 -> player.closeInventory();
        }
    }

    private void createTextDisplay() {
        Location location = RayTraceUtil.getTargetLocation(player, 
            plugin.getConfig().getDouble("editor.max-distance", 50));
        
        player.getWorld().spawn(location, TextDisplay.class, textDisplay -> {
            textDisplay.setText(plugin.getLang().get("text-display-default"));
            textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(128, 0, 0, 0));
            
            // Register in storage
            plugin.getDisplayStorage().addDisplay(textDisplay);
        });
        
        player.sendMessage(plugin.getLang().getPrefixed("display-created", "type", "Text Display"));
        
        // Open text input
        plugin.getEditorManager().startTextInput(player, null);
    }
}
