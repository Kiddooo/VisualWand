package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class TextColorGUI extends BaseGUI {

    private final TextDisplay textDisplay;

    // Color data: Material, color code, NamedTextColor
    private static final ColorData[] COLORS = {
        new ColorData(Material.WHITE_DYE, "&f", NamedTextColor.WHITE, "White"),
        new ColorData(Material.LIGHT_GRAY_DYE, "&7", NamedTextColor.GRAY, "Gray"),
        new ColorData(Material.GRAY_DYE, "&8", NamedTextColor.DARK_GRAY, "Dark Gray"),
        new ColorData(Material.BLACK_DYE, "&0", NamedTextColor.BLACK, "Black"),
        new ColorData(Material.RED_DYE, "&c", NamedTextColor.RED, "Red"),
        new ColorData(Material.ORANGE_DYE, "&6", NamedTextColor.GOLD, "Gold"),
        new ColorData(Material.YELLOW_DYE, "&e", NamedTextColor.YELLOW, "Yellow"),
        new ColorData(Material.LIME_DYE, "&a", NamedTextColor.GREEN, "Green"),
        new ColorData(Material.GREEN_DYE, "&2", NamedTextColor.DARK_GREEN, "Dark Green"),
        new ColorData(Material.CYAN_DYE, "&b", NamedTextColor.AQUA, "Aqua"),
        new ColorData(Material.LIGHT_BLUE_DYE, "&3", NamedTextColor.DARK_AQUA, "Dark Aqua"),
        new ColorData(Material.BLUE_DYE, "&9", NamedTextColor.BLUE, "Blue"),
        new ColorData(Material.PURPLE_DYE, "&5", NamedTextColor.DARK_PURPLE, "Dark Purple"),
        new ColorData(Material.MAGENTA_DYE, "&d", NamedTextColor.LIGHT_PURPLE, "Light Purple"),
        new ColorData(Material.PINK_DYE, "&d", NamedTextColor.LIGHT_PURPLE, "Pink"),
        new ColorData(Material.BROWN_DYE, "&4", NamedTextColor.DARK_RED, "Dark Red"),
    };

    public TextColorGUI(VisualWand plugin, Player player, TextDisplay textDisplay) {
        super(plugin, player);
        this.textDisplay = textDisplay;
    }

    @Override
    protected void createInventory() {
        
        
        String title = "&8✦ &6Text Color";
        inventory = Bukkit.createInventory(this, 45, Lang.getComponent(title));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Add color dyes
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};
        
        for (int i = 0; i < COLORS.length && i < slots.length; i++) {
            ColorData color = COLORS[i];
            String colorName = color.name;
            String loreText = "&7Click to set color";
            
            inventory.setItem(slots[i], createItem(
                color.material,
                Lang.getComponent(color.code + "✦ " + colorName),
                Lang.getComponent("&7"),
                Lang.getComponent(loreText),
                Lang.getComponent("&7"),
                Lang.getComponent("&fPreview: " + color.code + "Sample text")
            ));
        }
        
        // Text formatting options
        String boldText = "&lBold";
        String italicText = "&oItalic";
        String underlineText = "&nUnderline";
        String strikeText = "&mStrikethrough";
        
        inventory.setItem(31, createItem(Material.ANVIL, Lang.getComponent(boldText),
            Lang.getComponent("&7"), Lang.getComponent("&7Toggle bold")));
        inventory.setItem(32, createItem(Material.FEATHER, Lang.getComponent(italicText),
            Lang.getComponent("&7"), Lang.getComponent("&7Toggle italic")));
        inventory.setItem(33, createItem(Material.IRON_CHAIN, Lang.getComponent(underlineText),
            Lang.getComponent("&7"), Lang.getComponent("&7Toggle underline")));
        inventory.setItem(34, createItem(Material.BARRIER, Lang.getComponent(strikeText),
            Lang.getComponent("&7"), Lang.getComponent("&7Toggle strikethrough")));
        
        // Rainbow gradient option
        String rainbowText = "&c&lR&6&la&e&li&a&ln&b&lb&9&lo&d&lw";
        inventory.setItem(30, createItem(Material.PRISMARINE_SHARD, Lang.getComponent(rainbowText),
            Lang.getComponent("&7"),
            Lang.getComponent("&7Apply rainbow effect")));
        
        // Back button
        inventory.setItem(36, getBackButton());
        
        // Close button
        inventory.setItem(44, getCloseButton());
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        // Color slots
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};
        
        for (int i = 0; i < slots.length && i < COLORS.length; i++) {
            if (slot == slots[i]) {
                applyColor(COLORS[i].textColor);
                return;
            }
        }
        
        switch (slot) {
            case 30 -> applyRainbow();
            case 31 -> toggleDecoration(TextDecoration.BOLD);
            case 32 -> toggleDecoration(TextDecoration.ITALIC);
            case 33 -> toggleDecoration(TextDecoration.UNDERLINED);
            case 34 -> toggleDecoration(TextDecoration.STRIKETHROUGH);
            case 36 -> {
                player.closeInventory();
                new PropertiesMenuGUI(plugin, player, textDisplay).open();
            }
            case 44 -> player.closeInventory();
        }
    }

    private void applyColor(NamedTextColor color) {
        Component currentText = textDisplay.text();
        if (currentText == null) {
            currentText = Component.text("Text");
        }
        
        String plainText = PlainTextComponentSerializer.plainText().serialize(currentText);
        Component newText = Component.text(plainText).color(color);
        
        textDisplay.text(newText);
        
        player.sendMessage(Lang.getPrefixed("&aChanges saved!"));
    }

    private void toggleDecoration(TextDecoration decoration) {
        Component currentText = textDisplay.text();
        if (currentText == null) {
            currentText = Component.text("Text");
        }
        
        String plainText = PlainTextComponentSerializer.plainText().serialize(currentText);
        TextColor currentColor = currentText.color();
        
        // Check if decoration is currently applied
        TextDecoration.State currentState = currentText.decoration(decoration);
        boolean isApplied = currentState == TextDecoration.State.TRUE;
        
        Component newText = Component.text(plainText)
            .color(currentColor)
            .decoration(decoration, !isApplied);
        
        textDisplay.text(newText);
        createInventory(); // Refresh GUI
    }

    private void applyRainbow() {
        Component currentText = textDisplay.text();
        if (currentText == null) {
            currentText = Component.text("Text");
        }
        
        String plainText = PlainTextComponentSerializer.plainText().serialize(currentText);
        
        // Create rainbow text
        NamedTextColor[] rainbowColors = {
            NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW,
            NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.BLUE,
            NamedTextColor.LIGHT_PURPLE
        };
        
        Component rainbowText = Component.empty();
        for (int i = 0; i < plainText.length(); i++) {
            NamedTextColor color = rainbowColors[i % rainbowColors.length];
            rainbowText = rainbowText.append(
                Component.text(String.valueOf(plainText.charAt(i))).color(color)
            );
        }
        
        textDisplay.text(rainbowText);
        
        player.sendMessage(Lang.getPrefixed("&aChanges saved!"));
    }

    private record ColorData(Material material, String code, NamedTextColor textColor, String name) {}
}
