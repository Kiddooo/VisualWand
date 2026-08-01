# Nearby Display Cycling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Shift+wheel preview cycling for visible Display entities within eight blocks, confirm the preview with right click, and remove the redundant Shift+RMB deletion shortcut.

**Architecture:** `WandListener` remains the Bukkit lifecycle owner, while a package-private `DisplayCycle` model owns deterministic ranking, slot-direction inference, and immutable per-player candidate rings. `EditorConfiguration` validates the cycle range, and existing hover glow/reference counting is reused for cycle previews.

**Tech Stack:** Java 21, Paper 1.21.11 API, Gradle Kotlin DSL, JUnit 5, Mockito 5.

---

## File Structure

- Create `src/main/java/dev/kiddo/visualwand/listener/DisplayCycle.java`: pure direction, cone, ranking, and ring navigation contracts.
- Create `src/test/java/dev/kiddo/visualwand/listener/DisplayCycleTest.java`: cycle-model contract tests.
- Create `src/test/java/dev/kiddo/visualwand/editor/EditorConfigurationTest.java`: range default, fallback, and clamp tests.
- Create `src/test/java/dev/kiddo/visualwand/listener/WandListenerTest.java`: held-slot routing and editor-exit tests.
- Modify `src/main/java/dev/kiddo/visualwand/editor/EditorConfiguration.java`: load and expose validated cycle range.
- Modify `src/main/java/dev/kiddo/visualwand/listener/WandListener.java`: register Shift+slot cycling, resolve previews, own cycle lifecycle, and remove wand deletion.
- Modify `src/main/java/dev/kiddo/visualwand/VisualWand.java`: clear targeting state on configuration reload.
- Modify `src/main/java/dev/kiddo/visualwand/util/WandItem.java`: replace deletion fallback lore with cycling guidance.
- Modify `src/main/resources/config.yml`: add cycle range/lore and remove delete-confirmation settings.
- Modify `README.md`: document Shift+scroll and GUI-only deletion in English and Polish.

### Task 1: Validated Cycle Range

**Files:**
- Create: `src/test/java/dev/kiddo/visualwand/editor/EditorConfigurationTest.java`
- Modify: `src/main/java/dev/kiddo/visualwand/editor/EditorConfiguration.java`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: Write failing configuration tests**

Create the test in the same package so it can call package-private `EditorConfiguration.load(ConfigurationSection, Logger)`:

```java
package dev.kiddo.visualwand.editor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorConfigurationTest {

    private static final Logger LOGGER = Logger.getLogger(EditorConfigurationTest.class.getName());

    @Test
    void cycleRangeDefaultsToEightBlocks() {
        EditorConfiguration configuration = load(50.0D, null);

        assertEquals(8.0D, configuration.targetCycleRange());
    }

    @Test
    void invalidCycleRangeFallsBackToEightBlocks() {
        EditorConfiguration configuration = load(50.0D, Double.NaN);

        assertEquals(8.0D, configuration.targetCycleRange());
    }

    @Test
    void cycleRangeCannotExceedEditorMaximumDistance() {
        EditorConfiguration configuration = load(5.0D, 8.0D);

        assertEquals(5.0D, configuration.targetCycleRange());
    }

    private static EditorConfiguration load(double maximumDistance, Double cycleRange) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("editor.max-distance", maximumDistance);
        if (cycleRange != null) {
            yaml.set("editor.targeting.cycle-range", cycleRange);
        }
        return EditorConfiguration.load(yaml, LOGGER);
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the missing contract**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.editor.EditorConfigurationTest
```

Expected: compilation fails because `targetCycleRange()` does not exist.

- [ ] **Step 3: Implement cycle-range loading**

In `EditorConfiguration`, add:

```java
private static final double DEFAULT_TARGET_CYCLE_RANGE = 8.0D;
```

Add `targetCycleRange` beside `maxDistance`, pass it immediately after `maxDistance` through the private constructor, and assign it. In `load(...)`, directly after loading `maxDistance`, add:

```java
double targetCycleRange = positiveDouble(
        configuration,
        logger,
        "editor.targeting.cycle-range",
        DEFAULT_TARGET_CYCLE_RANGE);
if (targetCycleRange > maxDistance) {
    logger.warning("Configured 'editor.targeting.cycle-range' exceeds 'editor.max-distance'; "
            + "clamping to " + maxDistance + ".");
    targetCycleRange = maxDistance;
}
```

Pass `targetCycleRange` into the constructor and expose:

```java
public double targetCycleRange() {
    return targetCycleRange;
}
```

Under `editor.targeting` in `config.yml`, add the range and remove the entire obsolete `delete-confirmation` section:

```yaml
  targeting:
    highlight-enabled: true
    update-interval: 2
    cycle-range: 8.0
```

- [ ] **Step 4: Run configuration tests**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.editor.EditorConfigurationTest
```

Expected: all three tests pass.

- [ ] **Step 5: Commit the range contract**

```bash
git add src/main/java/dev/kiddo/visualwand/editor/EditorConfiguration.java \
  src/test/java/dev/kiddo/visualwand/editor/EditorConfigurationTest.java \
  src/main/resources/config.yml
git commit -m "feat: configure nearby display cycle range"
```

### Task 2: Deterministic Candidate Ring

**Files:**
- Create: `src/main/java/dev/kiddo/visualwand/listener/DisplayCycle.java`
- Create: `src/test/java/dev/kiddo/visualwand/listener/DisplayCycleTest.java`

- [ ] **Step 1: Write failing cycle-model tests**

```java
package dev.kiddo.visualwand.listener;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class DisplayCycleTest {

    private static final UUID A = new UUID(0L, 1L);
    private static final UUID B = new UUID(0L, 2L);
    private static final UUID C = new UUID(0L, 3L);

    @Test
    void adjacentSlotsAndHotbarWrapMapToDirections() {
        assertSame(DisplayCycle.Direction.FORWARD, DisplayCycle.Direction.fromSlots(3, 4));
        assertSame(DisplayCycle.Direction.FORWARD, DisplayCycle.Direction.fromSlots(8, 0));
        assertSame(DisplayCycle.Direction.BACKWARD, DisplayCycle.Direction.fromSlots(4, 3));
        assertSame(DisplayCycle.Direction.BACKWARD, DisplayCycle.Direction.fromSlots(0, 8));
        assertNull(DisplayCycle.Direction.fromSlots(1, 5));
    }

    @Test
    void coneIncludesBoundaryAndRejectsObjectsOutsideIt() {
        Vector view = new Vector(0.0D, 0.0D, 1.0D);

        DisplayCycle.Candidate boundary = DisplayCycle.candidate(
                A, view, new Vector(1.0D, 0.0D, 1.0D));
        DisplayCycle.Candidate outside = DisplayCycle.candidate(
                B, view, new Vector(1.01D, 0.0D, 1.0D));

        assertEquals(A, boundary.displayId());
        assertNull(outside);
    }

    @Test
    void rankingUsesAlignmentThenDistanceThenUuid() {
        DisplayCycle cycle = DisplayCycle.start(List.of(
                new DisplayCycle.Candidate(C, 1.0D, 4.0D),
                new DisplayCycle.Candidate(B, 1.0D, 1.0D),
                new DisplayCycle.Candidate(A, 1.0D, 1.0D)),
                null,
                DisplayCycle.Direction.FORWARD);

        assertEquals(A, cycle.current());
        assertEquals(B, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertEquals(C, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
        assertEquals(A, cycle.advance(DisplayCycle.Direction.FORWARD, ignored -> true));
    }

    @Test
    void exactHoverIsStartingPointBeforeRequestedAdvance() {
        DisplayCycle cycle = DisplayCycle.start(candidates(), B, DisplayCycle.Direction.FORWARD);

        assertEquals(C, cycle.current());
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
        assertNull(cycle.advance(
                DisplayCycle.Direction.FORWARD,
                ignored -> false));
    }

    private static List<DisplayCycle.Candidate> candidates() {
        return List.of(
                new DisplayCycle.Candidate(A, 1.0D, 1.0D),
                new DisplayCycle.Candidate(B, 0.9D, 1.0D),
                new DisplayCycle.Candidate(C, 0.8D, 1.0D));
    }
}
```

- [ ] **Step 2: Run the focused test and confirm the type is missing**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.listener.DisplayCycleTest
```

Expected: compilation fails because `DisplayCycle` does not exist.

- [ ] **Step 3: Implement the pure cycle model**

Create `DisplayCycle.java`:

```java
package dev.kiddo.visualwand.listener;

import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

final class DisplayCycle {

    private static final double MINIMUM_ALIGNMENT = Math.cos(Math.toRadians(45.0D));
    private static final double ALIGNMENT_EPSILON = 1.0E-12D;

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
                || viewLengthSquared <= 1.0E-12D || distanceSquared <= 1.0E-12D) {
            return null;
        }
        double alignment = viewDirection.dot(eyeToDisplay)
                / Math.sqrt(viewLengthSquared * distanceSquared);
        if (!Double.isFinite(alignment)
                || alignment + ALIGNMENT_EPSILON < MINIMUM_ALIGNMENT) {
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
        int index = hoverIndex >= 0
                ? wrap(hoverIndex + direction.delta, ranked.size())
                : direction == Direction.FORWARD ? 0 : ranked.size() - 1;
        return new DisplayCycle(ranked, index);
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
                throw new IllegalArgumentException("Candidate ranking values must be finite");
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
```

- [ ] **Step 4: Run cycle-model tests**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.listener.DisplayCycleTest
```

Expected: all six tests pass.

- [ ] **Step 5: Commit the pure model**

```bash
git add src/main/java/dev/kiddo/visualwand/listener/DisplayCycle.java \
  src/test/java/dev/kiddo/visualwand/listener/DisplayCycleTest.java
git commit -m "feat: add deterministic display cycle model"
```

### Task 3: Shift+Wheel Listener Routing

**Files:**
- Create: `src/test/java/dev/kiddo/visualwand/listener/WandListenerTest.java`
- Modify: `src/main/java/dev/kiddo/visualwand/listener/WandListener.java`

- [ ] **Step 1: Write failing event-routing tests**

Use Mockito to construct `WandListener` without a running server. Stub the scheduled hover task, wand identity, inventory view, editor configuration, and an empty nearby-entity query. Add three tests:

```java
@Test
void acceptedShiftStepCancelsSlotChangeAndExitsEditor() {
    when(player.isSneaking()).thenReturn(true);
    when(event.getPreviousSlot()).thenReturn(3);
    when(event.getNewSlot()).thenReturn(4);
    when(editorManager.session(player)).thenReturn(editorSession);

    listener.onPlayerItemHeld(event);

    verify(event).setCancelled(true);
    verify(editorManager).clear(player);
    verify(player).sendActionBar(any(Component.class));
}

@Test
void nonAdjacentShiftSlotChangeIsUntouched() {
    when(player.isSneaking()).thenReturn(true);
    when(event.getPreviousSlot()).thenReturn(1);
    when(event.getNewSlot()).thenReturn(5);

    listener.onPlayerItemHeld(event);

    verify(event, never()).setCancelled(true);
    verify(editorManager, never()).clear(player);
}

@Test
void ordinarySlotChangeIsUntouched() {
    when(player.isSneaking()).thenReturn(false);
    when(event.getPreviousSlot()).thenReturn(3);
    when(event.getNewSlot()).thenReturn(4);

    listener.onPlayerItemHeld(event);

    verify(event, never()).setCancelled(true);
}
```

The test fixture must stub these exact contracts before constructing the listener:

```java
when(plugin.getServer()).thenReturn(server);
when(server.getScheduler()).thenReturn(scheduler);
when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
        .thenReturn(hoverTask);
when(plugin.getWandItem()).thenReturn(wandItem);
when(plugin.getEditorManager()).thenReturn(editorManager);
when(editorManager.configuration()).thenReturn(configuration);
when(configuration.targetCycleRange()).thenReturn(8.0D);
when(event.getPlayer()).thenReturn(player);
when(player.getUniqueId()).thenReturn(new UUID(0L, 10L));
when(player.getInventory()).thenReturn(inventory);
when(inventory.getItemInMainHand()).thenReturn(wandStack);
when(wandItem.isWand(wandStack)).thenReturn(true);
when(player.hasPermission("visualwand.use")).thenReturn(true);
when(player.getOpenInventory()).thenReturn(inventoryView);
when(inventoryView.getTopInventory()).thenReturn(topInventory);
when(player.getWorld()).thenReturn(world);
when(player.getEyeLocation()).thenReturn(new Location(world, 0.0D, 64.0D, 0.0D, 0.0F, 0.0F));
when(world.getNearbyEntities(any(Location.class), eq(8.0D), eq(8.0D), eq(8.0D), any()))
        .thenReturn(List.of());
```

Use `@AfterEach` to call `listener.shutdown()` and verify the mocked task can be cancelled.

- [ ] **Step 2: Run the routing tests and confirm the handler is missing**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.listener.WandListenerTest
```

Expected: compilation fails because `onPlayerItemHeld(PlayerItemHeldEvent)` does not exist.

- [ ] **Step 3: Add cycle state and the held-slot handler**

In `WandListener`, add imports for `PlayerItemHeldEvent`, `InventoryView`, and `Predicate`; add:

```java
private final Map<UUID, DisplayCycle> displayCycles = new HashMap<>();
```

Add the handler:

```java
@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
public void onPlayerItemHeld(PlayerItemHeldEvent event) {
    Player player = event.getPlayer();
    DisplayCycle.Direction direction = DisplayCycle.Direction.fromSlots(
            event.getPreviousSlot(), event.getNewSlot());
    if (direction == null
            || !player.isSneaking()
            || !isHoldingWand(player)
            || !player.hasPermission("visualwand.use")
            || player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGUI) {
        return;
    }

    event.setCancelled(true);
    if (plugin.getEditorManager().session(player) != null) {
        plugin.getEditorManager().clear(player);
    }

    Display target = advanceDisplayCycle(player, direction);
    if (target == null) {
        clearDisplayCycle(player.getUniqueId());
        player.sendActionBar(Lang.getComponent("&eNo visible displays within "
                + formatDistance(plugin.getEditorManager().configuration().targetCycleRange())
                + " blocks."));
        return;
    }

    setHoverTarget(player.getUniqueId(), target);
    player.sendActionBar(describeCycleTarget(target, player.getEyeLocation().distance(target.getLocation())));
}
```

Add `advanceDisplayCycle`, `getCycleCandidates`, `resolveCycleTarget`, and `toCycleCandidate` with these contracts:

```java
private Display advanceDisplayCycle(Player player, DisplayCycle.Direction direction) {
    UUID playerId = player.getUniqueId();
    DisplayCycle cycle = displayCycles.get(playerId);
    if (cycle == null) {
        List<DisplayCycle.Candidate> candidates = getCycleCandidates(player);
        if (candidates.isEmpty()) {
            return null;
        }
        Display exact = getTargetedDisplay(player);
        UUID exactId = exact == null ? null : exact.getUniqueId();
        cycle = DisplayCycle.start(candidates, exactId, direction);
        if (cycle == null) {
            return null;
        }
        displayCycles.put(playerId, cycle);
        return resolveCycleTarget(player, cycle.current());
    }

    UUID next = cycle.advance(direction, id -> resolveCycleTarget(player, id) != null);
    return next == null ? null : resolveCycleTarget(player, next);
}

private List<DisplayCycle.Candidate> getCycleCandidates(Player player) {
    Location eye = player.getEyeLocation();
    double range = plugin.getEditorManager().configuration().targetCycleRange();
    List<DisplayCycle.Candidate> candidates = new ArrayList<>();
    for (Entity entity : player.getWorld().getNearbyEntities(
            eye, range, range, range, WandListener::isSupportedDisplay)) {
        DisplayCycle.Candidate candidate = toCycleCandidate(player, (Display) entity, eye, range);
        if (candidate != null) {
            candidates.add(candidate);
        }
    }
    return candidates;
}

private Display resolveCycleTarget(Player player, UUID displayId) {
    Entity resolved = plugin.getServer().getEntity(displayId);
    if (!(resolved instanceof Display display)) {
        return null;
    }
    Location eye = player.getEyeLocation();
    double range = plugin.getEditorManager().configuration().targetCycleRange();
    return toCycleCandidate(player, display, eye, range) == null ? null : display;
}

private DisplayCycle.Candidate toCycleCandidate(
        Player player, Display display, Location eye, double range) {
    if (!isSupportedDisplay(display)
            || !display.isValid()
            || !display.getWorld().getKey().equals(player.getWorld().getKey())
            || !TransformationOperations.isFinite(display.getLocation())
            || !TransformationOperations.isFinite(display.getTransformation())
            || !player.hasLineOfSight(display)) {
        return null;
    }
    Vector offset = display.getLocation().toVector().subtract(eye.toVector());
    DisplayCycle.Candidate candidate = DisplayCycle.candidate(
            display.getUniqueId(), normalizedDirection(eye), offset);
    return candidate != null && candidate.distanceSquared() <= range * range
            ? candidate
            : null;
}

private static boolean isSupportedDisplay(Entity entity) {
    return entity instanceof BlockDisplay
            || entity instanceof ItemDisplay
            || entity instanceof TextDisplay;
}
```

Use one helper for numeric feedback formatting and add a cycling instruction to the action bar:

```java
private Component describeCycleTarget(Display display, double distance) {
    return describeDisplay(display, distance).append(
            Lang.getComponent(" &8| &eShift+scroll to cycle &8| &aRMB to select"));
}

private static String formatDistance(double distance) {
    return String.format(java.util.Locale.ROOT, "%.1f", distance);
}
```

- [ ] **Step 4: Integrate cycle validation with hover and RMB**

At the start of `updateHoverTarget`, replace every invalid-context `clearHover(...)` path with `clearDisplayCycle(...)`. Move the feedback-interval lookup before target resolution, then resolve an active cycle before strict ray targeting:

```java
int feedbackInterval = plugin.getEditorManager()
        .configuration()
        .feedbackUpdateIntervalTicks();
DisplayCycle cycle = displayCycles.get(player.getUniqueId());
if (cycle != null) {
    Display target = resolveCycleTarget(player, cycle.current());
    if (target == null) {
        clearDisplayCycle(player.getUniqueId());
        return;
    }
    setHoverTarget(player.getUniqueId(), target);
    if (hoverTicks % feedbackInterval == 0L) {
        player.sendActionBar(describeCycleTarget(
                target, player.getEyeLocation().distance(target.getLocation())));
    }
    return;
}
```

In `onPlayerInteract`, resolve a preview before clearing hover. Do not fall through to creation if a stale preview existed:

```java
UUID playerId = player.getUniqueId();
DisplayCycle cycle = displayCycles.get(playerId);
boolean hadCyclePreview = cycle != null;
Display display = hadCyclePreview
        ? resolveCycleTarget(player, cycle.current())
        : getTargetedDisplay(player);
clearDisplayCycle(playerId);

if (display == null) {
    if (hadCyclePreview) {
        player.sendActionBar(Lang.getComponent("&eThat display is no longer available."));
    } else {
        openCreateMenu(player);
    }
    return;
}
```

Add lifecycle cleanup:

```java
private void clearDisplayCycle(UUID playerId) {
    displayCycles.remove(playerId);
    clearHover(playerId);
}

public void reload() {
    displayCycles.clear();
    for (UUID playerId : List.copyOf(hoverTargets.keySet())) {
        clearHover(playerId);
    }
}
```

`onPlayerQuit` calls `clearDisplayCycle`. `shutdown` clears `displayCycles` after restoring highlights. A player with an unexpected active editor session, lost permission, a VisualWand GUI, or no wand must have both cycle and hover state cleared by the hover tick.

- [ ] **Step 5: Run listener and cycle tests**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.listener.WandListenerTest \
  --tests dev.kiddo.visualwand.listener.DisplayCycleTest
```

Expected: all listener routing and cycle-model tests pass.

- [ ] **Step 6: Commit listener integration**

```bash
git add src/main/java/dev/kiddo/visualwand/listener/WandListener.java \
  src/test/java/dev/kiddo/visualwand/listener/WandListenerTest.java
git commit -m "feat: cycle visible displays with shift scroll"
```

### Task 4: GUI-Only Deletion and Reload Lifecycle

**Files:**
- Modify: `src/main/java/dev/kiddo/visualwand/listener/WandListener.java`
- Modify: `src/main/java/dev/kiddo/visualwand/VisualWand.java`
- Modify: `src/main/java/dev/kiddo/visualwand/util/WandItem.java`
- Modify: `src/main/resources/config.yml`
- Modify: `README.md`

- [ ] **Step 1: Remove the wand deletion path at the source**

Delete from `WandListener`:

- `deleteConfirmations` and the `DeleteConfirmation` record;
- the sneaking branch in `onPlayerInteract`;
- all `clearDeleteConfirmation` calls;
- `pruneDeleteConfirmations()` from `tickHover`;
- `handleDeleteDisplay`, `armDeleteConfirmation`, `deleteDisplay`, `clearDeleteConfirmation`, `pruneDeleteConfirmations`, `deleteConfirmationTimeoutMillis`, and `formatSeconds`;
- `clearHoverOfDisplay` if it has no remaining caller; and
- imports made unused by those removals.

Do not change `EditMenuGUI` slot 27: its TNT deletion action remains the only deletion entry point.

- [ ] **Step 2: Clear cycle state on plugin reload**

In `VisualWand.reload()`, clear listener targeting before the configuration snapshot changes:

```java
public void reload() {
    editorManager.clearAll("Display editing was cleared by configuration reload.");
    wandListener.reload();
    reloadConfig();
    editorManager.reload();
    wandItem.reload();
}
```

- [ ] **Step 3: Update wand guidance**

In `WandItem.createWandItem()`, replace the hard-coded lore with:

```java
meta.lore(Lang.getComponents(List.of(
        "&7",
        "&fRMB in air: &eCreate objects",
        "&fRMB on object: &eEdit",
        "&fShift + scroll: &eCycle nearby objects",
        "&7")));
```

In `config.yml`, replace the deletion lore line with:

```yaml
    - "&fShift + scroll &8→ &eCycle nearby objects"
```

- [ ] **Step 4: Update English and Polish README behavior**

Document these exact user contracts in both language sections:

- Shift+scroll cycles visible displays in front of the player within the configured eight-block default and keeps the wand selected.
- Right click opens the highlighted display.
- Starting a cycle exits the current display selection.
- Deletion is performed with the TNT control in the edit GUI.
- Configuration examples include `editor.targeting.cycle-range: 8.0` and contain no delete-confirmation setting.

Replace the English deletion section with:

```markdown
### Deleting Objects
- Select the object, then use the **TNT Delete** control in its edit menu.
```

Replace the Polish section with:

```markdown
### Usuwanie obiektów
- Wybierz obiekt, a następnie użyj opcji **Usuń (TNT)** w menu edycji.
```

- [ ] **Step 5: Run focused regression tests and compile**

Run:

```bash
./gradlew test --tests dev.kiddo.visualwand.listener.WandListenerTest \
  --tests dev.kiddo.visualwand.listener.DisplayCycleTest \
  --tests dev.kiddo.visualwand.editor.EditorConfigurationTest
./gradlew compileJava
```

Expected: focused tests pass; production sources compile with no stale deletion references or unused imports.

- [ ] **Step 6: Commit the clean cutover**

```bash
git add src/main/java/dev/kiddo/visualwand/listener/WandListener.java \
  src/main/java/dev/kiddo/visualwand/VisualWand.java \
  src/main/java/dev/kiddo/visualwand/util/WandItem.java \
  src/main/resources/config.yml README.md
git commit -m "refactor: make display deletion GUI only"
```

### Task 5: End-to-End Verification

**Files:**
- No new files.

- [ ] **Step 1: Run the complete automated suite**

```bash
./gradlew test
```

Expected: all existing and new JUnit tests pass.

- [ ] **Step 2: Build the production plugin artifact**

```bash
./gradlew build
```

Expected: `BUILD SUCCESSFUL` and a plugin jar under `build/libs/`.

- [ ] **Step 3: Start Paper for the plugin-enable smoke test**

Run the project-scoped server process with `./gradlew runServer`; wait for both the VisualWand enable log and Paper's `Done` readiness log. Inspect logs for listener registration, configuration, or startup exceptions, then stop the server cleanly.

Expected evidence:

```text
VisualWand has been enabled!
Done (...s)! For help, type "help"
```

- [ ] **Step 4: Perform final repository-wide contract checks**

Search production sources, resources, and README for `DeleteConfirmation`, `delete-confirmation`, and Shift+RMB deletion wording. Expected: no wand-deletion implementation or documentation remains; unrelated GUI modifier text such as `Shift+RMB: -0.1` in property controls remains unchanged.

Confirm the implementation covers every spec path: both slot directions and hotbar wrap, stable ordering, line of sight, 90-degree cone, range clamp, active-session exit, stale-preview fail-closed behavior, glow cleanup, reload/quit/shutdown cleanup, locked-display handling, RMB confirmation, and GUI-only deletion.
