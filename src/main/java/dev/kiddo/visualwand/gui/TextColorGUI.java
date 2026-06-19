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
        new ColorData(Material.WHITE_DYE, "&f", NamedTextColor.WHITE, "White", "Biały"),
        new ColorData(Material.LIGHT_GRAY_DYE, "&7", NamedTextColor.GRAY, "Gray", "Szary"),
        new ColorData(Material.GRAY_DYE, "&8", NamedTextColor.DARK_GRAY, "Dark Gray", "Ciemnoszary"),
        new ColorData(Material.BLACK_DYE, "&0", NamedTextColor.BLACK, "Black", "Czarny"),
        new ColorData(Material.RED_DYE, "&c", NamedTextColor.RED, "Red", "Czerwony"),
        new ColorData(Material.ORANGE_DYE, "&6", NamedTextColor.GOLD, "Gold", "Złoty"),
        new ColorData(Material.YELLOW_DYE, "&e", NamedTextColor.YELLOW, "Yellow", "Żółty"),
        new ColorData(Material.LIME_DYE, "&a", NamedTextColor.GREEN, "Green", "Zielony"),
        new ColorData(Material.GREEN_DYE, "&2", NamedTextColor.DARK_GREEN, "Dark Green", "Ciemnozielony"),
        new ColorData(Material.CYAN_DYE, "&b", NamedTextColor.AQUA, "Aqua", "Cyjan"),
        new ColorData(Material.LIGHT_BLUE_DYE, "&3", NamedTextColor.DARK_AQUA, "Dark Aqua", "Ciemny cyjan"),
        new ColorData(Material.BLUE_DYE, "&9", NamedTextColor.BLUE, "Blue", "Niebieski"),
        new ColorData(Material.PURPLE_DYE, "&5", NamedTextColor.DARK_PURPLE, "Dark Purple", "Ciemnofioletowy"),
        new ColorData(Material.MAGENTA_DYE, "&d", NamedTextColor.LIGHT_PURPLE, "Light Purple", "Jasnofioletowy"),
        new ColorData(Material.PINK_DYE, "&d", NamedTextColor.LIGHT_PURPLE, "Pink", "Różowy"),
        new ColorData(Material.BROWN_DYE, "&4", NamedTextColor.DARK_RED, "Dark Red", "Ciemnoczerwony"),
    };

    public TextColorGUI(VisualWand plugin, Player player, TextDisplay textDisplay) {
        super(plugin, player);
        this.textDisplay = textDisplay;
    }

    @Override
    protected void createInventory() {
        String lang = plugin.getLang().getCurrentLanguage();
        boolean isPl = lang.equals("pl");
        
        String title = isPl ? "&8✦ &6Kolor Tekstu" : "&8✦ &6Text Color";
        inventory = Bukkit.createInventory(this, 45, Lang.colorize(title));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Add color dyes
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29};
        
        for (int i = 0; i < COLORS.length && i < slots.length; i++) {
            ColorData color = COLORS[i];
            String colorName = isPl ? color.namePl : color.nameEn;
            String loreText = isPl ? "&7Kliknij aby ustawić kolor" : "&7Click to set color";
            
            inventory.setItem(slots[i], createItem(
                color.material,
                color.code + "✦ " + colorName,
                "&7",
                loreText,
                "&7",
                "&fPodgląd: " + color.code + "Przykładowy tekst"
            ));
        }
        
        // Text formatting options
        String boldText = isPl ? "&lPogrubienie" : "&lBold";
        String italicText = isPl ? "&oKursywa" : "&oItalic";
        String underlineText = isPl ? "&nPodkreślenie" : "&nUnderline";
        String strikeText = isPl ? "&mPrzekreślenie" : "&mStrikethrough";
        
        inventory.setItem(31, createItem(Material.ANVIL, boldText,
            "&7", isPl ? "&7Przełącz pogrubienie" : "&7Toggle bold"));
        inventory.setItem(32, createItem(Material.FEATHER, italicText,
            "&7", isPl ? "&7Przełącz kursywę" : "&7Toggle italic"));
        inventory.setItem(33, createItem(Material.CHAIN, underlineText,
            "&7", isPl ? "&7Przełącz podkreślenie" : "&7Toggle underline"));
        inventory.setItem(34, createItem(Material.BARRIER, strikeText,
            "&7", isPl ? "&7Przełącz przekreślenie" : "&7Toggle strikethrough"));
        
        // Rainbow gradient option
        String rainbowText = isPl ? "&c&lT&6&lę&e&lc&a&lz&b&la" : "&c&lR&6&la&e&li&a&ln&b&lb&9&lo&d&lw";
        inventory.setItem(30, createItem(Material.PRISMARINE_SHARD, rainbowText,
            "&7",
            isPl ? "&7Zastosuj efekt tęczy" : "&7Apply rainbow effect"));
        
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
        
        String lang = plugin.getLang().getCurrentLanguage();
        player.sendMessage(plugin.getLang().getPrefixed(lang.equals("pl") ? "editor-saved" : "editor-saved"));
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
        
        String lang = plugin.getLang().getCurrentLanguage();
        player.sendMessage(plugin.getLang().getPrefixed(lang.equals("pl") ? "editor-saved" : "editor-saved"));
    }

    private record ColorData(Material material, String code, NamedTextColor textColor, String nameEn, String namePl) {}
}
