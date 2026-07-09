package dev.kiddo.visualwand.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;

public class Lang {

    private static final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private static final Component PREFIX = legacySerializer.deserialize("&8[&6VisualWand&8] ");

    public static Component getPrefixed(String message) {
        return PREFIX.append(legacySerializer.deserialize(message));
    }

    public static Component getComponent(String text) {
        return legacySerializer.deserialize(text);
    }

    public static List<Component> getComponents(List<String> lore) {
        return lore.stream()
                .<Component>map(legacySerializer::deserialize)
                .toList();
    }
}
