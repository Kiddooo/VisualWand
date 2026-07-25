package dev.kiddo.visualwand.editor;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Sole owner of display selections, edit modes, input repetition, undo, clipboard, and
 * editor lifecycle validation.
 */
public final class EditorManager {

    private final VisualWand plugin;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();
    private final Map<UUID, StepPreset> retainedPresets = new HashMap<>();
    private final Map<UUID, Transformation> clipboards = new HashMap<>();
    private final Map<UUID, TextInputRequest> pendingInputs = new HashMap<>();
    private final EditorFeedback feedback;

    private EditorConfiguration configuration;
    private TransformationOperations operations;
    private EditValidator validator;
    private BukkitTask tickTask;
    private long currentTick;
    private boolean shutDown;

    public EditorManager(VisualWand plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configuration = EditorConfiguration.load(plugin);
        this.operations = createOperations(configuration);
        this.validator = new EditValidator(plugin.getServer(), configuration);
        this.feedback = new EditorFeedback(plugin, configuration);
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tick,
                1L,
                1L);
    }

    public EditorConfiguration configuration() {
        return configuration;
    }

    public EditorSession session(Player player) {
        Objects.requireNonNull(player, "player");
        return sessions.get(player.getUniqueId());
    }

    public List<EditorSession> sessions() {
        return List.copyOf(sessions.values());
    }

    public boolean select(Player player, Display display) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(display, "display");

        EditValidator.Result validation = validator.validateSelection(player, display);
        if (!validation.valid()) {
            feedback.reason(player, validation.failureReason());
            return false;
        }

        UUID playerId = player.getUniqueId();
        EditorSession previous = sessions.remove(playerId);
        if (previous != null) {
            feedback.releaseSelection(previous.displayId());
        }
        pendingInputs.remove(playerId);

        StepPreset preset = retainedPresets.getOrDefault(playerId, StepPreset.NORMAL);
        EditorSession selected = new EditorSession(player, validation.display(), preset);
        sessions.put(playerId, selected);
        feedback.retainSelection(validation.display());
        feedback.show(player, validation.display(), selected, validation.state(), configuration);
        return true;
    }

    public Display selectedDisplay(Player player) {
        Objects.requireNonNull(player, "player");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            return null;
        }

        EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
        return validation.valid() ? validation.display() : null;
    }

    public boolean isSelected(UUID displayId) {
        Objects.requireNonNull(displayId, "displayId");
        for (EditorSession selected : sessions.values()) {
            if (selected.displayId().equals(displayId)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasActiveMode(Player player) {
        EditorSession selected = session(player);
        return selected != null && selected.mode() != null;
    }

    public boolean shouldConsume(Player player) {
        Objects.requireNonNull(player, "player");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null || selected.mode() == null) {
            return false;
        }
        return validateOrClear(player, selected, selected.mode()).valid();
    }

    public boolean selectMode(Player player, EditMode mode) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mode, "mode");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            feedback.reason(player, "Select a display before choosing an editing mode.");
            return false;
        }

        EditValidator.Result validation = validateOrClear(player, selected, mode);
        if (!validation.valid()) {
            return false;
        }

        selected.selectMode(mode);
        feedback.show(player, validation.display(), selected, validation.state(), configuration);
        feedback.sendModeInstruction(player, mode);
        return true;
    }

    public boolean selectPreset(Player player, StepPreset preset) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(preset, "preset");
        UUID playerId = player.getUniqueId();
        retainedPresets.put(playerId, preset);

        EditorSession selected = sessions.get(playerId);
        if (selected == null) {
            return true;
        }

        EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
        if (!validation.valid()) {
            return false;
        }

        selected.selectPreset(preset);
        selected.repeatGate().reset();
        selected.setLastFeedback(null);
        feedback.show(player, validation.display(), selected, validation.state(), configuration);
        feedback.success(player, preset.displayName() + " step preset selected.");
        return true;
    }

    public boolean cancelEditing(Player player) {
        Objects.requireNonNull(player, "player");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            return false;
        }

        EditValidator.Result validation = validateOrClear(player, selected, null);
        if (!validation.valid()) {
            return false;
        }

        selected.cancelMode();
        feedback.show(player, validation.display(), selected, validation.state(), configuration);
        feedback.notice(player, "Click editing paused; the display remains selected.");
        return true;
    }

    /**
     * Consumes every valid active pulse, even when the repeat gate throttles its mutation.
     */
    public boolean applyInput(Player player, EditDirection direction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(direction, "direction");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null || selected.mode() == null) {
            return false;
        }

        EditMode mode = selected.mode();
        EditValidator.Result validation = validateOrClear(player, selected, mode);
        if (!validation.valid()) {
            return false;
        }

        selected.markInputConsumed(currentTick);
        boolean accepted = selected.repeatGate().accept(
                direction,
                currentTick,
                configuration.inputInitialDelayTicks(),
                configuration.inputRepeatIntervalTicks(),
                configuration.inputReleaseGapTicks());
        if (!accepted) {
            return true;
        }

        double step = configuration.step(mode.stepType(), selected.preset());
        double signedDelta = step * direction.sign();
        DisplayState candidate;
        try {
            candidate = mode.apply(operations, validation.state(), step, direction);
        } catch (IllegalArgumentException exception) {
            feedback.reason(player, "That edit would create an invalid display state.");
            return false;
        }
        if (!TransformationOperations.isFinite(candidate)) {
            feedback.reason(player, "That edit would create non-finite values.");
            return false;
        }

        selected.setLastFeedback(new EditorSession.LastFeedback(
                mode,
                signedDelta,
                mode.formatCurrent(candidate)));
        WriteResult write = writeCandidate(player, selected, validation, candidate);
        if (write.outcome() == WriteOutcome.REJECTED) {
            feedback.reason(player, "That edit could not be applied safely.");
            return false;
        }

        DisplayState authoritative = write.authoritativeState();
        selected.setLastFeedback(new EditorSession.LastFeedback(
                mode,
                signedDelta,
                mode.formatCurrent(authoritative)));
        feedback.show(
                player,
                validation.display(),
                selected,
                authoritative,
                configuration);
        return true;
    }

    public boolean undo(Player player) {
        Objects.requireNonNull(player, "player");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            feedback.notice(player, "No display is selected.");
            return false;
        }

        DisplayState undoState = selected.undoState();
        if (undoState == null) {
            feedback.notice(player, "There is no editor change to undo.");
            return false;
        }

        EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
        if (!validation.valid()) {
            return false;
        }

        selected.setLastFeedback(null);
        WriteResult write = writeCandidate(player, selected, validation, undoState);
        return finishUtility(player, selected, validation.display(), write, "Swapped to the previous display state.");
    }

    public boolean copy(Player player) {
        Objects.requireNonNull(player, "player");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            feedback.notice(player, "No display is selected.");
            return false;
        }

        EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
        if (!validation.valid()) {
            return false;
        }

        clipboards.put(
                player.getUniqueId(),
                TransformationOperations.copyTransformation(validation.state().transformation()));
        feedback.success(player, "Copied the display transformation.");
        feedback.show(player, validation.display(), selected, validation.state(), configuration);
        return true;
    }

    public boolean paste(Player player) {
        Objects.requireNonNull(player, "player");
        Transformation clipboard = clipboards.get(player.getUniqueId());
        if (clipboard == null) {
            feedback.notice(player, "The transformation clipboard is empty.");
            return false;
        }

        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            feedback.notice(player, "No display is selected.");
            return false;
        }
        EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
        if (!validation.valid()) {
            return false;
        }
        if (!scaleWithinConfiguredBounds(clipboard)) {
            feedback.reason(player, "The copied scale is outside the configured editor bounds.");
            return false;
        }

        selected.setLastFeedback(null);
        DisplayState candidate = validation.state().withTransformation(
                TransformationOperations.copyTransformation(clipboard));
        WriteResult write = writeCandidate(player, selected, validation, candidate);
        return finishUtility(player, selected, validation.display(), write, "Pasted the transformation.");
    }

    public boolean resetTranslation(Player player) {
        return applyTransformationUtility(
                player,
                operations::resetTranslation,
                "Reset transformation translation.");
    }

    public boolean resetRotations(Player player) {
        return applyTransformationUtility(
                player,
                operations::resetRotations,
                "Reset both transformation rotations.");
    }

    public boolean resetScale(Player player) {
        return applyTransformationUtility(
                player,
                operations::resetScale,
                "Reset transformation scale.");
    }

    public boolean resetEntireTransformation(Player player) {
        return applyTransformationUtility(
                player,
                ignored -> operations.resetAll(),
                "Reset the entire transformation.");
    }

    public void clear(Player player) {
        Objects.requireNonNull(player, "player");
        clearPlayer(player.getUniqueId(), player, false, null);
    }
    public void clear(Player player, String reason) {
        Objects.requireNonNull(player, "player");
        clearPlayer(
                player.getUniqueId(),
                player,
                true,
                Objects.requireNonNull(reason, "reason"));
    }


    public int clearEditorsOf(UUID displayId) {
        Objects.requireNonNull(displayId, "displayId");
        List<UUID> editors = new ArrayList<>();
        for (Map.Entry<UUID, EditorSession> entry : sessions.entrySet()) {
            if (entry.getValue().displayId().equals(displayId)) {
                editors.add(entry.getKey());
            }
        }

        for (UUID playerId : editors) {
            Player player = plugin.getServer().getPlayer(playerId);
            clearPlayer(
                    playerId,
                    player,
                    true,
                    "Editing ended because the selected display was removed.");
        }
        return editors.size();
    }

    public boolean isAwaitingInput(Player player) {
        return pendingInputs.containsKey(player.getUniqueId());
    }

    public void startTextInput(Player player, TextDisplay textDisplay) {
        Objects.requireNonNull(player, "player");
        player.sendMessage(Lang.getPrefixed("&eType text in chat:"));
        pendingInputs.put(
                player.getUniqueId(),
                new TextInputRequest(textDisplay == null ? null : textDisplay.getUniqueId()));
    }

    public void handleChatInput(Player player, String message) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(message, "message");
        TextInputRequest request = pendingInputs.remove(player.getUniqueId());
        if (request == null || request.displayId() == null) {
            return;
        }

        Entity resolved = plugin.getServer().getEntity(request.displayId());
        if (!(resolved instanceof TextDisplay textDisplay)) {
            feedback.reason(player, "The text display is no longer available.");
            return;
        }
        EditValidator.Result validation = validator.validateSelection(player, textDisplay);
        if (!validation.valid()) {
            feedback.reason(player, validation.failureReason());
            return;
        }

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        ((TextDisplay) validation.display()).text(component);
        player.sendMessage(Lang.getPrefixed("&aText set: &f" + message));
    }

    public void clearAll() {
        clearAll(false, null);
    }
    public void clearAll(String reason) {
        clearAll(true, Objects.requireNonNull(reason, "reason"));
    }


    public void reload() {
        clearAll(true, "Display editing was cleared by configuration reload.");
        pendingInputs.clear();
        clipboards.clear();
        configuration = EditorConfiguration.load(plugin);
        operations = createOperations(configuration);
        validator = new EditValidator(plugin.getServer(), configuration);
        feedback.reload(configuration);
    }

    public void shutdown() {
        if (shutDown) {
            return;
        }
        shutDown = true;
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        clearAll(false, null);
        pendingInputs.clear();
        retainedPresets.clear();
        clipboards.clear();
        feedback.shutdown();
    }

    public void tick() {
        if (shutDown) {
            return;
        }
        currentTick++;

        for (EditorSession selected : List.copyOf(sessions.values())) {
            Player player = plugin.getServer().getPlayer(selected.playerId());
            if (player == null || !player.isOnline()) {
                clearPlayer(selected.playerId(), null, false, null);
                continue;
            }

            selected.clearConsumptionIfIdle(
                    currentTick,
                    configuration.inputReleaseGapTicks());
            EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
            if (!validation.valid() || selected.mode() == null) {
                continue;
            }
            if (currentTick % configuration.feedbackUpdateIntervalTicks() == 0L) {
                feedback.show(
                        player,
                        validation.display(),
                        selected,
                        validation.state(),
                        configuration);
            }
        }
    }

    private boolean applyTransformationUtility(
            Player player,
            UnaryOperator<Transformation> mutation,
            String successMessage) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(mutation, "mutation");
        EditorSession selected = sessions.get(player.getUniqueId());
        if (selected == null) {
            feedback.notice(player, "No display is selected.");
            return false;
        }

        EditValidator.Result validation = validateOrClear(player, selected, selected.mode());
        if (!validation.valid()) {
            return false;
        }

        Transformation transformed;
        try {
            transformed = mutation.apply(validation.state().transformation());
        } catch (IllegalArgumentException exception) {
            feedback.reason(player, "That utility would create an invalid transformation.");
            return false;
        }
        DisplayState candidate = validation.state().withTransformation(transformed);
        selected.setLastFeedback(null);
        WriteResult write = writeCandidate(player, selected, validation, candidate);
        return finishUtility(player, selected, validation.display(), write, successMessage);
    }

    private boolean finishUtility(
            Player player,
            EditorSession selected,
            Display display,
            WriteResult write,
            String successMessage) {
        if (write.outcome() == WriteOutcome.REJECTED) {
            feedback.reason(player, "That editor utility could not be applied safely.");
            return false;
        }
        if (write.outcome() == WriteOutcome.UNCHANGED) {
            feedback.notice(player, "The display already has that state.");
        } else {
            feedback.success(player, successMessage);
        }
        feedback.show(player, display, selected, write.authoritativeState(), configuration);
        return true;
    }

    private WriteResult writeCandidate(
            Player player,
            EditorSession selected,
            EditValidator.Result validation,
            DisplayState candidate) {
        if (!TransformationOperations.isFinite(candidate)) {
            return WriteResult.rejected();
        }

        Location candidateLocation = candidate.location();
        if (candidateLocation.getWorld() == null
                || !candidateLocation.getWorld().getKey().equals(selected.worldKey())
                || !candidateLocation.getWorld().isChunkLoaded(
                        candidateLocation.getBlockX() >> 4,
                        candidateLocation.getBlockZ() >> 4)) {
            return WriteResult.rejected();
        }
        double distanceSquared = player.getEyeLocation().distanceSquared(candidateLocation);
        double maximumDistance = configuration.maxDistance();
        if (!Double.isFinite(distanceSquared)
                || distanceSquared > maximumDistance * maximumDistance) {
            return WriteResult.rejected();
        }

        DisplayState current = validation.state();
        if (candidate.equals(current)) {
            return WriteResult.unchanged(current);
        }

        candidate.apply(validation.display());
        DisplayState authoritative = DisplayState.capture(validation.display());
        if (!TransformationOperations.isFinite(authoritative)) {
            return WriteResult.rejected();
        }
        if (authoritative.equals(current)) {
            return WriteResult.unchanged(authoritative);
        }

        selected.setUndoState(current);
        return WriteResult.applied(authoritative);
    }

    private EditValidator.Result validateOrClear(
            Player player,
            EditorSession selected,
            EditMode mode) {
        EditValidator.Result validation = validator.validate(player, selected, mode);
        if (!validation.valid()) {
            clearPlayer(
                    selected.playerId(),
                    player,
                    true,
                    validation.failureReason());
        }
        return validation;
    }

    private void clearPlayer(
            UUID playerId,
            Player player,
            boolean explain,
            String reason) {
        EditorSession removed = sessions.remove(playerId);
        pendingInputs.remove(playerId);
        if (removed != null) {
            feedback.releaseSelection(removed.displayId());
        }
        feedback.clearActionBar(player);
        if (explain && reason != null) {
            feedback.reason(player, reason);
        }
    }

    private void clearAll(boolean explain, String reason) {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = plugin.getServer().getPlayer(playerId);
            clearPlayer(playerId, player, explain, reason);
        }
    }

    private boolean scaleWithinConfiguredBounds(Transformation transformation) {
        Vector3f scale = transformation.getScale();
        return componentWithinScaleBounds(scale.x)
                && componentWithinScaleBounds(scale.y)
                && componentWithinScaleBounds(scale.z);
    }

    private boolean componentWithinScaleBounds(float value) {
        float magnitude = Math.abs(value);
        return Float.isFinite(value)
                && magnitude >= configuration.scaleMinimumMagnitude()
                && magnitude <= configuration.scaleMaximumMagnitude();
    }

    private static TransformationOperations createOperations(EditorConfiguration configuration) {
        return new TransformationOperations(
                configuration.scaleMinimumMagnitude(),
                configuration.scaleMaximumMagnitude());
    }

    private enum WriteOutcome {
        APPLIED,
        UNCHANGED,
        REJECTED
    }

    private record WriteResult(WriteOutcome outcome, DisplayState authoritativeState) {
        private static WriteResult applied(DisplayState state) {
            return new WriteResult(WriteOutcome.APPLIED, state);
        }

        private static WriteResult unchanged(DisplayState state) {
            return new WriteResult(WriteOutcome.UNCHANGED, state);
        }

        private static WriteResult rejected() {
            return new WriteResult(WriteOutcome.REJECTED, null);
        }
    }

    private record TextInputRequest(UUID displayId) {
    }
}
