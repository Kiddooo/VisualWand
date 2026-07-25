# Display Editing Modes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace direct GUI transformation controls and duplicate gizmo sessions with one server-authoritative editor where a selected mode maps left click to increase and right click to decrease.

**Architecture:** `EditorManager` owns one `EditorSession` per player. Declarative `EditMode` metadata feeds a focused operation service, validator, mode GUI, input listener, feedback renderer, and visual gizmo; `WandListener` only targets/selects/creates/deletes displays. Bukkit remains the synchronization authority.

**Tech Stack:** Java 21, Paper 1.21.11/paperweight-userdev, Bukkit Display/Transformation APIs, JOML quaternions/vectors, Adventure components, Gradle Kotlin DSL, JUnit Jupiter 5.

---

## File Structure

**Create**

- `src/main/java/dev/kiddo/visualwand/editor/EditAxis.java` — X/Y/Z axis value type.
- `src/main/java/dev/kiddo/visualwand/editor/EditCategory.java` — translation/rotation/scale/entity-orientation grouping.
- `src/main/java/dev/kiddo/visualwand/editor/EditDirection.java` — positive/negative signed input.
- `src/main/java/dev/kiddo/visualwand/editor/StepPreset.java` — Fine/Normal/Coarse selection.
- `src/main/java/dev/kiddo/visualwand/editor/StepType.java` — translation/rotation/scale step lookup.
- `src/main/java/dev/kiddo/visualwand/editor/EditMode.java` — declarative mode contract and GUI metadata.
- `src/main/java/dev/kiddo/visualwand/editor/EditorConfiguration.java` — validated configuration snapshot.
- `src/main/java/dev/kiddo/visualwand/editor/DisplayState.java` — defensive immutable location/transformation snapshot.
- `src/main/java/dev/kiddo/visualwand/editor/TransformationOperations.java` — pure movement, quaternion, scale, yaw, and pitch operations.
- `src/main/java/dev/kiddo/visualwand/editor/InputRepeatGate.java` — immediate press plus delayed/rate-limited repeated click acceptance.
- `src/main/java/dev/kiddo/visualwand/editor/EditValidator.java` — entity resolution, permission, world, distance, type, and finite-value validation.
- `src/main/java/dev/kiddo/visualwand/editor/EditorFeedback.java` — glow lifecycle and action-bar text.
- `src/main/java/dev/kiddo/visualwand/listener/EditorInputListener.java` — left/right click routing and cancellation of normal actions.
- `src/test/java/dev/kiddo/visualwand/editor/TransformationOperationsTest.java` — transformation contracts.
- `src/test/java/dev/kiddo/visualwand/editor/InputRepeatGateTest.java` — click repetition contracts.
- `src/test/java/dev/kiddo/visualwand/editor/EditModeTest.java` — complete unique mode metadata and step mapping.

**Rewrite/modify**

- `src/main/java/dev/kiddo/visualwand/editor/EditorSession.java` — selected UUID/world, mode, preset, undo, repeat, consumption, and feedback state.
- `src/main/java/dev/kiddo/visualwand/editor/EditorManager.java` — single orchestration/state owner plus existing text input.
- `src/main/java/dev/kiddo/visualwand/gui/TransformMenuGUI.java` — mode/preset/utility menu only.
- `src/main/java/dev/kiddo/visualwand/gui/EditMenuGUI.java` — select session before transform menu; remove independent gizmo toggle/delete state leaks.
- `src/main/java/dev/kiddo/visualwand/listener/WandListener.java` — retain strict targeting/create/delete; remove transform sessions and select through `EditorManager`.
- `src/main/java/dev/kiddo/visualwand/listener/DisplayInteractListener.java` — retain only session lifecycle events or remove after moving them to `EditorInputListener`.
- `src/main/java/dev/kiddo/visualwand/gizmo/GizmoManager.java` — render active editor sessions without owning sessions.
- `src/main/java/dev/kiddo/visualwand/VisualWand.java` — construct/register new components and close sessions on disable/reload.
- `src/main/resources/config.yml` — centralized preset, scale, repeat, feedback, and validation values.
- `README.md` — replace obsolete direct-button/mouse-wheel gizmo controls with mode-menu left/right-click flow.
- `build.gradle.kts` — JUnit Jupiter setup.

**Remove**

- `src/main/java/dev/kiddo/visualwand/gizmo/GizmoAxis.java`
- `src/main/java/dev/kiddo/visualwand/gizmo/GizmoMode.java`
- `src/main/java/dev/kiddo/visualwand/gizmo/GizmoSession.java`

---

### Task 1: Test Foundation and Mode Metadata

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/main/java/dev/kiddo/visualwand/editor/{EditAxis,EditCategory,EditDirection,StepPreset,StepType,EditMode}.java`
- Create: `src/test/java/dev/kiddo/visualwand/editor/EditModeTest.java`

- [ ] **Step 1: Add JUnit Jupiter and enable its engine**

Add inside `dependencies` and `tasks`:

```kotlin
testImplementation(platform("org.junit:junit-bom:5.13.4"))
testImplementation("org.junit.jupiter:junit-jupiter")

test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing metadata contract test**

```java
package dev.kiddo.visualwand.editor;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EditModeTest {
    @Test
    void exposesEveryRequiredModeWithUniqueDisplayNames() {
        Set<String> names = Arrays.stream(EditMode.values())
                .map(EditMode::displayName)
                .collect(Collectors.toSet());
        assertEquals(15, EditMode.values().length);
        assertEquals(15, names.size());
        assertAll(
                () -> assertNotNull(EditMode.MOVE_X),
                () -> assertNotNull(EditMode.LEFT_ROTATION_Z),
                () -> assertNotNull(EditMode.RIGHT_ROTATION_Z),
                () -> assertNotNull(EditMode.UNIFORM_SCALE),
                () -> assertNotNull(EditMode.ENTITY_YAW),
                () -> assertNotNull(EditMode.ENTITY_PITCH));
    }

    @Test
    void categoriesUseTheCorrectStepType() {
        for (EditMode mode : EditMode.values()) {
            StepType expected = switch (mode.category()) {
                case TRANSLATION -> StepType.TRANSLATION;
                case LEFT_ROTATION, RIGHT_ROTATION, ENTITY_ORIENTATION -> StepType.ROTATION;
                case SCALE -> StepType.SCALE;
            };
            assertEquals(expected, mode.stepType(), mode.name());
        }
    }
}
```

- [ ] **Step 3: Run the test and verify the domain types are missing**

Run: `./gradlew test --tests dev.kiddo.visualwand.editor.EditModeTest`

Expected: compilation fails because `EditMode`, `StepType`, and related enums do not exist.

- [ ] **Step 4: Create the mode value types and declarative enum**

```java
public enum EditAxis { X, Y, Z }
public enum EditCategory { TRANSLATION, LEFT_ROTATION, RIGHT_ROTATION, SCALE, ENTITY_ORIENTATION }
public enum EditDirection {
    POSITIVE(1), NEGATIVE(-1);
    private final int sign;
    EditDirection(int sign) { this.sign = sign; }
    public int sign() { return sign; }
}
public enum StepPreset {
    FINE("Fine"),
    NORMAL("Normal"),
    COARSE("Coarse");

    private final String displayName;

    StepPreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
public enum StepType { TRANSLATION, ROTATION, SCALE }
```

Implement `EditMode` with exact entries and fields:

```java
public enum EditMode {
    MOVE_X("Move X", EditCategory.TRANSLATION, EditAxis.X, StepType.TRANSLATION, Material.RED_CONCRETE),
    MOVE_Y("Move Y", EditCategory.TRANSLATION, EditAxis.Y, StepType.TRANSLATION, Material.LIME_CONCRETE),
    MOVE_Z("Move Z", EditCategory.TRANSLATION, EditAxis.Z, StepType.TRANSLATION, Material.BLUE_CONCRETE),
    LEFT_ROTATION_X("Left Rotation X", EditCategory.LEFT_ROTATION, EditAxis.X, StepType.ROTATION, Material.RED_WOOL),
    LEFT_ROTATION_Y("Left Rotation Y", EditCategory.LEFT_ROTATION, EditAxis.Y, StepType.ROTATION, Material.LIME_WOOL),
    LEFT_ROTATION_Z("Left Rotation Z", EditCategory.LEFT_ROTATION, EditAxis.Z, StepType.ROTATION, Material.BLUE_WOOL),
    RIGHT_ROTATION_X("Right Rotation X", EditCategory.RIGHT_ROTATION, EditAxis.X, StepType.ROTATION, Material.RED_CANDLE),
    RIGHT_ROTATION_Y("Right Rotation Y", EditCategory.RIGHT_ROTATION, EditAxis.Y, StepType.ROTATION, Material.LIME_CANDLE),
    RIGHT_ROTATION_Z("Right Rotation Z", EditCategory.RIGHT_ROTATION, EditAxis.Z, StepType.ROTATION, Material.BLUE_CANDLE),
    SCALE_X("Scale X", EditCategory.SCALE, EditAxis.X, StepType.SCALE, Material.RED_DYE),
    SCALE_Y("Scale Y", EditCategory.SCALE, EditAxis.Y, StepType.SCALE, Material.LIME_DYE),
    SCALE_Z("Scale Z", EditCategory.SCALE, EditAxis.Z, StepType.SCALE, Material.BLUE_DYE),
    UNIFORM_SCALE("Uniform Scale", EditCategory.SCALE, null, StepType.SCALE, Material.MAGMA_CREAM),
    ENTITY_YAW("Entity Yaw", EditCategory.ENTITY_ORIENTATION, EditAxis.Y, StepType.ROTATION, Material.COMPASS),
    ENTITY_PITCH("Entity Pitch", EditCategory.ENTITY_ORIENTATION, EditAxis.X, StepType.ROTATION, Material.SPYGLASS);

    private final String displayName;
    private final EditCategory category;
    private final EditAxis axis;
    private final StepType stepType;
    private final Material material;

    EditMode(String displayName, EditCategory category, EditAxis axis,
             StepType stepType, Material material) {
        this.displayName = displayName;
        this.category = category;
        this.axis = axis;
        this.stepType = stepType;
        this.material = material;
    }

    public String displayName() { return displayName; }
    public EditCategory category() { return category; }
    public EditAxis axis() { return axis; }
    public StepType stepType() { return stepType; }
    public Material material() { return material; }
}
```

- [ ] **Step 5: Run the metadata test**

Run: `./gradlew test --tests dev.kiddo.visualwand.editor.EditModeTest`

Expected: PASS, 2 tests.

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts src/main/java/dev/kiddo/visualwand/editor src/test/java/dev/kiddo/visualwand/editor/EditModeTest.java
git commit -m "Add display editing mode metadata"
```

---

### Task 2: Configuration and Transformation Math

**Files:**
- Create: `src/main/java/dev/kiddo/visualwand/editor/EditorConfiguration.java`
- Create: `src/main/java/dev/kiddo/visualwand/editor/DisplayState.java`
- Create: `src/main/java/dev/kiddo/visualwand/editor/TransformationOperations.java`
- Create: `src/test/java/dev/kiddo/visualwand/editor/TransformationOperationsTest.java`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: Write failing pure-operation tests**

Create `TransformationOperationsTest` with a `TransformationOperations(0.01F, 100.0F)` fixture and these concrete contracts:

```java
@Test
void moveXChangesOnlyWorldX() {
    Location moved = operations.move(new Location(null, 0, 2, 3), EditAxis.X, 0.1);
    assertAll(
            () -> assertEquals(0.1, moved.getX(), 1.0e-9),
            () -> assertEquals(2.0, moved.getY(), 1.0e-9),
            () -> assertEquals(3.0, moved.getZ(), 1.0e-9));
}

@Test
void leftRotationPreservesRightQuaternion() {
    Quaternionf right = new Quaternionf().rotateZ(0.4F);
    Transformation result = operations.rotate(
            transformation(new Quaternionf(), right, new Vector3f(1, 1, 1)),
            EditCategory.LEFT_ROTATION, EditAxis.Y, 5.0);
    assertQuaternionEquals(right, result.getRightRotation());
}

@Test
void rightRotationPreservesLeftQuaternion() {
    Quaternionf left = new Quaternionf().rotateX(0.3F);
    Transformation result = operations.rotate(
            transformation(left, new Quaternionf(), new Vector3f(1, 1, 1)),
            EditCategory.RIGHT_ROTATION, EditAxis.Z, -5.0);
    assertQuaternionEquals(left, result.getLeftRotation());
}

@Test
void repeatedMixedAxisRotationsRemainNormalized() {
    Transformation value = transformation(
            new Quaternionf(), new Quaternionf(), new Vector3f(1, 1, 1));
    for (int index = 0; index < 1_000; index++) {
        value = operations.rotate(value, EditCategory.LEFT_ROTATION,
                EditAxis.values()[index % 3], 15.0);
    }
    assertEquals(1.0F, value.getLeftRotation().length(), 1.0e-5F);
}

@Test
void scaleAxisPreservesOtherAxesAndMirrorSign() {
    Transformation result = operations.scale(
            transformation(new Quaternionf(), new Quaternionf(), new Vector3f(-1, 2, 3)),
            EditAxis.X, 0.1);
    assertEquals(new Vector3f(-1.1F, 2.0F, 3.0F), result.getScale());
}

@Test
void scaleCannotCrossZero() {
    Transformation result = operations.scale(
            transformation(new Quaternionf(), new Quaternionf(), new Vector3f(0.1F, 2, 3)),
            EditAxis.X, -0.5);
    assertEquals(0.01F, result.getScale().x, 1.0e-6F);
}

@Test
void uniformScaleChangesEveryMagnitudeEqually() {
    Transformation result = operations.scale(
            transformation(new Quaternionf(), new Quaternionf(), new Vector3f(1, -2, 3)),
            null, -0.5);
    assertEquals(new Vector3f(0.5F, -1.5F, 2.5F), result.getScale());
}

@Test
void yawWrapsAndPitchClamps() {
    Location start = new Location(null, 0, 0, 0, 179.0F, 89.0F);
    Location yaw = operations.rotateEntity(start, EditMode.ENTITY_YAW, 5.0);
    Location pitch = operations.rotateEntity(start, EditMode.ENTITY_PITCH, 5.0);
    assertEquals(-176.0F, yaw.getYaw(), 1.0e-6F);
    assertEquals(90.0F, pitch.getPitch(), 1.0e-6F);
}

@Test
void resetTransformationUsesBukkitIdentity() {
    Transformation reset = operations.resetAll();
    assertEquals(new Vector3f(), reset.getTranslation());
    assertQuaternionEquals(new Quaternionf(), reset.getLeftRotation());
    assertEquals(new Vector3f(1, 1, 1), reset.getScale());
    assertQuaternionEquals(new Quaternionf(), reset.getRightRotation());
}

private static Transformation transformation(
        Quaternionf left, Quaternionf right, Vector3f scale) {
    return new Transformation(new Vector3f(), left, scale, right);
}

private static void assertQuaternionEquals(Quaternionf expected, Quaternionf actual) {
    assertAll(
            () -> assertEquals(expected.x, actual.x, 1.0e-6F),
            () -> assertEquals(expected.y, actual.y, 1.0e-6F),
            () -> assertEquals(expected.z, actual.z, 1.0e-6F),
            () -> assertEquals(expected.w, actual.w, 1.0e-6F));
}
```

- [ ] **Step 2: Run the operation test and verify failure**

Run: `./gradlew test --tests dev.kiddo.visualwand.editor.TransformationOperationsTest`

Expected: compilation fails because operation/configuration types do not exist.

- [ ] **Step 3: Implement immutable snapshots and pure operations**

`DisplayState.capture(Display)` clones `Location`, `Vector3f`, and both `Quaternionf` values. `apply(Display)` teleports only when location differs and calls `setTransformation` only when transformation components differ.

Implement rotation with post-multiplication to preserve current local-axis behavior:

```java
Quaternionf delta = switch (axis) {
    case X -> new Quaternionf().rotateX(radians);
    case Y -> new Quaternionf().rotateY(radians);
    case Z -> new Quaternionf().rotateZ(radians);
};
Quaternionf updated = new Quaternionf(selected).mul(delta).normalize();
```

Implement scale by magnitude without sign crossing:

```java
private float adjustScale(float value, float signedStep, float minimum, float maximum) {
    float sign = value < 0.0F ? -1.0F : 1.0F;
    float magnitude = Math.clamp(Math.abs(value) + signedStep, minimum, maximum);
    return sign * magnitude;
}
```

`ENTITY_YAW` wraps to `(-180, 180]`; `ENTITY_PITCH` clamps to `[-90, 90]`. Reject any non-finite source or result with `IllegalArgumentException`.

Extend `EditMode` so the mode contract owns applicability, signed dispatch, and feedback formatting rather than leaking mode switches into listeners or GUI code:

```java
public boolean supports(Display display) {
    return display instanceof BlockDisplay
            || display instanceof ItemDisplay
            || display instanceof TextDisplay;
}

public DisplayState apply(TransformationOperations operations, DisplayState state,
                          double step, EditDirection direction) {
    double signedStep = step * direction.sign();
    return switch (category) {
        case TRANSLATION -> state.withLocation(
                operations.move(state.location(), axis, signedStep));
        case LEFT_ROTATION, RIGHT_ROTATION -> state.withTransformation(
                operations.rotate(state.transformation(), category, axis, signedStep));
        case SCALE -> state.withTransformation(
                operations.scale(state.transformation(), axis, signedStep));
        case ENTITY_ORIENTATION -> state.withLocation(
                operations.rotateEntity(state.location(), this, signedStep));
    };
}

public String formatCurrent(DisplayState state) {
    return switch (category) {
        case TRANSLATION -> String.format(Locale.ROOT, "%s %.3f", axis,
                switch (axis) {
                    case X -> state.location().getX();
                    case Y -> state.location().getY();
                    case Z -> state.location().getZ();
                });
        case SCALE -> String.format(Locale.ROOT, "X %.3f Y %.3f Z %.3f",
                state.transformation().getScale().x,
                state.transformation().getScale().y,
                state.transformation().getScale().z);
        case ENTITY_ORIENTATION -> String.format(Locale.ROOT, "%s %.1f°",
                this == ENTITY_YAW ? "Yaw" : "Pitch",
                this == ENTITY_YAW ? state.location().getYaw() : state.location().getPitch());
        case LEFT_ROTATION, RIGHT_ROTATION -> "Quaternion normalized";
    };
}
```

`DisplayState.withLocation` and `withTransformation` construct fresh defensive snapshots. `EditValidator` calls `mode.supports(display)` before dispatch. `EditorFeedback` calls `mode.formatCurrent(state)` and appends the signed step, so every mode supplies its own validation and feedback contract.

- [ ] **Step 4: Implement validated centralized configuration**

Load one immutable snapshot with these defaults:

```yaml
editor:
  max-distance: 50.0
  steps:
    translation: { fine: 0.01, normal: 0.1, coarse: 1.0 }
    rotation-degrees: { fine: 1.0, normal: 5.0, coarse: 15.0 }
    scale: { fine: 0.01, normal: 0.1, coarse: 0.5 }
  scale:
    minimum-magnitude: 0.01
    maximum-magnitude: 100.0
  input:
    initial-delay-ticks: 6
    repeat-interval-ticks: 2
    release-gap-ticks: 8
  feedback:
    update-interval-ticks: 10
    glow-selected: true
```

Expose `double step(StepType, StepPreset)`, scale bounds, distance, tick intervals, and glow. Each loader accepts only finite positive values (non-negative only where zero is valid), logs the exact path on fallback, and enforces `maximum >= minimum` and `releaseGap > repeatInterval`.

- [ ] **Step 5: Run transformation tests**

Run: `./gradlew test --tests dev.kiddo.visualwand.editor.TransformationOperationsTest`

Expected: PASS for all movement, quaternion, scale, entity-angle, and reset contracts.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/kiddo/visualwand/editor src/test/java/dev/kiddo/visualwand/editor/TransformationOperationsTest.java src/main/resources/config.yml
git commit -m "Add safe display transformation operations"
```

---

### Task 3: Input Repeat Gate

**Files:**
- Create: `src/main/java/dev/kiddo/visualwand/editor/InputRepeatGate.java`
- Create: `src/test/java/dev/kiddo/visualwand/editor/InputRepeatGateTest.java`

- [ ] **Step 1: Write failing repeat tests**

```java
@Test void firstClickIsImmediate() { assertTrue(gate.accept(EditDirection.POSITIVE, 100)); }
@Test void secondPulseWaitsForInitialDelay() { gate.accept(POSITIVE, 100); assertFalse(gate.accept(POSITIVE, 101)); assertTrue(gate.accept(POSITIVE, 106)); }
@Test void heldPulsesRespectRepeatInterval() {
    gate.accept(POSITIVE, 100);
    assertTrue(gate.accept(POSITIVE, 106));
    assertFalse(gate.accept(POSITIVE, 107));
    assertTrue(gate.accept(POSITIVE, 108));
}
@Test void releaseGapMakesNextPulseImmediate() { gate.accept(POSITIVE, 100); assertTrue(gate.accept(POSITIVE, 108)); }
@Test void oppositeDirectionHasIndependentImmediatePress() { gate.accept(POSITIVE, 100); assertTrue(gate.accept(NEGATIVE, 101)); }
@Test void sameTickDuplicateIsRejected() { gate.accept(POSITIVE, 100); assertFalse(gate.accept(POSITIVE, 100)); }
```

Instantiate with `(initialDelay=6, repeatInterval=2, releaseGap=8)`.

- [ ] **Step 2: Run and verify failure**

Run: `./gradlew test --tests dev.kiddo.visualwand.editor.InputRepeatGateTest`

Expected: compilation fails because `InputRepeatGate` does not exist.

- [ ] **Step 3: Implement the tick-based gate**

Store per-direction `firstTick`, `lastPulseTick`, and `lastAcceptedTick`. A gap greater than or equal to `releaseGapTicks` resets that direction. First pulse returns true, pre-delay pulses false, and later pulses return true only when `tick - lastAcceptedTick >= repeatIntervalTicks`. Update `lastPulseTick` for every pulse.


- [ ] **Step 4: Run repeat and metadata tests**

Run: `./gradlew test --tests 'dev.kiddo.visualwand.editor.InputRepeatGateTest' --tests 'dev.kiddo.visualwand.editor.EditModeTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kiddo/visualwand/editor/InputRepeatGate.java src/test/java/dev/kiddo/visualwand/editor/InputRepeatGateTest.java
git commit -m "Add deterministic editor input repetition"
```

---

### Task 4: Validation, Manager, History, and Clipboard

**Files:**
- Create: `src/main/java/dev/kiddo/visualwand/editor/EditValidator.java`
- Create: `src/main/java/dev/kiddo/visualwand/editor/EditorFeedback.java`
- Rewrite: `src/main/java/dev/kiddo/visualwand/editor/EditorManager.java`
- Rewrite: `src/main/java/dev/kiddo/visualwand/editor/EditorSession.java`
- Modify: `src/main/java/dev/kiddo/visualwand/listener/WandListener.java`
- Remove: `src/main/java/dev/kiddo/visualwand/listener/DisplayInteractListener.java`
- Modify: `src/main/java/dev/kiddo/visualwand/VisualWand.java`

- [ ] **Step 1: Rewrite `EditorSession` as the sole state record**

Implement this API:

```java
public final class EditorSession {
    public EditorSession(Player player, Display display, StepPreset preset,
                         EditorConfiguration configuration);
    public UUID playerId();
    public UUID displayId();
    public NamespacedKey worldKey();
    public StepPreset preset();
    public void setPreset(StepPreset preset);
    public EditMode mode();
    public void setMode(EditMode mode);
    public boolean hasActiveMode();
    public boolean accept(EditDirection direction, long currentTick);
    public boolean consumingInput();
    public void markInputConsumed(long currentTick);
    public void clearConsumptionIfIdle(long currentTick);
    public DisplayState undoState();
    public void rememberUndo(DisplayState state);
    public DisplayState takeUndo();
    public String lastFeedback();
    public void setLastFeedback(String text);
}
```

Remove `GizmoMode`, mouse-look transformation, original whole-session rollback, and plugin dependency. Mode selection resets the gate but not the undo state; entity selection creates a fresh session. `markInputConsumed` records every valid active-session pulse, including throttled repeats; `clearConsumptionIfIdle` resets the flag after the configured release gap.

- [ ] **Step 2: Implement validator result and finite checks**

Use a sealed-style record result:

```java
public record ValidationResult(Display display, Component error) {
    public boolean valid() { return display != null; }
    public static ValidationResult success(Display display) { return new ValidationResult(display, null); }
    public static ValidationResult failure(Component error) { return new ValidationResult(null, error); }
}
```

`validate(Player, EditorSession)` resolves `plugin.getServer().getEntity(session.displayId())`, requires `Display`, matching `worldKey`, `isValid()`, `visualwand.use`, same player world, squared distance within configured maximum, and finite `DisplayState.capture`. Return a specific Adventure error for each failure.

- [ ] **Step 3: Implement feedback and glow restoration**

`EditorFeedback.select` uses a display-UUID keyed reference count containing the original glow value. `clear` decrements the count and restores the original value only after the final editor leaves, matching the existing hover-highlight safety. `send` formats:

```text
Block Display #a1b2c3 | Left Rotation Y | Step 5° | Last +5° | Drop: menu
```

For move show the changed world coordinate; scale shows XYZ; entity orientation shows yaw/pitch; quaternion modes show signed last delta and never Euler-decompose the quaternion.

- [ ] **Step 4: Rewrite `EditorManager` around one session map**

Preserve `pendingInputs` and chat input methods. Add:

```java
public EditorSession select(Player player, Display display);
public Optional<EditorSession> session(Player player);
public Optional<Display> selectedDisplay(Player player);
public Collection<EditorSession> sessions();
public boolean hasActiveMode(Player player);
public boolean shouldConsume(Player player);
public void selectMode(Player player, EditMode mode);
public void selectPreset(Player player, StepPreset preset);
public boolean applyInput(Player player, EditDirection direction);
public boolean undo(Player player);
public boolean resetTranslation(Player player);
public boolean resetRotations(Player player);
public boolean resetScale(Player player);
public boolean resetTransformation(Player player);
public boolean copyTransformation(Player player);
public boolean pasteTransformation(Player player);
public void clear(Player player, Component reason);
public void clearEditorsOf(UUID displayId, Component reason);
public void clearAll();
public void reload();
public void shutdown();
public void tick();
```

`applyInput` returns false when no active mode exists or validation says the player is no longer allowed to edit; those paths leave the click normal after clearing stale state. It returns true for every valid active-session click, including a pulse suppressed by the repeat gate, so held input cannot leak into normal gameplay between edits. For an accepted pulse it captures the before state, calculates with `TransformationOperations`, skips equal results, applies once, stores undo, and sends feedback. Clipboard uses `Map<UUID, Transformation>` and deep copies on both copy and paste. The manager owns a monotonic tick incremented by `tick()`; input never uses mutable world time.

- [ ] **Step 5: Implement deliberate utilities through the same write path**

Every reset/paste validates first, captures `DisplayState`, computes a new state, skips equality, applies, and replaces the one-level undo. `resetRotations` identities both transform quaternions only. `resetTransformation` uses zero translation, identity/identity rotation, and `(1,1,1)` scale while retaining world location/yaw/pitch. `undo` swaps current and stored state, permitting one-step toggle without an unbounded history.

- [ ] **Step 6: Start manager maintenance task**

In the constructor, schedule `tick()` every tick. `tick()` validates all sessions, clears invalid sessions safely, and sends action bars at `feedback.update-interval-ticks`; manager shutdown cancels the task and clears all glows.

- [ ] **Step 7: Migrate manager callsites and compile**

Replace `EditorManager.getSession/createSession/removeSession` usages in `WandListener` with `session/select/clear`. Remove `DisplayInteractListener`; its mouse-look transformation lifecycle is obsolete, and quit/world cleanup moves to `EditorInputListener` in Task 6. Remove its bootstrap registration. Do not change WandListener's old gizmo implementation yet.

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/kiddo/visualwand/editor src/main/java/dev/kiddo/visualwand/listener/WandListener.java src/main/java/dev/kiddo/visualwand/listener/DisplayInteractListener.java src/main/java/dev/kiddo/visualwand/VisualWand.java
git commit -m "Add authoritative display editor manager"
```

---

### Task 5: Mode Menu and Persistent Visual Feedback

**Files:**
- Rewrite: `src/main/java/dev/kiddo/visualwand/gui/TransformMenuGUI.java`
- Modify: `src/main/java/dev/kiddo/visualwand/gui/EditMenuGUI.java`

- [ ] **Step 1: Replace direct transform buttons with a slot-to-mode table**

Use these stable slots:

```java
private static final Map<Integer, EditMode> MODES = Map.ofEntries(
    entry(10, MOVE_X), entry(11, MOVE_Y), entry(12, MOVE_Z),
    entry(19, LEFT_ROTATION_X), entry(20, LEFT_ROTATION_Y), entry(21, LEFT_ROTATION_Z),
    entry(23, RIGHT_ROTATION_X), entry(24, RIGHT_ROTATION_Y), entry(25, RIGHT_ROTATION_Z),
    entry(28, SCALE_X), entry(29, SCALE_Y), entry(30, SCALE_Z), entry(31, UNIFORM_SCALE),
    entry(32, ENTITY_YAW), entry(33, ENTITY_PITCH));
```

Slots 37/38/39 select Fine/Normal/Coarse. Use slot 13 for Undo, 14 for Copy, 15 for Paste, 16 for Cancel Editing, 22 for Deselect Entity, 40 for Reset Transform Translation, 41 for Reset Rotations, 42 for Reset Scale, and 43 for Reset Entire Transformation. All 28 interior slots are deliberate controls; no mode item mutates a display.

- [ ] **Step 2: Implement exact click behavior**

```java
EditMode mode = MODES.get(slot);
if (mode != null) {
    plugin.getEditorManager().selectMode(player, mode);
    player.closeInventory();
    return;
}
```

Preset clicks call `selectPreset` and refresh. Utility clicks call manager methods and refresh only if the session still exists. Cancel closes immediately. Mode lore states `Left click: +` and `Right click: -` plus the active category-specific step.

- [ ] **Step 3: Make Edit Menu selection authoritative**

When slot 11 opens transformations, call `editorManager.select(player, display)` before opening `TransformMenuGUI`. Remove the independent gizmo toggle. Delete still stops animation, then calls `editorManager.clear` before `display.remove()`.

- [ ] **Step 4: Compile the mode menu**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kiddo/visualwand/gui
git commit -m "Convert transform GUI to mode selection"
```

---

### Task 6: Left and Right Click Input Interception

**Files:**
- Create: `src/main/java/dev/kiddo/visualwand/listener/EditorInputListener.java`
- Modify: `src/main/java/dev/kiddo/visualwand/VisualWand.java`

- [ ] **Step 1: Route air/block clicks once through PlayerInteractEvent**

Register the handler at `EventPriority.LOWEST`. Filter main hand. For `LEFT_CLICK_AIR`/`LEFT_CLICK_BLOCK`, request positive; for `RIGHT_CLICK_AIR`/`RIGHT_CLICK_BLOCK`, request negative. Call the manager before cancellation:

```java
if (!manager.applyInput(player, direction)) {
    return;
}
event.setCancelled(true);
event.setUseInteractedBlock(Event.Result.DENY);
event.setUseItemInHand(Event.Result.DENY);
```

Mark WandListener's normal interact handler `ignoreCancelled = true`, so an active edit consumed at LOWEST never also opens, deletes, or creates through the wand listener.

- [ ] **Step 2: Handle entity-targeted clicks without duplicate operation paths**

`EntityDamageByEntityEvent` with a player damager requests positive and cancels damage only when `applyInput` returns true. `PlayerInteractEntityEvent`/`PlayerInteractAtEntityEvent` in main hand requests negative and cancels interaction only when `applyInput` returns true. The repeat gate rejects duplicate same-tick subclass/base delivery. Cancellation-only `BlockDamageEvent` and `BlockBreakEvent` call `manager.shouldConsume(player)` and cancel only for a currently valid, permitted active session; they never call `applyInput`.

- [ ] **Step 3: Reopen menu through the wand Drop control**

Handle `PlayerDropItemEvent`. When a session exists and the dropped stack is the wand, cancel the drop and open `TransformMenuGUI` for the currently validated display. Do not require an active mode; this lets a selected-but-not-yet-active session recover its menu.

- [ ] **Step 4: Handle lifecycle invalidation**

On `PlayerQuitEvent` clear silently. On `PlayerChangedWorldEvent` clear with an action-bar reason. On permission loss the manager maintenance tick clears the session. Explicit cancel/deselect is already routed through the menu.

- [ ] **Step 5: Register the listener**

Construct one `EditorInputListener(plugin)` in `VisualWand.onEnable()` after `EditorManager`, and register it. Ensure plugin disable calls `editorManager.shutdown()` before gizmo shutdown.

- [ ] **Step 6: Compile input integration**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/kiddo/visualwand/listener/EditorInputListener.java src/main/java/dev/kiddo/visualwand/VisualWand.java
git commit -m "Route active editor clicks to display modes"
```

---

### Task 7: Wand Selection and Legacy State Removal

**Files:**
- Rewrite: `src/main/java/dev/kiddo/visualwand/listener/WandListener.java`
- Rewrite: `src/main/java/dev/kiddo/visualwand/gizmo/GizmoManager.java`
- Remove: `src/main/java/dev/kiddo/visualwand/gizmo/{GizmoAxis,GizmoMode,GizmoSession}.java`
- Modify: `src/main/java/dev/kiddo/visualwand/VisualWand.java`

- [ ] **Step 1: Delete transformation ownership from WandListener**

Remove all `TransformSession` fields/types, transform-control configuration fields, mouse-wheel/drop transform handlers, start/update/confirm/cancel methods, quaternion/scale helpers, and reflection into `GizmoManager`. Keep strict block-shape targeting, hover targeting, create menu, edit selection, and two-step deletion.

- [ ] **Step 2: Fix and simplify normal wand routing**

The main-hand guard must be `if (!isHoldingWand(player)) return;`. Normal right click with permission selects a targeted display through:

```java
plugin.getEditorManager().select(player, display);
new EditMenuGUI(plugin, player, display).open();
```

Crouch-right-click deletion remains two-step; before actual removal call `editorManager.clearEditorsOf(display.getUniqueId(), reason)` and stop animation. A selected entity alone does not consume clicks because `EditorInputListener.applyInput` returns false until a mode is active.

- [ ] **Step 3: Preserve hover without fighting selected glow**

Disable hover updates while `editorManager.session(player).isPresent()` or a VisualWand GUI is open. Reuse the existing reference-counted original-glow restoration for idle targeting. Remove every old gizmo condition.

- [ ] **Step 4: Convert GizmoManager into a read-only renderer**

Remove its map and public start/stop/set/cycle methods. Each configured render tick iterates `editorManager.sessions()`, resolves the selected display, and draws only for active modes. Map translation to axis arrows, either quaternion category/entity orientation to rotation circles, and scale to scale handles; draw the selected axis with its configured color and uniform scale with the scale color. `stop()` only cancels its scheduler task. Delete `GizmoAxis.java`, `GizmoMode.java`, and `GizmoSession.java`, remove their imports, and rename the bootstrap shutdown call from `stopAllGizmos()` to `stop()`.

- [ ] **Step 5: Prove the old architecture is gone**

Search for `TransformSession|GizmoSession|GizmoMode|startTransformation|updateTransformation|moveDisplay|rotateDisplay|scaleDisplay|handleClick\(Player` across `src/main/java`.

Expected: no matches except descriptive migration text outside source.

- [ ] **Step 6: Compile the complete main source set**

Run: `./gradlew compileJava`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/kiddo/visualwand/listener src/main/java/dev/kiddo/visualwand/VisualWand.java
git commit -m "Remove legacy gizmo transformation state"
```

---

### Task 8: Configuration, User-Facing Instructions, and Reload

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `README.md`
- Modify: `src/main/java/dev/kiddo/visualwand/VisualWand.java`
- Modify: `src/main/java/dev/kiddo/visualwand/editor/EditorManager.java`

- [ ] **Step 1: Remove obsolete transform settings**

Delete `editor.move-sensitivity`, `editor.rotate-sensitivity`, `editor.scale-sensitivity`, `gizmo.move-preview`, and `gizmo.transform-controls`. Keep particle density/size/update interval and colors used by the read-only renderer. Add only the validated settings from Task 2.

- [ ] **Step 2: Make reload rebuild editor configuration safely**

`VisualWand.reload()` must clear active sessions first, call `reloadConfig()`, rebuild/reload `EditorConfiguration` through `EditorManager.reload()`, then reload the wand item. Do not let old sessions retain stale limits or repeat intervals.

- [ ] **Step 3: Update player-facing instructions**

Replace README and wand lore references to direct transform buttons, wheel/Q gizmo transformation, and hard-coded RMB/LMB wording with:

```text
Select a display → Transformations → choose a mode.
Left click increases; right click decreases.
Use the Drop control while holding the wand to reopen the mode menu.
Fine, Normal, and Coarse persist until changed.
```

Retain creation, deletion, animation, and entity property documentation.

- [ ] **Step 4: Process resources and inspect expanded YAML**

Run: `./gradlew processResources`

Expected: BUILD SUCCESSFUL and no YAML expansion error.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/config.yml README.md src/main/java/dev/kiddo/visualwand/VisualWand.java src/main/java/dev/kiddo/visualwand/editor/EditorManager.java
git commit -m "Document and configure click editing controls"
```

---

### Task 9: Verification and Specification Audit

**Files:**
- Test: `src/test/java/dev/kiddo/visualwand/editor/*.java`
- Review: all changed source/configuration files

- [ ] **Step 1: Run focused contracts**

Run:

```bash
./gradlew test --tests 'dev.kiddo.visualwand.editor.*'
```

Expected: all metadata, transformation, and repeat-gate tests PASS.

- [ ] **Step 2: Run full project verification**

Run:

```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL with test and compilation tasks passing.

- [ ] **Step 3: Start Paper and verify plugin enablement**

Run the Paper server through the harness process manager using `./gradlew runServer`. Wait for both the Paper done banner and `VisualWand has been enabled!`, then stop it gracefully.

Expected: no VisualWand exception, listener registration error, invalid configuration warning for defaults, or scheduler error.

- [ ] **Step 4: Audit behavior against the approved spec**

Verify source has exactly 15 modes; mode clicks close the GUI; presets persist; only active modes consume clicks; positive/negative routes are unique; utilities never route through world clicks; all validation happens before writes; block/item/text share the same operations; properties/animations remain separate; transformation writes skip equality; mode clears on invalid entity/world/quit/permission/cancel; selected glow restores; and no old transform session or direct GUI transform method remains.

- [ ] **Step 5: Record the in-game boundary accurately**

Do not claim automated mouse interaction was exercised unless a Minecraft client was connected. Report server startup and unit/build evidence separately from the manual in-game click scenario.

- [ ] **Step 6: Commit any verification-only corrections, then ensure a clean build**

```bash
git add src/main src/test build.gradle.kts README.md
git commit -m "Finish display click editing refactor"
./gradlew build
```

Expected: either the commit reports the final corrections or there is nothing to commit; final build is successful.
