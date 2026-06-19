package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.animation.AnimationType;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class AnimationMenuGUI extends BaseGUI {

    private final Display display;

    public AnimationMenuGUI(VisualWand plugin, Player player, Display display) {
        super(plugin, player);
        this.display = display;
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 36, Lang.colorize(plugin.getLang().get("gui-animation-title")));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Slow Rotation
        inventory.setItem(11, createItem(
            Material.ENDER_PEARL,
            plugin.getLang().get("gui-anim-slow-rotation"),
            plugin.getLang().getColoredList("gui-anim-slow-rotation-lore")
        ));
        
        // Levitation
        inventory.setItem(13, createItem(
            Material.FEATHER,
            plugin.getLang().get("gui-anim-levitation"),
            plugin.getLang().getColoredList("gui-anim-levitation-lore")
        ));
        
        // Pulsing
        inventory.setItem(15, createItem(
            Material.HEART_OF_THE_SEA,
            plugin.getLang().get("gui-anim-pulsing"),
            plugin.getLang().getColoredList("gui-anim-pulsing-lore")
        ));
        
        // Stop animation
        inventory.setItem(22, createItem(
            Material.BARRIER,
            plugin.getLang().get("gui-anim-stop"),
            plugin.getLang().getColoredList("gui-anim-stop-lore")
        ));
        
        // Back button
        inventory.setItem(27, getBackButton());
        
        // Close button
        inventory.setItem(35, getCloseButton());
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 11 -> {
                // Slow rotation
                plugin.getAnimationManager().startAnimation(display, AnimationType.ROTATION);
                player.sendMessage(plugin.getLang().getPrefixed("display-created", "type", "Powolny Obrót"));
                player.closeInventory();
            }
            case 13 -> {
                // Levitation
                plugin.getAnimationManager().startAnimation(display, AnimationType.LEVITATION);
                player.sendMessage(plugin.getLang().getPrefixed("display-created", "type", "Lewitacja"));
                player.closeInventory();
            }
            case 15 -> {
                // Pulsing
                plugin.getAnimationManager().startAnimation(display, AnimationType.SCALE);
                player.sendMessage(plugin.getLang().getPrefixed("display-created", "type", "Pulsowanie"));
                player.closeInventory();
            }
            case 22 -> {
                // Stop animation
                plugin.getAnimationManager().stopAnimation(display);
                player.sendMessage(plugin.getLang().getPrefixed("editor-saved"));
                player.closeInventory();
            }
            case 27 -> {
                player.closeInventory();
                new EditMenuGUI(plugin, player, display).open();
            }
            case 35 -> player.closeInventory();
        }
    }
}
