package dev.kiddo.visualwand.gui;

import java.util.List;

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
        inventory = Bukkit.createInventory(this, 27, Lang.colorize("&8✦ &6VisualWand &8- &fMain Menu"));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Block Display
        inventory.setItem(11, createItem(
            Material.BRICKS,
            "&e✦ Block Display",
            Lang.colorizeList(List.of("&7", "&fCreates a block display.", "&7Perfect for decorations and builds.", "&7", "&aClick to create!"))
        ));
        
        // Item Display
        inventory.setItem(13, createItem(
            Material.DIAMOND,
            "&b✦ Item Display",
            Lang.colorizeList(List.of("&7", "&fCreates an item display.", "&7", "&aClick to create!"))
        ));
        
        // Text Display
        inventory.setItem(15, createItem(
            Material.OAK_SIGN,
            "&a✦ Text Display",
            Lang.colorizeList(List.of("&7", "&fCreates a text display.", "&7Supports colors and formatting.", "&7", "&aClick to create!"))
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
        Location targetLocation = RayTraceUtil.getTargetLocation(
                player,
                plugin.getConfig().getDouble("editor.max-distance", 50)
        );

        Location spawnLocation = targetLocation.getBlock()
                .getLocation()
                .add(0.5D, 0.0D, 0.5D);

        player.getWorld().spawn(spawnLocation, TextDisplay.class, textDisplay -> {
            textDisplay.setText("Click to edit");
            textDisplay.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(128, 0, 0, 0));

        });

        player.sendMessage(Lang.getPrefixed("&aCreated new object: &eText Display"));

        plugin.getEditorManager().startTextInput(player, null);
    }
}
