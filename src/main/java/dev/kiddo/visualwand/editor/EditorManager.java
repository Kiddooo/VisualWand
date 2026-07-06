package dev.kiddo.visualwand.editor;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
        player.sendMessage(Lang.getPrefixed("&eType text in chat:"));
        
        pendingInputs.put(player.getUniqueId(), new InputRequest(InputType.TEXT, input -> {
            if (textDisplay != null) {
                Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(input);
                textDisplay.text(component);
                player.sendMessage(Lang.getPrefixed("&aText set: &f" + input));
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
        TEXT
    }

    private record InputRequest(InputType type, Consumer<String> handler) {}
}
