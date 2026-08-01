package dev.kiddo.visualwand.listener;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisplayCycleTest {

    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);
    private static final UUID C = new UUID(0L, 3L);
    private static final UUID D = new UUID(0L, 4L);

    @Test
    void adjacentSlotsAndHotbarWrapMapToDirections() {
        assertSame(DisplayCycle.Direction.FORWARD, DisplayCycle.Direction.fromSlots(3, 4));
        assertSame(DisplayCycle.Direction.FORWARD, DisplayCycle.Direction.fromSlots(8, 0));
        assertSame(DisplayCycle.Direction.BACKWARD, DisplayCycle.Direction.fromSlots(4, 3));
        assertSame(DisplayCycle.Direction.BACKWARD, DisplayCycle.Direction.fromSlots(0, 8));
        assertNull(DisplayCycle.Direction.fromSlots(1, 5));
        assertNull(DisplayCycle.Direction.fromSlots(-1, 0));
        assertNull(DisplayCycle.Direction.fromSlots(9, 1));
        assertNull(DisplayCycle.Direction.fromSlots(0, -1));
        assertNull(DisplayCycle.Direction.fromSlots(8, 9));
    }

    @Test
    void viewConeIncludesBoundaryAndRejectsOutOfViewCandidates() {
        Vector view = new Vector(0.0D, 0.0D, 1.0D);

        DisplayCycle.Candidate boundary = DisplayCycle.candidate(
                A, view, new Vector(1.0D, 0.0D, 1.0D));
        DisplayCycle.Candidate outside = DisplayCycle.candidate(
                B, view, new Vector(1.01D, 0.0D, 1.0D));
        DisplayCycle.Candidate largeParallel = DisplayCycle.candidate(
                C,
                new Vector(1.0E100D, 0.0D, 0.0D),
                new Vector(1.0E100D, 0.0D, 0.0D));

        assertEquals(A, boundary.displayId());
        assertEquals(2.0D, boundary.distanceSquared());
        assertNull(outside);
        assertEquals(C, largeParallel.displayId());
        assertNull(DisplayCycle.candidate(A, new Vector(), new Vector(0.0D, 0.0D, 1.0D)));
        assertNull(DisplayCycle.candidate(
                A,
                new Vector(1.0E-7D, 0.0D, 0.0D),
                new Vector(1.0D, 0.0D, 0.0D)));
        assertNull(DisplayCycle.candidate(A, view, new Vector(Double.NaN, 0.0D, 1.0D)));
    }

    @Test
    void rankingUsesAlignmentThenDistanceThenUuid() {
        DisplayCycle cycle = DisplayCycle.start(List.of(
                new DisplayCycle.Candidate(D, 0.9D, 0.5D),
                new DisplayCycle.Candidate(C, 1.0D, 4.0D),
                new DisplayCycle.Candidate(B, 1.0D, 1.0D),
                new DisplayCycle.Candidate(A, 1.0D, 1.0D),
                new DisplayCycle.Candidate(A, 0.5D, 9.0D)),
                null,
                DisplayCycle.Direction.FORWARD);

        assertEquals(A, cycle.current());
        assertEquals(B, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertEquals(C, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertEquals(D, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertEquals(A, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertEquals(B, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertThrows(IllegalArgumentException.class,
                () -> new DisplayCycle.Candidate(A, Double.NaN, 1.0D));
        assertThrows(IllegalArgumentException.class,
                () -> new DisplayCycle.Candidate(A, 1.0D, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new DisplayCycle.Candidate(A, 1.0D, -1.0D));
    }

    @Test
    void exactHoverIsStartingPointBeforeRequestedAdvance() {
        List<DisplayCycle.Candidate> source = new ArrayList<>(candidates());
        DisplayCycle cycle = DisplayCycle.start(source, B, DisplayCycle.Direction.FORWARD);

        source.clear();
        source.add(new DisplayCycle.Candidate(D, 1.0D, 1.0D));

        assertEquals(C, cycle.current());
        assertEquals(A, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertNull(DisplayCycle.start(List.of(), null, DisplayCycle.Direction.FORWARD));
    }

    @Test
    void backwardStartAndNavigationWrap() {
        DisplayCycle cycle = DisplayCycle.start(candidates(), null, DisplayCycle.Direction.BACKWARD);

        assertEquals(C, cycle.current());
        assertEquals(B, cycle.advance(DisplayCycle.Direction.BACKWARD, ignored -> true));
        assertEquals(A, cycle.advance(DisplayCycle.Direction.BACKWARD, ignored -> true));
        assertEquals(C, cycle.advance(DisplayCycle.Direction.BACKWARD, ignored -> true));
    }

    @Test
    void advanceSkipsStaleCandidatesAndReportsExhaustion() {
        DisplayCycle cycle = DisplayCycle.start(candidates(), null, DisplayCycle.Direction.FORWARD);

        assertEquals(C, cycle.advance(
                DisplayCycle.Direction.FORWARD,
                id -> id.equals(C)));

        AtomicInteger checks = new AtomicInteger();
        assertNull(cycle.advance(
                DisplayCycle.Direction.FORWARD,
                ignored -> {
                    checks.incrementAndGet();
                    return false;
                }));
        assertEquals(3, checks.get());
        assertEquals(C, cycle.current());
    }

    private static List<DisplayCycle.Candidate> candidates() {
        return List.of(
                new DisplayCycle.Candidate(A, 1.0D, 1.0D),
                new DisplayCycle.Candidate(B, 0.9D, 1.0D),
                new DisplayCycle.Candidate(C, 0.8D, 1.0D));
    }
}
