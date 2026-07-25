package dev.kiddo.visualwand.editor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;

/**
 * Mutable editing metadata for one player, without retaining Bukkit entity objects.
 */
public final class EditorSession {

    private static final long NEVER_CONSUMED = Long.MIN_VALUE;

    private final UUID playerId;
    private final UUID displayId;
    private final NamespacedKey worldKey;
    private final InputRepeatGate repeatGate = new InputRepeatGate();

    private StepPreset preset;
    private EditMode mode;
    private boolean consuming;
    private long lastConsumedTick = NEVER_CONSUMED;
    private long inputSuppressedThroughTick = NEVER_CONSUMED;
    private DisplayState undoState;
    private LastFeedback lastFeedback;

    public EditorSession(Player player, Display display, StepPreset preset) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(display, "display");
        this.playerId = player.getUniqueId();
        this.displayId = display.getUniqueId();
        this.worldKey = display.getWorld().getKey();
        this.preset = Objects.requireNonNull(preset, "preset");
    }

    public UUID playerId() {
        return playerId;
    }

    public UUID displayId() {
        return displayId;
    }

    public NamespacedKey worldKey() {
        return worldKey;
    }

    public StepPreset preset() {
        return preset;
    }

    public EditMode mode() {
        return mode;
    }

    public boolean consuming() {
        return consuming;
    }

    public long lastConsumedTick() {
        return lastConsumedTick;
    }

    public DisplayState undoState() {
        return undoState;
    }

    public LastFeedback lastFeedback() {
        return lastFeedback;
    }

    InputRepeatGate repeatGate() {
        return repeatGate;
    }

    boolean isInputSuppressed(long tick) {
        return tick <= inputSuppressedThroughTick;
    }

    void suppressInputThrough(long tick) {
        inputSuppressedThroughTick = Math.max(inputSuppressedThroughTick, tick);
        repeatGate.reset();
    }

    void selectPreset(StepPreset preset) {
        this.preset = Objects.requireNonNull(preset, "preset");
    }

    void selectMode(EditMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
        repeatGate.reset();
        consuming = false;
        lastConsumedTick = NEVER_CONSUMED;
        inputSuppressedThroughTick = NEVER_CONSUMED;
        lastFeedback = null;
    }

    void cancelMode() {
        mode = null;
        repeatGate.reset();
        consuming = false;
        lastConsumedTick = NEVER_CONSUMED;
        inputSuppressedThroughTick = NEVER_CONSUMED;
        lastFeedback = null;
    }

    void markInputConsumed(long tick) {
        consuming = true;
        lastConsumedTick = tick;
    }

    void clearConsumptionIfIdle(long tick, int releaseGapTicks) {
        if (consuming
                && (tick < lastConsumedTick || tick - lastConsumedTick >= releaseGapTicks)) {
            consuming = false;
            lastConsumedTick = NEVER_CONSUMED;
        }
    }

    void setUndoState(DisplayState undoState) {
        this.undoState = undoState;
    }

    void setLastFeedback(LastFeedback lastFeedback) {
        this.lastFeedback = lastFeedback;
    }

    public record LastFeedback(EditMode mode, double signedDelta, String currentValue) {
        public LastFeedback {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(currentValue, "currentValue");
            if (!Double.isFinite(signedDelta)) {
                throw new IllegalArgumentException("signedDelta must be finite");
            }
        }
    }
}
