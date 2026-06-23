package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gizmo.GizmoManager;
import dev.kiddo.visualwand.gizmo.GizmoMode;
import dev.kiddo.visualwand.gizmo.GizmoSession;
import dev.kiddo.visualwand.gui.EditMenuGUI;
import dev.kiddo.visualwand.gui.BaseGUI;
import dev.kiddo.visualwand.gui.MainMenuGUI;
import dev.kiddo.visualwand.util.RayTraceUtil;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Coordinates all wand input used to create, edit, delete, and transform display entities.
 *
 * The listener owns the temporary state for active move, scale, and rotation operations.
 * It pauses the normal particle gizmo while an operation is active, highlights the edited
 * display with its glow outline, and restores the previous gizmo state when the operation
 * is confirmed or cancelled.
 *
 * Idle gizmo controls:
 * - Left-click cycles move, rotate, and scale modes.
 * - Right-click starts the selected mode.
 *
 * Active transformation controls:
 * - Move follows the player's aim; the mouse wheel changes depth and Q toggles surface mode.
 * - Scale uses the mouse wheel; crouching enables the configured alternate adjustment factor.
 * - Rotate uses the mouse wheel; Q cycles the X, Y, and Z axes.
 * - Right-click confirms the operation.
 * - Crouch + right-click cancels an active operation.
 * - Crouch + right-click while idle exits gizmo mode.
 */
public final class WandListener implements Listener {
    /** Owning plugin and access point for managers, configuration, language, and scheduling. */
    private final VisualWand plugin;
    /** Maximum ray-trace distance used by the normal wand edit and delete workflow. */
    private final double selectionDistance;
    /** Smallest allowed crosshair depth while moving a display. */
    private final double minMoveDistance;
    /** Largest allowed crosshair depth and surface ray-trace distance. */
    private final double maxMoveDistance;
    /** Number of blocks added or removed for one normal wheel step in move mode. */
    private final double moveScrollStep;
    /** Multiplier applied to move depth while the player is crouching for fine adjustment. */
    private final double moveFineMultiplier;
    /** Clearance applied along a hit face normal to reduce surface clipping. */
    private final double surfaceOffset;
    /** Multiplicative scale factor used for normal wheel input. */
    private final double scaleFactor;
    /** Multiplicative scale factor used while the fine modifier is held. */
    private final double scaleFineFactor;
    /** Minimum absolute scale allowed on any axis. */
    private final float minScale;
    /** Maximum absolute scale allowed on any axis. */
    private final float maxScale;
    /** Rotation angle, in degrees, applied for a normal wheel step. */
    private final double rotationStep;
    /** Rotation angle, in degrees, applied while the fine modifier is held. */
    private final double rotationFineStep;
    /** Scheduler interval, in ticks, for advancing active sessions. */
    private final int sessionUpdateInterval;
    /** Number of session updates between repeated action-bar refreshes. */
    private final int actionBarUpdateInterval;
    /** Whether the active display should glow while the particle gizmo is paused. */
    private final boolean glowWhileTransforming;
    /** Whether the wand highlights the display currently under the player's crosshair. */
    private final boolean targetHighlightEnabled;
    /** Number of scheduler ticks between target-highlight ray traces. */
    private final int targetHighlightUpdateInterval;
    /** How long a pending wand deletion confirmation remains valid, in milliseconds. */
    private final long deleteConfirmationTimeoutMillis;
    /** Axis selected whenever a new rotation session starts. */
    private final Axis initialRotationAxis;
    /** Active transformation state keyed by player UUID. */
    private final Map<UUID, TransformSession> sessions = new HashMap<>();
    /** Last display highlighted for each player while they are aiming with the wand. */
    private final Map<UUID, Display> hoverTargets = new HashMap<>();
    /** Original glow state and viewer count for displays currently highlighted by players. */
    private final Map<Display, HighlightState> highlightStates = new HashMap<>();
    /** Pending crouch-right-click delete confirmations keyed by player UUID. */
    private final Map<UUID, DeleteConfirmation> deleteConfirmations = new HashMap<>();
    /** Optional direct view of GizmoManager sessions used to pause rendering without mode loss. */
    private final Map<UUID, GizmoSession> activeGizmoSessions;
    /** Monotonic scheduler counter used to throttle action-bar refreshes. */
    private long previewTicks;

    /**
     * Creates the listener, validates all configurable transform controls, and starts the
     * repeating session updater used by movement tracking and action-bar feedback.
     *
     * @param plugin owning plugin instance
     */
    public WandListener(VisualWand plugin) {
        this.plugin = plugin;
        this.selectionDistance = plugin.getConfig().getDouble("editor.max-distance", 50.0D);
        this.minMoveDistance = positiveDouble(
                "gizmo.transform-controls.move.min-distance", 0.5D);
        this.maxMoveDistance = Math.max(
                minMoveDistance,
                positiveDouble("gizmo.transform-controls.move.max-distance", 512.0D));
        this.moveScrollStep = positiveDouble(
                "gizmo.transform-controls.move.scroll-step", 1.0D);
        this.moveFineMultiplier = boundedDouble(
                "gizmo.transform-controls.move.fine-multiplier", 0.20D, 0.01D, 1.0D);
        this.surfaceOffset = nonNegativeDouble(
                "gizmo.transform-controls.move.surface-offset", 0.02D);

        this.scaleFactor = greaterThanOne(
                "gizmo.transform-controls.scale.factor", 1.10D);
        this.scaleFineFactor = greaterThanOne(
                "gizmo.transform-controls.scale.fine-factor", 1.02D);
        this.minScale = (float) positiveDouble(
                "gizmo.transform-controls.scale.min", 0.01D);
        this.maxScale = (float) Math.max(
                minScale,
                positiveDouble("gizmo.transform-controls.scale.max", 100.0D));

        this.rotationStep = positiveDouble(
                "gizmo.transform-controls.rotation.step-degrees", 15.0D);
        this.rotationFineStep = positiveDouble(
                "gizmo.transform-controls.rotation.fine-step-degrees", 5.0D);
        this.initialRotationAxis = loadAxis(
                "gizmo.transform-controls.rotation.initial-axis", Axis.Y);

        this.sessionUpdateInterval = positiveInt(
                "gizmo.transform-controls.update-interval", 1);
        this.actionBarUpdateInterval = positiveInt(
                "gizmo.transform-controls.actionbar-update-interval", 8);
        this.glowWhileTransforming = plugin.getConfig().getBoolean(
                "gizmo.transform-controls.glow-while-transforming", true);
        this.targetHighlightEnabled = plugin.getConfig().getBoolean(
                "editor.targeting.highlight-enabled", true);
        this.targetHighlightUpdateInterval = positiveInt(
                "editor.targeting.update-interval", 2);
        this.deleteConfirmationTimeoutMillis = Math.max(
                1_000L,
                Math.round(positiveDouble(
                        "editor.delete-confirmation.timeout-seconds", 5.0D) * 1_000.0D));
        this.activeGizmoSessions = resolveActiveGizmoSessions(plugin.getGizmoManager());

        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickSessions,
                1L,
                sessionUpdateInterval
        );
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    /**
     * Routes primary wand input. Active transformations take precedence over idle gizmo
     * controls, which in turn take precedence over the normal create/edit/delete workflow.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!isHoldingWand(player)) {
            return;
        }

        if (!player.hasPermission("visualwand.use")) {
            player.sendMessage(plugin.getLang().getPrefixed("no-permission"));
            return;
        }

        Action action = event.getAction();
        boolean rightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;

        TransformSession active = sessions.get(player.getUniqueId());
        if (active != null) {
            if (rightClick) {
                clearHover(player);
                event.setCancelled(true);
                if (player.isSneaking()) {
                    cancelTransformation(player, active, true, true);
                } else {
                    confirmTransformation(player, active, true);
                }
            } else if (leftClick) {
                event.setCancelled(true);
            }
            return;
        }

        GizmoManager gizmoManager = plugin.getGizmoManager();
        GizmoSession gizmo = gizmoManager.getSession(player);

        if (gizmo != null && leftClick) {
            event.setCancelled(true);
            clearHover(player);
            clearDeleteConfirmation(player);
            gizmoManager.handleClick(player);
            plugin.getEditorManager().removeSession(player);
            return;
        }

        if (gizmo != null && rightClick) {
            event.setCancelled(true);
            clearHover(player);
            clearDeleteConfirmation(player);

            if (player.isSneaking()) {
                gizmoManager.stopGizmo(player);
                plugin.getEditorManager().removeSession(player);
                player.sendActionBar(plugin.getLang().getColored("gizmo-disabled-actionbar"));
                player.sendMessage(plugin.getLang().getPrefixed("gizmo-disabled"));
            } else {
                startTransformation(player, gizmo);
            }
            return;
        }

        if (rightClick) {
            event.setCancelled(true);
            clearHover(player);

            if (player.isSneaking()) {
                handleDeleteDisplay(player);
                return;
            }

            clearDeleteConfirmation(player);
            Display display = getTargetedDisplay(player);
            if (display != null) {
                event.setCancelled(true);
                clearHover(player);
                openEditMenu(player, display);
            } else {
                openCreateMenu(player);
            }
        }
    }

    /**
     * Converts hotbar scrolling into transform input while preventing the selected hotbar
     * slot from changing. Input is intercepted only while the Architect's Wand remains in
     * the player's main hand. Slot wrap-around is normalised before applying the delta.
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        TransformSession session = sessions.get(player.getUniqueId());
        if (session == null || !isHoldingWand(player)) {
            return;
        }

        event.setCancelled(true);
        int difference = normalizedSlotDifference(event.getPreviousSlot(), event.getNewSlot());
        if (difference == 0) {
            return;
        }

        int direction = difference > 0 ? 1 : -1;
        int steps = Math.max(1, Math.abs(difference));

        if (session.mode == GizmoMode.MOVE) {
            double multiplier = player.isSneaking() ? moveFineMultiplier : 1.0D;
            session.distance = clamp(
                    session.distance + direction * moveScrollStep * multiplier * steps,
                    minMoveDistance,
                    maxMoveDistance);
            session.surfaceTargeting = false;
            updateMoveTarget(player, session);
        } else if (session.mode == GizmoMode.SCALE) {
            applyScale(session, direction, steps, player.isSneaking());
        } else if (session.mode == GizmoMode.ROTATE) {
            applyRotation(session, direction, steps, player.isSneaking());
        }

        sendActionBar(player, session);
    }

    /**
     * Reuses the drop key as a mode-specific secondary control while transforming: move
     * toggles air/surface targeting and rotate cycles the active world axis.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        TransformSession session = sessions.get(player.getUniqueId());
        if (session == null
                || !plugin.getWandItem().isWand(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);

        if (session.mode == GizmoMode.MOVE) {
            session.surfaceTargeting = !session.surfaceTargeting;
            recalculateMoveOffset(player, session);
            updateMoveTarget(player, session);
            player.sendMessage(plugin.getLang().getPrefixed(
                    session.surfaceTargeting
                            ? "gizmo-move-targeting-surface"
                            : "gizmo-move-targeting-air"));
        } else if (session.mode == GizmoMode.ROTATE) {
            session.axis = session.axis.next();
            player.sendMessage(plugin.getLang().getPrefixed(
                    "gizmo-rotation-axis-changed",
                    "axis",
                    localizedAxis(session.axis)));
        }

        sendActionBar(player, session);
    }

    /**
     * Cleans up transformation, editor, and gizmo state when a player disconnects.
     *
     * Cancellation is silent because the player can no longer receive useful feedback.
     *
     * @param event player disconnect event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TransformSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancelTransformation(player, session, false, false);
        }
        clearHover(player);
        clearDeleteConfirmation(player);
        plugin.getEditorManager().removeSession(player);
        plugin.getGizmoManager().stopGizmo(player);
    }

    // -------------------------------------------------------------------------
    // Transformation lifecycle
    // -------------------------------------------------------------------------

    /**
     * Captures a complete rollback snapshot, pauses the particle gizmo, and starts the
     * selected transform mode.
     */
    private void startTransformation(Player player, GizmoSession gizmo) {
        Display display = gizmo.getDisplay();
        if (display == null || !display.isValid()) {
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-display-invalid"));
            plugin.getGizmoManager().stopGizmo(player);
            plugin.getEditorManager().removeSession(player);
            return;
        }

        clearHover(player);
        clearDeleteConfirmation(player);
        plugin.getEditorManager().removeSession(player);
        GizmoSession pausedGizmo = pauseGizmo(player, gizmo);
        TransformSession session = new TransformSession(
                player,
                display,
                gizmo.getMode(),
                display.getLocation().clone(),
                copyTransformation(display.getTransformation()),
                display.isGlowing(),
                pausedGizmo);

        if (glowWhileTransforming) {
            display.setGlowing(true);
        }

        if (session.mode == GizmoMode.MOVE) {
            initialiseMoveSession(player, session);
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-move-started"));
        } else if (session.mode == GizmoMode.SCALE) {
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-scale-started"));
        } else {
            session.axis = initialRotationAxis;
            player.sendMessage(plugin.getLang().getPrefixed(
                    "gizmo-rotation-started",
                    "axis",
                    localizedAxis(session.axis)));
        }

        sessions.put(player.getUniqueId(), session);
        sendActionBar(player, session);
    }

    /**
     * Initialises ray-based movement without snapping the display to the crosshair. The
     * stored offset preserves the point at which the player effectively "grabbed" it.
     */
    private void initialiseMoveSession(Player player, TransformSession session) {
        Location original = session.originalLocation;
        Location eye = player.getEyeLocation();
        Vector direction = normalizedDirection(eye);
        Vector toDisplay = original.toVector().subtract(eye.toVector());
        double projectedDistance = toDisplay.dot(direction);
        if (projectedDistance < minMoveDistance) {
            projectedDistance = eye.distance(original);
        }
        session.distance = clamp(projectedDistance, minMoveDistance, maxMoveDistance);

        Location anchor = eye.clone().add(direction.clone().multiply(session.distance));
        session.moveOffset = original.toVector().subtract(anchor.toVector());
        updateMoveTarget(player, session);
    }

    /**
     * Advances all active sessions, handles invalid state and wand removal, updates moving
     * displays continuously, and refreshes action-bar feedback at the configured cadence.
     */
    private void tickSessions() {
        previewTicks++;
        Iterator<Map.Entry<UUID, TransformSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TransformSession> entry = iterator.next();
            TransformSession session = entry.getValue();
            Player player = session.player;

            if (!player.isOnline() || !session.display.isValid()) {
                if (session.display.isValid()) {
                    restoreOriginalState(session);
                }
                iterator.remove();
                continue;
            }

            if (!isHoldingWand(player)) {
                restoreOriginalState(session);
                restoreGlow(session);
                resumeGizmo(player, session);
                iterator.remove();
                player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-wand-put-away"));
                continue;
            }

            if (session.mode == GizmoMode.MOVE) {
                updateMoveTarget(player, session);
            }

            if (previewTicks % actionBarUpdateInterval == 0L) {
                sendActionBar(player, session);
            }
        }

        if (targetHighlightEnabled && previewTicks % targetHighlightUpdateInterval == 0L) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                updateHoverTarget(player);
            }
        }

        pruneDeleteConfirmations();
    }

    /**
     * Resolves the current movement anchor from either crosshair depth or block ray tracing,
     * reapplies the preserved grab offset, and teleports the display to the resulting target.
     */
    private void updateMoveTarget(Player player, TransformSession session) {
        Location eye = player.getEyeLocation();
        Vector direction = normalizedDirection(eye);
        Location anchor = null;
        boolean surfaceFound = false;

        if (session.surfaceTargeting) {
            RayTraceResult hit = player.rayTraceBlocks(maxMoveDistance, FluidCollisionMode.NEVER);
            if (hit != null && hit.getHitPosition() != null) {
                Vector hitPosition = hit.getHitPosition().clone();
                if (hit.getHitBlockFace() != null) {
                    hitPosition.add(hit.getHitBlockFace().getDirection().clone().multiply(surfaceOffset));
                }
                anchor = new Location(
                        eye.getWorld(),
                        hitPosition.getX(),
                        hitPosition.getY(),
                        hitPosition.getZ());
                surfaceFound = true;
            }
        }

        if (anchor == null) {
            anchor = eye.clone().add(direction.clone().multiply(session.distance));
        }

        Location current = session.display.getLocation();
        Location target = anchor.clone().add(session.moveOffset);
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());

        session.surfaceFound = surfaceFound;
        session.display.teleport(target);
    }

    /**
     * Recomputes the grab offset when switching between air and surface targeting so the
     * display does not jump merely because the targeting model changed.
     */
    private void recalculateMoveOffset(Player player, TransformSession session) {
        Location eye = player.getEyeLocation();
        Location anchor = null;

        if (session.surfaceTargeting) {
            RayTraceResult hit = player.rayTraceBlocks(maxMoveDistance, FluidCollisionMode.NEVER);
            if (hit != null && hit.getHitPosition() != null) {
                Vector hitPosition = hit.getHitPosition().clone();
                if (hit.getHitBlockFace() != null) {
                    hitPosition.add(hit.getHitBlockFace().getDirection().clone().multiply(surfaceOffset));
                }
                anchor = new Location(
                        eye.getWorld(),
                        hitPosition.getX(),
                        hitPosition.getY(),
                        hitPosition.getZ());
            }
        }

        if (anchor == null) {
            anchor = eye.clone().add(normalizedDirection(eye).multiply(session.distance));
        }

        session.moveOffset = session.display.getLocation().toVector().subtract(anchor.toVector());
    }

    /**
     * Applies multiplicative scaling while preserving non-uniform proportions and negative
     * axis signs. Values are clamped to the configured magnitude range.
     */
    private void applyScale(TransformSession session, int direction, int steps, boolean fine) {
        double baseFactor = fine ? scaleFineFactor : scaleFactor;
        double multiplier = Math.pow(baseFactor, direction * steps);

        Transformation current = session.display.getTransformation();
        Vector3f scale = current.getScale();
        Vector3f updatedScale = new Vector3f(
                clampScale((float) (scale.x * multiplier)),
                clampScale((float) (scale.y * multiplier)),
                clampScale((float) (scale.z * multiplier)));

        session.display.setTransformation(new Transformation(
                copyVector(current.getTranslation()),
                copyQuaternion(current.getLeftRotation()),
                updatedScale,
                copyQuaternion(current.getRightRotation())));
    }

    /**
     * Applies a fixed local-axis rotation step to the display left quaternion.
     *
     * The existing rotation is multiplied by the new delta, matching the order used by the
     * transformation GUI. The result is normalised to prevent numerical drift after repeated
     * wheel input.
     */
    private void applyRotation(TransformSession session, int direction, int steps, boolean fine) {
        double degrees = (fine ? rotationFineStep : rotationStep) * direction * steps;
        float radians = (float) Math.toRadians(degrees);

        Quaternionf delta = new Quaternionf();
        if (session.axis == Axis.X) {
            delta.rotateXYZ(radians, 0.0F, 0.0F);
        } else if (session.axis == Axis.Y) {
            delta.rotateXYZ(0.0F, radians, 0.0F);
        } else {
            delta.rotateXYZ(0.0F, 0.0F, radians);
        }

        Transformation current = session.display.getTransformation();
        Quaternionf updatedRotation = copyQuaternion(current.getLeftRotation());
        updatedRotation.mul(delta).normalize();
        session.display.setTransformation(new Transformation(
                copyVector(current.getTranslation()),
                updatedRotation,
                copyVector(current.getScale()),
                copyQuaternion(current.getRightRotation())));
        session.rotationDegrees += degrees;
    }

    /** Sends mode-specific, colourised transform state to the player action bar. */
    private void sendActionBar(Player player, TransformSession session) {
        if (session.mode == GizmoMode.MOVE) {
            String modeKey;
            if (session.surfaceTargeting) {
                modeKey = session.surfaceFound
                        ? "gizmo-move-mode-surface"
                        : "gizmo-move-mode-surface-no-hit";
            } else {
                modeKey = "gizmo-move-mode-air";
            }

            player.sendActionBar(plugin.getLang().getColored(
                    "gizmo-move-actionbar",
                    "distance",
                    format(session.distance),
                    "mode",
                    plugin.getLang().getColored(modeKey)
            ));
            return;
        }

        if (session.mode == GizmoMode.SCALE) {
            Vector3f scale = session.display.getTransformation().getScale();
            player.sendActionBar(plugin.getLang().getColored(
                    "gizmo-scale-actionbar",
                    "x",
                    format(scale.x),
                    "y",
                    format(scale.y),
                    "z",
                    format(scale.z)
            ));
            return;
        }

        player.sendActionBar(plugin.getLang().getColored(
                "gizmo-rotation-actionbar",
                "axis",
                localizedAxis(session.axis),
                "angle",
                formatAngle(session.rotationDegrees)
        ));
    }

    /** Commits the current state, restores the original glow value, and resumes the gizmo. */
    private void confirmTransformation(Player player, TransformSession session, boolean sendMessage) {
        sessions.remove(player.getUniqueId());
        restoreGlow(session);
        resumeGizmo(player, session);
        if (sendMessage) {
            player.sendActionBar(plugin.getLang().getColored("gizmo-transform-saved-actionbar"));
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-confirmed"));
        }
    }

    /**
     * Rolls a session back to its captured location and transformation, optionally restoring
     * the paused gizmo and notifying the player.
     */
    private void cancelTransformation(
            Player player,
            TransformSession session,
            boolean sendMessage,
            boolean resumeGizmo) {
        sessions.remove(player.getUniqueId());
        restoreOriginalState(session);
        restoreGlow(session);
        if (resumeGizmo) {
            resumeGizmo(player, session);
        }
        if (sendMessage) {
            player.sendActionBar(plugin.getLang().getColored("gizmo-transform-cancelled-actionbar"));
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-cancelled"));
        }
    }

    /** Restores both entity location and display transformation from the session snapshot. */
    private void restoreOriginalState(TransformSession session) {
        if (session.display != null && session.display.isValid()) {
            session.display.teleport(session.originalLocation);
            session.display.setTransformation(copyTransformation(session.originalTransformation));
        }
    }

    /** Restores the display's glow state exactly as it was before editing began. */
    private void restoreGlow(TransformSession session) {
        if (session.display != null && session.display.isValid()) {
            session.display.setGlowing(session.originalGlowing);
        }
    }

    /**
     * Temporarily removes the original gizmo session so its particles do not obscure the
     * edited display. Falls back to stopping the public gizmo API when reflection is unavailable.
     */
    private GizmoSession pauseGizmo(Player player, GizmoSession fallback) {
        if (activeGizmoSessions != null) {
            GizmoSession removed = activeGizmoSessions.remove(player.getUniqueId());
            return removed != null ? removed : fallback;
        }

        plugin.getGizmoManager().stopGizmo(player);
        return fallback;
    }

    /**
     * Restores the paused gizmo after a transformation ends.
     *
     * Direct map access preserves the exact session instance and selected mode. If reflection
     * was unavailable, the method recreates the gizmo through the public manager API.
     *
     * @param player owner of the gizmo
     * @param session completed transformation state containing the paused gizmo
     */
    private void resumeGizmo(Player player, TransformSession session) {
        if (!player.isOnline() || session.display == null || !session.display.isValid()) {
            return;
        }

        if (activeGizmoSessions != null && session.pausedGizmo != null) {
            activeGizmoSessions.put(player.getUniqueId(), session.pausedGizmo);
            return;
        }

        plugin.getGizmoManager().startGizmo(player, session.display);
        plugin.getGizmoManager().setMode(player, session.mode);
    }

    /**
     * Resolves GizmoManager's private active-session map. Returning null deliberately
     * degrades to the public stop/start API rather than making transformations unavailable.
     */
    @SuppressWarnings("unchecked")
    private static Map<UUID, GizmoSession> resolveActiveGizmoSessions(GizmoManager manager) {
        try {
            Field field = GizmoManager.class.getDeclaredField("activeSessions");
            field.setAccessible(true);
            return (Map<UUID, GizmoSession>) field.get(manager);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Resolves the localized, colourized label for a rotation axis.
     *
     * @param axis axis whose label should be displayed
     * @return colourized language value for the axis
     */
    private String localizedAxis(Axis axis) {
        return plugin.getLang().getColored("gizmo-axis-" + axis.name().toLowerCase(java.util.Locale.ROOT));
    }

    // -------------------------------------------------------------------------
    // Configuration and general helpers
    // -------------------------------------------------------------------------

    /** Loads a configured axis name and reports invalid values before using the fallback. */
    private Axis loadAxis(String path, Axis fallback) {
        String configured = plugin.getConfig().getString(path, fallback.name());
        try {
            return Axis.valueOf(configured.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(
                    "Invalid axis at " + path + ": " + configured + "; using " + fallback.name());
            return fallback;
        }
    }

    /** Reads a finite positive double, logging and returning the fallback when invalid. */
    private double positiveDouble(String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (Double.isFinite(value) && value > 0.0D) {
            return value;
        }
        plugin.getLogger().warning(
                "Invalid positive number at " + path + ": " + value + "; using " + fallback);
        return fallback;
    }

    /** Reads a finite non-negative double, logging and returning the fallback when invalid. */
    private double nonNegativeDouble(String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (Double.isFinite(value) && value >= 0.0D) {
            return value;
        }
        plugin.getLogger().warning(
                "Invalid non-negative number at " + path + ": " + value + "; using " + fallback);
        return fallback;
    }

    /** Reads a finite scaling factor greater than one. */
    /**
     * Loads a finite double constrained to the supplied inclusive range.
     * Invalid values are replaced with the fallback and reported to the server log.
     */
    private double boundedDouble(String path, double fallback, double minimum, double maximum) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            plugin.getLogger().warning(
                    "Invalid value for " + path + ": " + value
                            + ". Expected " + minimum + " to " + maximum
                            + "; using " + fallback + ".");
            return fallback;
        }
        return value;
    }

    private double greaterThanOne(String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (Double.isFinite(value) && value > 1.0D) {
            return value;
        }
        plugin.getLogger().warning(
                "Invalid scale factor at " + path + ": " + value + "; using " + fallback);
        return fallback;
    }

    /** Reads a positive integer, logging and returning the fallback when invalid. */
    private int positiveInt(String path, int fallback) {
        int value = plugin.getConfig().getInt(path, fallback);
        if (value > 0) {
            return value;
        }
        plugin.getLogger().warning(
                "Invalid positive integer at " + path + ": " + value + "; using " + fallback);
        return fallback;
    }


    // -------------------------------------------------------------------------
    // Target highlighting
    // -------------------------------------------------------------------------

    /**
     * Updates the temporary glow outline for the display under the player's crosshair.
     *
     * Highlighting is intentionally disabled while the player is transforming a display,
     * using an open VisualWand GUI, or working with the particle gizmo. Those states have
     * their own feedback and should not fight with the normal targeting indicator.
     *
     * @param player player whose crosshair target should be checked
     */
    private void updateHoverTarget(Player player) {
        if (!player.isOnline()
                || !isHoldingWand(player)
                || !player.hasPermission("visualwand.use")
                || sessions.containsKey(player.getUniqueId())
                || plugin.getGizmoManager().getSession(player) != null
                || player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGUI) {
            clearHover(player);
            return;
        }

        Display target = getTargetedDisplay(player);
        setHoverTarget(player, target);

        if (target != null && previewTicks % actionBarUpdateInterval == 0L) {
            double distance = player.getEyeLocation().distance(target.getLocation());
            player.sendActionBar(describeDisplay(target, distance));
        }
    }

    /**
     * Changes the highlighted display for a player, restoring the previous display's glow
     * state when no other player is still targeting it.
     *
     * @param player player whose target changed
     * @param target new display target, or null to clear highlighting
     */
    private void setHoverTarget(Player player, Display target) {
        UUID playerId = player.getUniqueId();
        Display previous = hoverTargets.get(playerId);
        if (previous != null && previous.equals(target)) {
            return;
        }

        clearHover(player);

        if (target == null || !target.isValid()) {
            return;
        }

        HighlightState state = highlightStates.get(target);
        if (state == null) {
            state = new HighlightState(target.isGlowing());
            highlightStates.put(target, state);
        } else {
            state.viewers++;
        }

        target.setGlowing(true);
        hoverTargets.put(playerId, target);
    }

    /**
     * Removes the player's temporary target highlight and restores the display's original
     * glow state when this was the last player highlighting it.
     *
     * @param player player whose target highlight should be cleared
     */
    private void clearHover(Player player) {
        Display previous = hoverTargets.remove(player.getUniqueId());
        if (previous == null) {
            return;
        }

        HighlightState state = highlightStates.get(previous);
        if (state == null) {
            return;
        }

        state.viewers--;
        if (state.viewers <= 0) {
            highlightStates.remove(previous);
            if (previous.isValid()) {
                previous.setGlowing(state.originalGlowing);
            }
        }
    }

    /**
     * Finds the nearest display whose selection shape is directly intersected by
     * the player's crosshair ray.
     *
     * Block displays are tested against the vanilla block state's voxel shape,
     * which handles vanilla non-cube blocks such as copper golem statues. Item
     * and text displays keep simple fallback boxes because they do not have a
     * server-side vanilla block shape.
     *
     * This method is the single targeting path for hover, edit, and delete. Do
     * not use RayTraceUtil.rayTraceDisplay for those actions, because that
     * utility is intentionally loose and can select nearby displays that are
     * not actually under the crosshair.
     *
     * @param player player whose view ray should be tested
     * @return nearest intersected display, or null when the ray misses all displays
     */
    private Display getTargetedDisplay(Player player) {
        Location eye = player.getEyeLocation();
        Vector rayStart = eye.toVector();
        Vector rayDirection = normalizedDirection(eye);

        Display bestDisplay = null;
        double bestDistance = selectionDistance;

        World world = player.getWorld();
        for (Entity entity : world.getNearbyEntities(
                eye,
                selectionDistance,
                selectionDistance,
                selectionDistance,
                candidate -> candidate instanceof Display)) {
            Display display = (Display) entity;
            if (!display.isValid()) {
                continue;
            }

            double hitDistance = getDisplayHitDistance(
                    display,
                    rayStart,
                    rayDirection,
                    selectionDistance);

            if (hitDistance >= 0.0D && hitDistance < bestDistance) {
                bestDistance = hitDistance;
                bestDisplay = display;
            }
        }

        return bestDisplay;
    }

    /**
     * Returns the world-space distance from the ray start to the first
     * intersection with the display's selection shape.
     *
     * A negative return value means the ray missed the display.
     */
    private double getDisplayHitDistance(
            Display display,
            Vector rayStart,
            Vector rayDirection,
            double maxDistance) {
        if (display instanceof BlockDisplay blockDisplay) {
            double blockDistance = getBlockDisplayHitDistance(
                    blockDisplay,
                    rayStart,
                    rayDirection,
                    maxDistance);

            if (blockDistance >= 0.0D) {
                return blockDistance;
            }

            // Some blocks may have an empty or unavailable outline shape. Fall
            // back to a conservative one-block box so they remain selectable.
        }

        BoundingBox selectionBox = getSimpleDisplaySelectionBox(display);
        RayTraceResult hit = selectionBox.rayTrace(rayStart, rayDirection, maxDistance);
        if (hit == null || hit.getHitPosition() == null) {
            return -1.0D;
        }

        return hit.getHitPosition().distance(rayStart);
    }

    /**
     * Ray-tests a block display against the vanilla voxel shape of its displayed
     * block state.
     *
     * The player ray is transformed into the display's local coordinate space,
     * each vanilla shape AABB is tested locally, and the nearest hit is converted
     * back to world distance. Testing in local space avoids the overly-large
     * world AABBs created by rotated or non-cube block shapes.
     */
    private double getBlockDisplayHitDistance(
            BlockDisplay display,
            Vector rayStart,
            Vector rayDirection,
            double maxDistance) {
        VoxelShape shape = getBlockDisplayVoxelShape(display);
        if (shape == null || shape.isEmpty()) {
            return -1.0D;
        }

        Location origin = display.getLocation();
        Transformation transformation = display.getTransformation();

        Vector rayEnd = rayStart.clone().add(rayDirection.clone().multiply(maxDistance));
        Vector localStart = worldToDisplayLocal(origin, transformation, rayStart);
        Vector localEnd = worldToDisplayLocal(origin, transformation, rayEnd);
        Vector localDirection = localEnd.clone().subtract(localStart);

        double localMaxDistance = localDirection.length();
        if (localMaxDistance <= 1.0E-8D) {
            return -1.0D;
        }

        localDirection.normalize();

        double bestDistance = -1.0D;

        for (AABB localBox : shape.toAabbs()) {
            BoundingBox localSelectionBox = new BoundingBox(
                    localBox.minX,
                    localBox.minY,
                    localBox.minZ,
                    localBox.maxX,
                    localBox.maxY,
                    localBox.maxZ
            ).expand(0.002D);

            RayTraceResult localHit = localSelectionBox.rayTrace(
                    localStart,
                    localDirection,
                    localMaxDistance);

            if (localHit == null || localHit.getHitPosition() == null) {
                continue;
            }

            Vector worldHit = displayLocalToWorld(
                    origin,
                    transformation,
                    localHit.getHitPosition());
            double worldDistance = worldHit.distance(rayStart);

            if (worldDistance > maxDistance) {
                continue;
            }

            if (bestDistance < 0.0D || worldDistance < bestDistance) {
                bestDistance = worldDistance;
            }
        }

        return bestDistance;
    }

    /**
     * Gets the vanilla outline voxel shape for the block rendered by a block
     * display. This uses Paper/CraftBukkit internals, so the plugin must be
     * built with paperweight-userdev rather than only paper-api.
     */
    private VoxelShape getBlockDisplayVoxelShape(BlockDisplay display) {
        if (!(display.getBlock() instanceof CraftBlockData craftBlockData)) {
            return null;
        }

        BlockState blockState = craftBlockData.getState();
        CraftWorld craftWorld = (CraftWorld) display.getWorld();
        Location location = display.getLocation();
        BlockPos blockPos = BlockPos.containing(
                location.getX(),
                location.getY(),
                location.getZ());

        return blockState.getShape(craftWorld.getHandle(), blockPos);
    }

    /**
     * Converts a world-space point into a block display's local block-model
     * coordinates by applying the inverse of the display transformation.
     */
    private Vector worldToDisplayLocal(
            Location origin,
            Transformation transformation,
            Vector worldPoint) {
        Vector3f point = new Vector3f(
                (float) (worldPoint.getX() - origin.getX()),
                (float) (worldPoint.getY() - origin.getY()),
                (float) (worldPoint.getZ() - origin.getZ()));

        Vector3f translation = transformation.getTranslation();
        point.sub(translation);

        Quaternionf inverseLeftRotation = new Quaternionf(
                transformation.getLeftRotation()).invert();
        inverseLeftRotation.transform(point);

        Vector3f scale = transformation.getScale();
        point.set(
                divideByScale(point.x, scale.x),
                divideByScale(point.y, scale.y),
                divideByScale(point.z, scale.z));

        Quaternionf inverseRightRotation = new Quaternionf(
                transformation.getRightRotation()).invert();
        inverseRightRotation.transform(point);

        return new Vector(point.x, point.y, point.z);
    }

    /**
     * Converts a point from block-display local coordinates back into world
     * coordinates by applying the display transformation.
     */
    private Vector displayLocalToWorld(
            Location origin,
            Transformation transformation,
            Vector localPoint) {
        Vector3f point = new Vector3f(
                (float) localPoint.getX(),
                (float) localPoint.getY(),
                (float) localPoint.getZ());

        Quaternionf rightRotation = new Quaternionf(transformation.getRightRotation());
        Quaternionf leftRotation = new Quaternionf(transformation.getLeftRotation());
        Vector3f scale = transformation.getScale();
        Vector3f translation = transformation.getTranslation();

        rightRotation.transform(point);
        point.mul(scale);
        leftRotation.transform(point);
        point.add(translation);

        return new Vector(
                origin.getX() + point.x,
                origin.getY() + point.y,
                origin.getZ() + point.z);
    }

    /**
     * Prevents division by zero when a display has been scaled to, or near, zero
     * on one axis. A near-zero axis cannot be targeted meaningfully, so using a
     * tiny divisor is safer than throwing during hover updates.
     */
    private float divideByScale(float value, float scale) {
        if (Math.abs(scale) <= 1.0E-6F) {
            return value / (scale < 0.0F ? -1.0E-6F : 1.0E-6F);
        }

        return value / scale;
    }

    /**
     * Fallback selection boxes for displays that do not have vanilla voxel
     * shapes. These are intentionally small so item and text displays do not
     * steal focus unless the crosshair is actually near the entity.
     */
    private BoundingBox getSimpleDisplaySelectionBox(Display display) {
        Location location = display.getLocation();

        if (display instanceof BlockDisplay) {
            return new BoundingBox(
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getX() + 1.0D,
                    location.getY() + 1.0D,
                    location.getZ() + 1.0D);
        }

        if (display instanceof ItemDisplay) {
            return new BoundingBox(
                    location.getX() - 0.5D,
                    location.getY() - 0.25D,
                    location.getZ() - 0.5D,
                    location.getX() + 0.5D,
                    location.getY() + 0.75D,
                    location.getZ() + 0.5D);
        }

        if (display instanceof TextDisplay) {
            return new BoundingBox(
                    location.getX() - 0.75D,
                    location.getY() - 0.25D,
                    location.getZ() - 0.1D,
                    location.getX() + 0.75D,
                    location.getY() + 0.5D,
                    location.getZ() + 0.1D);
        }

        return new BoundingBox(
                location.getX() - 0.5D,
                location.getY() - 0.5D,
                location.getZ() - 0.5D,
                location.getX() + 0.5D,
                location.getY() + 0.5D,
                location.getZ() + 0.5D);
    }

    /**
     * Builds the action-bar text shown for the currently targeted display.
     *
     * @param display display currently under the player's crosshair
     * @param distance distance from the player's eye to the display entity
     * @return localized and colourized target description
     */
    private String describeDisplay(Display display, double distance) {
        String formattedDistance = format(distance);
        if (display instanceof BlockDisplay blockDisplay) {
            return plugin.getLang().getColored(
                    "target-display-block",
                    "material",
                    blockDisplay.getBlock().getMaterial().name(),
                    "distance",
                    formattedDistance);
        }

        if (display instanceof ItemDisplay itemDisplay) {
            return plugin.getLang().getColored(
                    "target-display-item",
                    "material",
                    itemDisplay.getItemStack().getType().name(),
                    "distance",
                    formattedDistance);
        }

        if (display instanceof TextDisplay) {
            return plugin.getLang().getColored(
                    "target-display-text",
                    "distance",
                    formattedDistance);
        }

        return plugin.getLang().getColored(
                "target-display-generic",
                "distance",
                formattedDistance);
    }

    /**
     * Checks the player's main hand using the plugin's canonical wand matcher.
     *
     * @param player player whose held item should be tested
     * @return true when the main-hand item is a VisualWand wand
     */
    private boolean isHoldingWand(Player player) {
        return plugin.getWandItem().isWand(player.getInventory().getItemInMainHand());
    }

    /**
     * Opens the root display-creation menu for a player who is not targeting a display.
     *
     * @param player player who initiated the wand interaction
     */
    private void openCreateMenu(Player player) {
        new MainMenuGUI(plugin, player).open();
    }

    /**
     * Announces the selected display type and opens its edit menu.
     *
     * @param player player editing the display
     * @param display selected display entity
     */
    private void openEditMenu(Player player, Display display) {
        player.sendMessage(plugin.getLang().getPrefixed(
                "editor-opened", "type", getDisplayTypeName(display)));
        new EditMenuGUI(plugin, player, display).open();
    }

    /**
     * Handles crouch-right-click deletion through a two-step confirmation.
     *
     * The first crouch-right-click arms deletion for the currently targeted display.
     * Repeating the same input on the same target before the timeout expires performs
     * the actual deletion. Targeting a different display replaces the pending target
     * instead of deleting either display.
     *
     * @param player player requesting deletion
     */
    private void handleDeleteDisplay(Player player) {
        Display display = getTargetedDisplay(player);
        if (display == null) {
            clearDeleteConfirmation(player);
            player.sendMessage(plugin.getLang().getPrefixed("display-not-found"));
            return;
        }

        UUID playerId = player.getUniqueId();
        DeleteConfirmation pending = deleteConfirmations.get(playerId);
        long now = System.currentTimeMillis();

        if (pending != null) {
            if (!pending.display.isValid() || pending.expiresAtMillis <= now) {
                deleteConfirmations.remove(playerId);
                player.sendMessage(plugin.getLang().getPrefixed("display-delete-confirm-expired"));
            } else if (pending.displayId.equals(display.getUniqueId())) {
                deleteConfirmations.remove(playerId);
                deleteDisplay(player, display);
                return;
            } else {
                armDeleteConfirmation(player, display, true);
                return;
            }
        }

        armDeleteConfirmation(player, display, false);
    }

    /**
     * Stores the display that will be deleted if the player repeats the delete input before
     * the configured timeout expires.
     *
     * @param player player arming deletion
     * @param display display that will be deleted on confirmation
     * @param targetChanged whether an existing pending target was replaced
     */
    private void armDeleteConfirmation(Player player, Display display, boolean targetChanged) {
        long expiresAtMillis = System.currentTimeMillis() + deleteConfirmationTimeoutMillis;
        deleteConfirmations.put(
                player.getUniqueId(),
                new DeleteConfirmation(display, expiresAtMillis));

        String seconds = formatSeconds(deleteConfirmationTimeoutMillis);
        String type = getDisplayTypeName(display);
        player.sendMessage(plugin.getLang().getPrefixed(
                targetChanged
                        ? "display-delete-confirm-changed"
                        : "display-delete-confirm-arm",
                "type",
                type,
                "seconds",
                seconds));
        player.sendActionBar(plugin.getLang().getColored(
                "display-delete-confirm-actionbar",
                "type",
                type,
                "seconds",
                seconds));
    }

    /**
     * Performs the confirmed deletion and removes all transient visual state first so the
     * display is not left in a temporary glow state while being removed.
     *
     * @param player player confirming deletion
     * @param display display to delete
     */
    private void deleteDisplay(Player player, Display display) {
        clearHover(player);
        clearDeleteConfirmation(player);

        plugin.getAnimationManager().stopAnimation(display);
        plugin.getDisplayStorage().removeDisplay(display);
        display.remove();

        player.sendMessage(plugin.getLang().getPrefixed(
                "display-delete-confirmed",
                "type",
                getDisplayTypeName(display)));
    }

    /** Clears one player's pending delete confirmation without sending feedback. */
    private void clearDeleteConfirmation(Player player) {
        deleteConfirmations.remove(player.getUniqueId());
    }

    /** Removes expired or invalid delete confirmations during the normal wand update tick. */
    private void pruneDeleteConfirmations() {
        long now = System.currentTimeMillis();
        deleteConfirmations.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            DeleteConfirmation pending = entry.getValue();
            return player == null
                    || !player.isOnline()
                    || !isHoldingWand(player)
                    || !pending.display.isValid()
                    || pending.expiresAtMillis <= now;
        });
    }

    /** Clamps scale magnitude without discarding a mirrored axis' negative sign. */
    private float clampScale(float value) {
        if (value == 0.0F) {
            return minScale;
        }
        float sign = value < 0.0F ? -1.0F : 1.0F;
        float magnitude = Math.max(minScale, Math.min(maxScale, Math.abs(value)));
        return sign * magnitude;
    }

    /** Creates a defensive deep copy because Bukkit transformation components are mutable. */
    private static Transformation copyTransformation(Transformation transformation) {
        return new Transformation(
                copyVector(transformation.getTranslation()),
                copyQuaternion(transformation.getLeftRotation()),
                copyVector(transformation.getScale()),
                copyQuaternion(transformation.getRightRotation()));
    }

    /**
     * Copies a mutable JOML vector so later edits cannot mutate the rollback snapshot.
     *
     * @param vector source vector
     * @return independent vector copy
     */
    private static Vector3f copyVector(Vector3f vector) {
        return new Vector3f(vector.x, vector.y, vector.z);
    }

    /**
     * Copies a mutable quaternion for safe transformation snapshots and composition.
     *
     * @param quaternion source quaternion
     * @return independent quaternion copy
     */
    private static Quaternionf copyQuaternion(Quaternionf quaternion) {
        return new Quaternionf(quaternion.x, quaternion.y, quaternion.z, quaternion.w);
    }

    /** Returns a safe unit look vector, including a deterministic fallback for zero length. */
    private static Vector normalizedDirection(Location location) {
        Vector direction = location.getDirection();
        if (direction.lengthSquared() < 1.0E-8D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    /**
     * Converts hotbar slot changes into the shortest signed wheel delta across the 8↔0 wrap.
     */
    private static int normalizedSlotDifference(int previous, int current) {
        int difference = current - previous;
        if (difference > 4) {
            difference -= 9;
        } else if (difference < -4) {
            difference += 9;
        }
        return difference;
    }

    /**
     * Restricts a numeric value to an inclusive range.
     *
     * @param value value to constrain
     * @param minimum lower bound
     * @param maximum upper bound
     * @return constrained value
     */
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /**
     * Formats numeric action-bar values with stable locale-independent precision.
     *
     * @param value value to format
     * @return value rounded to two decimal places
     */
    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** Normalises an angle to (-180, 180] for concise action-bar display. */
    private static String formatAngle(double angle) {
        double normalized = angle % 360.0D;
        if (normalized > 180.0D) {
            normalized -= 360.0D;
        } else if (normalized <= -180.0D) {
            normalized += 360.0D;
        }
        return String.format(java.util.Locale.ROOT, "%+.1f°", normalized);
    }

    /** Formats a millisecond duration as compact seconds for delete confirmation messages. */
    private static String formatSeconds(long millis) {
        double seconds = millis / 1_000.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 1.0E-9D) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return format(seconds);
    }

    /**
     * Converts the Bukkit display subtype into the user-facing label used by the editor.
     *
     * @param display selected display entity
     * @return readable display type name
     */
    private static String getDisplayTypeName(Display display) {
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

    // -------------------------------------------------------------------------
    // Session model
    // -------------------------------------------------------------------------

    /** World-space rotation axes available to scroll-wheel rotation mode. */
    private enum Axis {
        X,
        Y,
        Z;

        private Axis next() {
            Axis[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /**
     * Mutable per-player editing state plus the immutable snapshot required for cancellation.
     * A session exists only while a transformation is active.
     */
    private static final class TransformSession {
        private final Player player;
        private final Display display;
        private final GizmoMode mode;
        private final Location originalLocation;
        private final Transformation originalTransformation;
        private final boolean originalGlowing;
        private final GizmoSession pausedGizmo;
        private double distance;
        private Vector moveOffset;
        private boolean surfaceTargeting;
        private boolean surfaceFound;
        private Axis axis = Axis.Y;
        private double rotationDegrees;

        private TransformSession(
                Player player,
                Display display,
                GizmoMode mode,
                Location originalLocation,
                Transformation originalTransformation,
                boolean originalGlowing,
                GizmoSession pausedGizmo) {
            this.player = player;
            this.display = display;
            this.mode = mode;
            this.originalLocation = originalLocation;
            this.originalTransformation = originalTransformation;
            this.originalGlowing = originalGlowing;
            this.pausedGizmo = pausedGizmo;
        }
    }

    /**
     * Stores the display selected by the first crouch-right-click delete input.
     *
     * The same player must target this same valid display again before the expiry time
     * for deletion to proceed.
     */
    private static final class DeleteConfirmation {

        private final Display display;
        private final UUID displayId;
        private final long expiresAtMillis;

        private DeleteConfirmation(Display display, long expiresAtMillis) {
            this.display = display;
            this.displayId = display.getUniqueId();
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    /**
     * Stores temporary glow state for a display being targeted by one or more players.
     *
     * The original glow value must be restored when the final viewer stops targeting
     * the display. The viewer count prevents one player from clearing another
     * player's temporary highlight.
     */
    private static final class HighlightState {

        private final boolean originalGlowing;
        private int viewers;

        private HighlightState(boolean originalGlowing) {
            this.originalGlowing = originalGlowing;
            this.viewers = 1;
        }
    }
}
