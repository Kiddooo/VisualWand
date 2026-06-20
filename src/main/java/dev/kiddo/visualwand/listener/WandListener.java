package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gizmo.GizmoManager;
import dev.kiddo.visualwand.gizmo.GizmoMode;
import dev.kiddo.visualwand.gizmo.GizmoSession;
import dev.kiddo.visualwand.gui.EditMenuGUI;
import dev.kiddo.visualwand.gui.MainMenuGUI;
import dev.kiddo.visualwand.util.RayTraceUtil;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    /** Multiplier applied to move depth while the configured fast modifier is held. */
    private final double moveFastScrollMultiplier;
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
    /** Axis selected whenever a new rotation session starts. */
    private final Axis initialRotationAxis;
    /** Active transformation state keyed by player UUID. */
    private final Map<UUID, TransformSession> sessions = new HashMap<>();
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
        this.moveFastScrollMultiplier = positiveDouble(
                "gizmo.transform-controls.move.fast-scroll-multiplier", 5.0D);
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
            gizmoManager.handleClick(player);
            plugin.getEditorManager().removeSession(player);
            return;
        }

        if (gizmo != null && rightClick && !player.isSneaking()) {
            event.setCancelled(true);
            startTransformation(player, gizmo);
            return;
        }

        if (rightClick) {
            event.setCancelled(true);

            if (player.isSneaking()) {
                handleDeleteDisplay(player);
                return;
            }

            Display display = RayTraceUtil.rayTraceDisplay(player, selectionDistance);
            if (display != null) {
                openEditMenu(player, display);
            } else {
                openCreateMenu(player);
            }
        }
    }

    /**
     * Converts hotbar scrolling into transform input while preventing the selected hotbar
     * slot from changing. Slot wrap-around is normalised before applying the delta.
     */
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        TransformSession session = sessions.get(player.getUniqueId());
        if (session == null) {
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
            double multiplier = player.isSprinting() ? moveFastScrollMultiplier : 1.0D;
            session.distance = clamp(
                    session.distance + direction * moveScrollStep * multiplier * steps,
                    minMoveDistance,
                    maxMoveDistance);
            session.surfaceTargeting = false;
            updateMoveTarget(player, session);
        } else if (session.mode == GizmoMode.SCALE) {
            applyScale(session, direction, steps, player.isSprinting());
        } else if (session.mode == GizmoMode.ROTATE) {
            applyRotation(session, direction, steps, player.isSprinting());
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
     * Cancels the active transformation when the player starts crouching.
     *
     * The display location, transformation, glow state, and paused gizmo are restored by the
     * shared cancellation path.
     *
     * @param event crouch-state change event
     */
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        TransformSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            cancelTransformation(player, session, true, true);
        }
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
     * Applies a fixed world-axis rotation step to the display left quaternion. This preserves
     * Patch 3 composition order; a later patch corrects the multiplication order.
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
        Quaternionf updatedRotation = delta.mul(copyQuaternion(current.getLeftRotation()));
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

    /** Deletes the display currently selected by the existing ray-trace utility. */
    private void handleDeleteDisplay(Player player) {
        Display display = RayTraceUtil.rayTraceDisplay(player, selectionDistance);
        if (display != null) {
            plugin.getAnimationManager().stopAnimation(display);
            plugin.getDisplayStorage().removeDisplay(display);
            display.remove();
            player.sendMessage(plugin.getLang().getPrefixed("display-deleted"));
        } else {
            player.sendMessage(plugin.getLang().getPrefixed("display-not-found"));
        }
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
}
