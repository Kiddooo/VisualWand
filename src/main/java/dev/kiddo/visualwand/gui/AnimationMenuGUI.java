package dev.kiddo.visualwand.gui;

import java.util.List;

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
        inventory = Bukkit.createInventory(this, 36, Lang.getComponent("&8✦ &6Animations"));
        
        fillBorder();
        
        // Slow Rotation
        inventory.setItem(11, createItem(
            Material.ENDER_PEARL,
            Lang.getComponent("&e✦ Slow Rotation"),
            Lang.getComponents(List.of("&7", "&fObject rotates slowly.", "&7Perfect for trophies and lootboxes.", "&7", "&aClick to apply!"))
        ));
        
        // Levitation
        inventory.setItem(13, createItem(
            Material.FEATHER,
            Lang.getComponent("&b✦ Levitation"),
            Lang.getComponents(List.of("&7", "&fObject floats up and down.", "&7Great effect for signposts.", "&7", "&aClick to apply!"))
        ));
        
        // Pulsing
        inventory.setItem(15, createItem(
            Material.HEART_OF_THE_SEA,
            Lang.getComponent("&d✦ Pulsing"),
            Lang.getComponents(List.of("&7", "&fObject pulses (changes size).", "&7Attracts player attention!", "&7", "&aClick to apply!"))
        ));
        
        // Stop animation
        inventory.setItem(22, createItem(
            Material.BARRIER,
            Lang.getComponent("&c✖ Stop Animation"),
            Lang.getComponents(List.of("&7", "&fStops the current animation.", "&7", "&cClick to stop!"))
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
                player.sendMessage(Lang.getPrefixed("&aApplied animation: &eSlow Rotation"));
                player.closeInventory();
            }
            case 13 -> {
                // Levitation
                plugin.getAnimationManager().startAnimation(display, AnimationType.LEVITATION);
                player.sendMessage(Lang.getPrefixed("&aApplied animation: &eLevitation"));
                player.closeInventory();
            }
            case 15 -> {
                // Pulsing
                plugin.getAnimationManager().startAnimation(display, AnimationType.SCALE);
                player.sendMessage(Lang.getPrefixed("&aApplied animation: &ePulsing"));
                player.closeInventory();
            }
            case 22 -> {
                // Stop animation
                plugin.getAnimationManager().stopAnimation(display);
                player.sendMessage(Lang.getPrefixed("&cAnimation stopped!"));
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
