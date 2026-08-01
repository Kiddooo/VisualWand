# Nearby Display Cycling Design

## Goal

Make nearby Display entities easier to find without weakening the existing exact crosshair targeting. While holding the Architect's Wand, a player can use Shift plus an adjacent hotbar step to cycle a highlighted preview through visible displays near the crosshair. Right click confirms the preview and opens the existing editor.

The feature remains server-only and requires no client mod or custom networking. Paper reports held-slot changes but does not distinguish mouse-wheel input from number-key input. Therefore Shift plus an adjacent number key is indistinguishable from Shift+wheel and also cycles; nonadjacent number-key changes retain normal hotbar behavior.

## Interaction Flow

Cycling is available when the player:

- is online and holding the Architect's Wand in the main hand;
- has `visualwand.use`;
- is not in a VisualWand GUI; and
- produces an adjacent `PlayerItemHeldEvent` while sneaking.

The listener cancels an accepted cycle event, so the held hotbar slot does not change. If the player already has an editor session, the first accepted cycle clears that session and restores its selection feedback before building the candidate ring. Applied edits remain applied, and the retained step preset remains unchanged.

The first cycle step builds one stable candidate snapshot. If exact crosshair targeting currently identifies a candidate, that candidate is the starting position and the requested wheel direction advances to its neighbor. Otherwise forward cycling starts at the highest-ranked candidate and backward cycling starts at the lowest-ranked candidate. Both directions wrap at the ends.

The active candidate glows and is described in the action bar. Right click resolves the cycle candidate before normal ray targeting, clears the cycle preview, selects the display through `EditorManager`, and opens the existing edit menu. When no cycle preview exists, exact crosshair selection and object-creation behavior are unchanged.

If no candidate qualifies, the hotbar change remains cancelled and the player receives a short action-bar message. This deliberate failure does not open the creation menu.

## Candidate Eligibility and Ranking

A cycle candidate must:

- be a valid Block, Item, or Text Display in the player's current world;
- have finite location and transformation values;
- be no farther than `editor.targeting.cycle-range`, default `8.0` blocks, from the player's eye position;
- be in front of the player's eye plane, with positive alignment to the look direction regardless of the client's FOV setting; and
- have line of sight from the player according to Paper's entity visibility check.

Candidates are ranked by angular closeness to the crosshair, then eye distance, then UUID. The UUID tie-break makes the ring deterministic when geometry is equal. The snapshot stores UUIDs rather than entity references.

A stable snapshot does not reorder from small camera movements. Each wheel step re-resolves UUIDs and skips ineligible entries in the requested direction. The hover tick and RMB confirmation validate the active entry; if it is invalid, they clear the cycle rather than choosing a replacement without wheel input. Removed, moved, occluded, behind-or-perpendicular, out-of-range, wrong-world, and non-finite entries are ineligible. The next accepted Shift+wheel gesture creates a new snapshot from the current view.

## State and Lifecycle

`WandListener` remains the Bukkit event and visual-lifecycle owner. It adds one per-player cycle-state map and handles `PlayerItemHeldEvent`. A small package-private cycle model owns an immutable ordered UUID list and its current index. Pure helpers calculate front-half-space membership, ranking keys, direction, and wraparound so those contracts can be tested without a running server.

Existing reference-counted hover glow state is reused rather than creating a second highlighting system. While a cycle preview exists, the hover task validates and retains it instead of replacing it with strict ray targeting. Clearing the cycle also releases its hover reference and restores the display's original glow when no other player references it.

Cycle state is cleared when:

- the preview is confirmed;
- the player stops holding the wand;
- the player opens a VisualWand GUI;
- the active candidate becomes ineligible;
- the player quits;
- the plugin shuts down; or
- configuration reload clears editor interaction state.

The event treats `newSlot == (previousSlot + 1) % 9` as forward and `newSlot == (previousSlot + 8) % 9` as backward. Other sneaking transitions are not cancelled. Ordinary nonsneaking hotbar changes are never intercepted.

## Configuration

`EditorConfiguration` loads `editor.targeting.cycle-range` once and exposes the validated value. The default is `8.0`. Non-finite or nonpositive values log a warning and fall back to the default; values above `editor.max-distance` are clamped to that maximum. Every cycled display is therefore valid for subsequent editor distance validation.

Paper cannot read each client's FOV setting, so candidate eligibility uses the full forward half-space rather than a fixed view-cone angle. Any line-of-sight display with positive alignment to the player's look direction qualifies; perpendicular and behind-the-player displays do not. There is no FOV configuration.

## GUI-Only Deletion Cutover

Shift+RMB wand deletion is removed completely. `WandListener` no longer owns delete-confirmation state, timeout pruning, deletion helpers, or sneaking right-click routing. `editor.delete-confirmation.timeout-seconds` is removed from the default configuration.

Deletion remains available from `EditMenuGUI`, which is the single user-facing deletion entry point. Wand lore and both English and Polish README instructions remove Shift+RMB deletion guidance. No compatibility alias or hidden shortcut remains.

After the cutover, sneaking right click behaves like ordinary right click for selection. In particular, a player may keep Shift held after cycling and right click to confirm the highlighted preview safely.

## Failure Handling

All candidate identity is server-resolved from UUIDs at use time. A stale snapshot never supplies an entity directly to `EditorManager`. Candidate resolution fails closed and does not fall through to object creation. Locked displays remain discoverable, but confirmation follows the existing locked-display path and opens the unlock UI instead of selecting them.

Permission loss, world changes, invalid entities, and non-finite geometry clear state without mutation. Highlight restoration preserves each display's original glowing value and remains reference-counted across players.

## Verification

Add JUnit 5 contract tests for the pure cycle model and geometry helpers:

- forward half-space inclusion plus perpendicular and behind-player exclusion;
- deterministic angular, distance, and UUID ranking;
- initial selection relative to an exact hover target;
- forward and backward movement;
- slot 0/8 and candidate-list wraparound;
- stale-candidate skipping and exhaustion; and
- stable ordering after snapshot creation.

Add focused listener/manager tests where existing Mockito conventions can observe event cancellation and active-session clearing without reproducing Paper internals. Existing strict hitbox tests remain unchanged because exact crosshair targeting remains the default path.

Run the focused tests, the complete Gradle test and build tasks, and a Paper startup smoke test proving the plugin enables with the new listener and configuration. Startup cannot simulate client wheel input; the automated behavioral proof comes from the extracted cycle contracts and listener routing tests. Final in-game acceptance is: Shift+wheel keeps the wand selected, cycles every line-of-sight display in the player's forward half-space within eight blocks regardless of client FOV, exits an active editor session on the first step, and right click opens the highlighted display without a deletion shortcut.