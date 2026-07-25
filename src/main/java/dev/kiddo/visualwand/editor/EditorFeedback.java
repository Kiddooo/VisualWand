package dev.kiddo.visualwand.editor;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns editor action-bar feedback and reference-counted selection glow restoration.
 */
public final class EditorFeedback {

    private final VisualWand plugin;
    private final Map<UUID, GlowState> glowStates = new HashMap<>();
    private boolean glowSelected;

    public EditorFeedback(VisualWand plugin, EditorConfiguration configuration) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.glowSelected = Objects.requireNonNull(configuration, "configuration").glowSelected();
    }

    public void reload(EditorConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (glowSelected && !configuration.glowSelected()) {
            restoreAllGlows();
        }
        glowSelected = configuration.glowSelected();
    }

    public void retainSelection(Display display) {
        Objects.requireNonNull(display, "display");
        if (!glowSelected) {
            return;
        }

        UUID displayId = display.getUniqueId();
        GlowState existing = glowStates.get(displayId);
        if (existing == null) {
            glowStates.put(displayId, new GlowState(display.isGlowing()));
        } else {
            existing.references++;
        }
        display.setGlowing(true);
    }

    public void releaseSelection(UUID displayId) {
        GlowState state = glowStates.get(Objects.requireNonNull(displayId, "displayId"));
        if (state == null) {
            return;
        }

        state.references--;
        if (state.references > 0) {
            return;
        }

        glowStates.remove(displayId);
        Entity resolved = plugin.getServer().getEntity(displayId);
        if (resolved instanceof Display display && display.isValid()) {
            display.setGlowing(state.originalGlowing);
        }
    }

    public void show(
            Player player,
            Display display,
            EditorSession session,
            DisplayState state,
            EditorConfiguration configuration) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(configuration, "configuration");

        EditMode mode = session.mode();
        StringBuilder text = new StringBuilder()
                .append("&e")
                .append(displayType(display))
                .append(" &8#")
                .append(uuidSuffix(display.getUniqueId()))
                .append(" &8| ");

        if (mode == null) {
            text.append("&7Choose a mode &8| &f")
                    .append(session.preset().displayName())
                    .append(" preset");
        } else {
            double step = configuration.step(mode.stepType(), session.preset());
            text.append("&f")
                    .append(mode.displayName())
                    .append(" &8| &7")
                    .append(categoryName(mode.category()))
                    .append(" &f")
                    .append(session.preset().displayName())
                    .append(' ')
                    .append(formatStep(mode.stepType(), step));

            EditorSession.LastFeedback last = session.lastFeedback();
            if (last != null && last.mode() == mode) {
                text.append(" &8| &a")
                        .append(formatSigned(mode.stepType(), last.signedDelta()))
                        .append(" &8→ &f")
                        .append(mode.formatCurrent(state));
            } else {
                text.append(" &8| &f").append(mode.formatCurrent(state));
            }
        }

        text.append(" &8| &6Drop: menu");
        player.sendActionBar(Lang.getComponent(text.toString()));
    }

    public void sendModeInstruction(Player player, EditMode mode) {
        player.sendMessage(Lang.getPrefixed(
                "&a" + mode.displayName()
                        + " selected. &fLeft click increases, right click decreases. "
                        + "&eDrop the wand to reopen the menu."));
    }

    public void success(Player player, String message) {
        player.sendMessage(Lang.getPrefixed("&a" + message));
    }

    public void notice(Player player, String message) {
        player.sendMessage(Lang.getPrefixed("&e" + message));
    }

    public void reason(Player player, String reason) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendActionBar(Lang.getComponent("&c" + reason));
        player.sendMessage(Lang.getPrefixed("&c" + reason));
    }

    public void clearActionBar(Player player) {
        if (player != null && player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }

    public void shutdown() {
        restoreAllGlows();
    }

    private void restoreAllGlows() {
        for (Map.Entry<UUID, GlowState> entry : glowStates.entrySet()) {
            Entity resolved = plugin.getServer().getEntity(entry.getKey());
            if (resolved instanceof Display display && display.isValid()) {
                display.setGlowing(entry.getValue().originalGlowing);
            }
        }
        glowStates.clear();
    }

    private static String uuidSuffix(UUID id) {
        String value = id.toString().replace("-", "");
        return value.substring(value.length() - 8);
    }

    private static String displayType(Display display) {
        if (display instanceof BlockDisplay) {
            return "Block Display";
        }
        if (display instanceof ItemDisplay) {
            return "Item Display";
        }
        if (display instanceof TextDisplay) {
            return "Text Display";
        }
        return "Display";
    }

    private static String categoryName(EditCategory category) {
        String lower = category.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String formatStep(StepType type, double value) {
        return switch (type) {
            case TRANSLATION, SCALE -> String.format(Locale.ROOT, "%.3f", value);
            case ROTATION -> String.format(Locale.ROOT, "%.1f°", value);
        };
    }

    private static String formatSigned(StepType type, double value) {
        return switch (type) {
            case TRANSLATION, SCALE -> String.format(Locale.ROOT, "%+.3f", value);
            case ROTATION -> String.format(Locale.ROOT, "%+.1f°", value);
        };
    }

    private static final class GlowState {
        private final boolean originalGlowing;
        private int references = 1;

        private GlowState(boolean originalGlowing) {
            this.originalGlowing = originalGlowing;
        }
    }
}
