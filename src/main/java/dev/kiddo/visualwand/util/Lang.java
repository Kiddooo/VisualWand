package dev.kiddo.visualwand.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;

import java.util.List;

public class Lang {

    public static final String PREFIX = ChatColor.translateAlternateColorCodes('&', "&8[&6VisualWand&8] ");
    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public static String getPrefixed(String message) {
        return PREFIX + ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static Component getComponent(String text) {
        return legacySerializer.deserialize(text);
    }

    public static List<String> colorizeList(List<String> lore) {
        return lore.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .toList();
    }
}
