package dev.kiddo.visualwand.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import dev.kiddo.visualwand.VisualWand;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lang {

    private final VisualWand plugin;
    private FileConfiguration langConfig;
    private final Map<String, String> messages = new HashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private String currentLanguage;

    public Lang(VisualWand plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        messages.clear();
        
        String language = plugin.getConfig().getString("language", "en");
        this.currentLanguage = language;
        File langFile = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + language + ".yml", false);
        }
        
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("lang/" + language + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defaultConfig);
        }
        
        // Cache all messages
        for (String key : langConfig.getKeys(true)) {
            if (langConfig.isString(key)) {
                messages.put(key, langConfig.getString(key));
            }
        }
    }

    public String get(String key) {
        return messages.getOrDefault(key, key);
    }

    public String get(String key, Object... replacements) {
        String message = get(key);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
            }
        }
        return message;
    }

    public String getColored(String key) {
        return ChatColor.translateAlternateColorCodes('&', get(key));
    }

    public String getColored(String key, Object... replacements) {
        return ChatColor.translateAlternateColorCodes('&', get(key, replacements));
    }

    public Component getComponent(String key) {
        return legacySerializer.deserialize(get(key));
    }

    public Component getComponent(String key, Object... replacements) {
        return legacySerializer.deserialize(get(key, replacements));
    }

    public String getPrefix() {
        return getColored("prefix");
    }

    public String getPrefixed(String key) {
        return getPrefix() + getColored(key);
    }

    public String getPrefixed(String key, Object... replacements) {
        return getPrefix() + getColored(key, replacements);
    }

    public List<String> getList(String key) {
        return langConfig.getStringList(key);
    }

    public List<String> getColoredList(String key) {
        return getList(key).stream()
            .map(line -> ChatColor.translateAlternateColorCodes('&', line))
            .toList();
    }

    public static String colorize(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public boolean setLanguage(String language) {
        if (!language.equals("pl") && !language.equals("en")) {
            return false;
        }
        
        plugin.getConfig().set("language", language);
        plugin.saveConfig();
        reload();
        plugin.getWandItem().reload();
        plugin.updatePlayersWands();
        return true;
    }
}
