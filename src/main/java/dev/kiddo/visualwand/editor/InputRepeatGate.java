package dev.kiddo.visualwand.editor;

import java.util.Objects;

/**
 * Bounds repeated click pulses independently for each edit direction.
 */
public final class InputRepeatGate {

    private static final long UNSET = Long.MIN_VALUE;

    private final PulseState[] states = {
        new PulseState(),
        new PulseState()
    };

    /**
     * Returns whether this pulse may mutate the selected display.
     * The first pulse after a release gap is immediate, then repeating begins after the
     * configured initial delay and is bounded by the repeat interval.
     */
    public boolean accept(
            EditDirection direction,
            long tick,
            int initialDelayTicks,
            int repeatIntervalTicks,
            int releaseGapTicks) {
        Objects.requireNonNull(direction, "direction");
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must be non-negative");
        }
        if (initialDelayTicks < 0) {
            throw new IllegalArgumentException("initialDelayTicks must be non-negative");
        }
        if (repeatIntervalTicks < 1) {
            throw new IllegalArgumentException("repeatIntervalTicks must be positive");
        }
        if (releaseGapTicks < 1) {
            throw new IllegalArgumentException("releaseGapTicks must be positive");
        }

        PulseState state = states[direction.ordinal()];
        if (state.lastPulseTick == tick) {
            return false;
        }

        boolean released = state.lastPulseTick == UNSET
                || tick < state.lastPulseTick
                || tick - state.lastPulseTick >= releaseGapTicks;
        if (released) {
            state.firstPulseTick = tick;
            state.lastPulseTick = tick;
            state.lastAcceptedTick = tick;
            return true;
        }

        state.lastPulseTick = tick;
        if (tick - state.firstPulseTick < initialDelayTicks) {
            return false;
        }
        if (tick - state.lastAcceptedTick < repeatIntervalTicks) {
            return false;
        }

        state.lastAcceptedTick = tick;
        return true;
    }

    public void reset() {
        for (PulseState state : states) {
            state.reset();
        }
    }

    private static final class PulseState {
        private long firstPulseTick = UNSET;
        private long lastPulseTick = UNSET;
        private long lastAcceptedTick = UNSET;

        private void reset() {
            firstPulseTick = UNSET;
            lastPulseTick = UNSET;
            lastAcceptedTick = UNSET;
        }
    }
}
