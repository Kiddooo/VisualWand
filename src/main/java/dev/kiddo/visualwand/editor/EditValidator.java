package dev.kiddo.visualwand.editor;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.Objects;
import java.util.UUID;

/**
 * Resolves and validates live Bukkit state before every editor read or write.
 */
public final class EditValidator {

    private final Server server;
    private final EditorConfiguration configuration;

    public EditValidator(Server server, EditorConfiguration configuration) {
        this.server = Objects.requireNonNull(server, "server");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public Result validateSelection(Player player, Display display) {
        Objects.requireNonNull(display, "display");
        return validate(
                player,
                display.getUniqueId(),
                display.getWorld().getKey(),
                null);
    }

    public Result validate(Player player, EditorSession session, EditMode mode) {
        Objects.requireNonNull(session, "session");
        if (player == null || !session.playerId().equals(player.getUniqueId())) {
            return Result.failure("The editor session no longer belongs to this player.");
        }
        return validate(player, session.displayId(), session.worldKey(), mode);
    }

    private Result validate(
            Player player,
            UUID displayId,
            NamespacedKey expectedWorldKey,
            EditMode mode) {
        if (player == null || !player.isOnline()) {
            return Result.failure("You must be online to edit a display.");
        }
        if (!player.hasPermission("visualwand.use")) {
            return Result.failure("You no longer have permission to edit displays.");
        }

        Entity resolved = server.getEntity(displayId);
        if (!(resolved instanceof Display display) || !isSupportedDisplay(display)) {
            return Result.failure("The selected display is no longer available.");
        }
        if (!display.isValid() || display.isDead()) {
            return Result.failure("The selected display is no longer valid.");
        }

        World displayWorld = display.getWorld();
        if (!displayWorld.getKey().equals(expectedWorldKey)) {
            return Result.failure("The selected display moved to another world.");
        }
        if (!player.getWorld().getKey().equals(expectedWorldKey)) {
            return Result.failure("You must be in the selected display's world.");
        }

        DisplayState state = DisplayState.capture(display);
        if (!TransformationOperations.isFinite(state)) {
            return Result.failure("The selected display contains invalid transformation values.");
        }

        Location location = state.location();
        if (!displayWorld.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return Result.failure("The selected display's chunk is not loaded.");
        }

        double maximumDistance = configuration.maxDistance();
        double distanceSquared = player.getEyeLocation().distanceSquared(location);
        if (!Double.isFinite(distanceSquared)
                || distanceSquared > maximumDistance * maximumDistance) {
            return Result.failure("The selected display is too far away.");
        }
        if (mode != null && !mode.supports(display)) {
            return Result.failure("This editing mode does not support the selected display.");
        }

        return Result.success(display, state);
    }

    private static boolean isSupportedDisplay(Display display) {
        return display instanceof BlockDisplay
                || display instanceof ItemDisplay
                || display instanceof TextDisplay;
    }

    public record Result(Display display, DisplayState state, String failureReason) {
        public static Result success(Display display, DisplayState state) {
            return new Result(
                    Objects.requireNonNull(display, "display"),
                    Objects.requireNonNull(state, "state"),
                    null);
        }

        public static Result failure(String reason) {
            return new Result(null, null, Objects.requireNonNull(reason, "reason"));
        }

        public boolean valid() {
            return display != null;
        }
    }
}
