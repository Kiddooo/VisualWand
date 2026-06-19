package dev.kiddo.visualwand.editor;

import dev.kiddo.visualwand.VisualWand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class EditorManager {

    private final VisualWand plugin;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Map<UUID, InputRequest> pendingInputs = new HashMap<>();

    public EditorManager(VisualWand plugin) {
        this.plugin = plugin;
    }

    public EditorSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public EditorSession createSession(Player player, Display display) {
        EditorSession session = new EditorSession(plugin, player, display);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public void removeSession(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
        }
        pendingInputs.remove(player.getUniqueId());
    }

    public boolean isAwaitingInput(Player player) {
        return pendingInputs.containsKey(player.getUniqueId());
    }

    public void startTextInput(Player player, TextDisplay textDisplay) {
        player.sendMessage(plugin.getLang().getPrefixed("text-enter-message"));
        
        pendingInputs.put(player.getUniqueId(), new InputRequest(InputType.TEXT, input -> {
            if (textDisplay != null) {
                Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(input);
                textDisplay.text(component);
                player.sendMessage(plugin.getLang().getPrefixed("text-set-success", "text", input));
            }
        }));
    }

    public void startCMDInput(Player player, ItemDisplay itemDisplay) {
        player.sendMessage(plugin.getLang().getPrefixed("cmd-enter-value"));
        
        pendingInputs.put(player.getUniqueId(), new InputRequest(InputType.CMD, input -> {
            try {
                int cmd = Integer.parseInt(input);
                if (itemDisplay != null) {
                    ItemStack item = itemDisplay.getItemStack();
                    if (item != null) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null) {
                            meta.setCustomModelData(cmd);
                            item.setItemMeta(meta);
                            itemDisplay.setItemStack(item);
                            player.sendMessage(plugin.getLang().getPrefixed("cmd-set-success", "value", cmd));
                        }
                    }
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getLang().getPrefixed("cmd-invalid"));
            }
        }));
    }

    public void handleChatInput(Player player, String message) {
        InputRequest request = pendingInputs.remove(player.getUniqueId());
        if (request != null) {
            request.handler.accept(message);
        }
    }

    public enum InputType {
        TEXT,
        CMD
    }

    private record InputRequest(InputType type, Consumer<String> handler) {}
}
