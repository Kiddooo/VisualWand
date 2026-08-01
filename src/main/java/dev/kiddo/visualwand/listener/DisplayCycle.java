package dev.kiddo.visualwand.listener;

import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

final class DisplayCycle {

    private static final double MINIMUM_LENGTH_SQUARED = 1.0E-12D;

    private final List<UUID> displayIds;
    private int index;

    private DisplayCycle(List<UUID> displayIds, int index) {
        this.displayIds = List.copyOf(displayIds);
        this.index = index;
    }

    static Candidate candidate(UUID displayId, Vector viewDirection, Vector eyeToDisplay) {
        Objects.requireNonNull(displayId, "displayId");
        Objects.requireNonNull(viewDirection, "viewDirection");
        Objects.requireNonNull(eyeToDisplay, "eyeToDisplay");

        double viewLengthSquared = viewDirection.lengthSquared();
        double distanceSquared = eyeToDisplay.lengthSquared();
        if (!finite(viewDirection) || !finite(eyeToDisplay)
                || !Double.isFinite(viewLengthSquared) || !Double.isFinite(distanceSquared)
                || viewLengthSquared <= MINIMUM_LENGTH_SQUARED
                || distanceSquared <= MINIMUM_LENGTH_SQUARED) {
            return null;
        }

        double alignment = viewDirection.dot(eyeToDisplay)
                / (Math.sqrt(viewLengthSquared) * Math.sqrt(distanceSquared));
        if (!Double.isFinite(alignment) || alignment <= 0.0D) {
            return null;
        }
        return new Candidate(displayId, alignment, distanceSquared);
    }

    static DisplayCycle start(
            Collection<Candidate> candidates,
            UUID exactHover,
            Direction direction) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(direction, "direction");

        List<UUID> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::alignment).reversed()
                        .thenComparingDouble(Candidate::distanceSquared)
                        .thenComparing(Candidate::displayId))
                .map(Candidate::displayId)
                .distinct()
                .toList();
        if (ranked.isEmpty()) {
            return null;
        }

        int hoverIndex = exactHover == null ? -1 : ranked.indexOf(exactHover);
        int initialIndex = hoverIndex >= 0
                ? wrap(hoverIndex + direction.delta, ranked.size())
                : direction == Direction.FORWARD ? 0 : ranked.size() - 1;
        return new DisplayCycle(ranked, initialIndex);
    }

    UUID current() {
        return displayIds.get(index);
    }

    UUID advance(Direction direction, Predicate<UUID> eligible) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(eligible, "eligible");

        for (int checked = 0; checked < displayIds.size(); checked++) {
            index = wrap(index + direction.delta, displayIds.size());
            UUID candidate = current();
            if (eligible.test(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int wrap(int value, int size) {
        return Math.floorMod(value, size);
    }

    private static boolean finite(Vector vector) {
        return Double.isFinite(vector.getX())
                && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }

    record Candidate(UUID displayId, double alignment, double distanceSquared) {
        Candidate {
            Objects.requireNonNull(displayId, "displayId");
            if (!Double.isFinite(alignment) || !Double.isFinite(distanceSquared)
                    || distanceSquared < 0.0D) {
                throw new IllegalArgumentException(
                        "Candidate ranking values must be finite with non-negative distance");
            }
        }
    }

    enum Direction {
        FORWARD(1),
        BACKWARD(-1);

        private final int delta;

        Direction(int delta) {
            this.delta = delta;
        }

        static Direction fromSlots(int previousSlot, int newSlot) {
            if (previousSlot < 0 || previousSlot >= 9 || newSlot < 0 || newSlot >= 9) {
                return null;
            }
            if (newSlot == Math.floorMod(previousSlot + 1, 9)) {
                return FORWARD;
            }
            if (newSlot == Math.floorMod(previousSlot - 1, 9)) {
                return BACKWARD;
            }
            return null;
        }
    }
}
