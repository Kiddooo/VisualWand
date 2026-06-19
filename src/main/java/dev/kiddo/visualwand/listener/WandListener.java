package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.editor.EditorSession;
import dev.kiddo.visualwand.gizmo.GizmoManager;
import dev.kiddo.visualwand.gizmo.GizmoMode;
import dev.kiddo.visualwand.gizmo.GizmoSession;
import dev.kiddo.visualwand.gui.EditMenuGUI;
import dev.kiddo.visualwand.gui.MainMenuGUI;
import dev.kiddo.visualwand.util.RayTraceUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class WandListener implements Listener {
    private final VisualWand plugin;
    private final double selectionDistance;
    private final double minMoveDistance;
    private final double maxMoveDistance;
    private final double scrollStep;
    private final double fastScrollMultiplier;
    private final double surfaceOffset;
    private final double axisLength;
    private final double offsetLineThresholdSquared;
    private final int previewUpdateInterval;
    private final int actionBarUpdateInterval;
    private final int beamSamples;
    private final int axisSamples;
    private final int offsetSamples;
    private final Particle.DustOptions airBeam;
    private final Particle.DustOptions surfaceBeam;
    private final Particle.DustOptions missingSurfaceBeam;
    private final Particle.DustOptions axisX;
    private final Particle.DustOptions axisY;
    private final Particle.DustOptions axisZ;
    private final Particle.DustOptions offsetLine;
    private final Map<UUID, Location> originalLocations = new HashMap<>();
    private final Map<UUID, MoveSession> moveSessions = new HashMap<>();
    private long previewTicks;

    public WandListener(VisualWand plugin) {
        this.plugin = plugin;
        this.selectionDistance = plugin.getConfig().getDouble("editor.max-distance", 50.0D);
        this.minMoveDistance = positiveDouble("gizmo.move-preview.min-distance", 0.5D);
        this.maxMoveDistance = Math.max(
                minMoveDistance,
                positiveDouble("gizmo.move-preview.max-distance", 512.0D)
        );
        this.scrollStep = positiveDouble("gizmo.move-preview.scroll-step", 1.0D);
        this.fastScrollMultiplier = positiveDouble(
                "gizmo.move-preview.fast-scroll-multiplier",
                5.0D
        );
        this.surfaceOffset = nonNegativeDouble("gizmo.move-preview.surface-offset", 0.02D);
        this.axisLength = positiveDouble("gizmo.move-preview.axis-length", 0.24D);
        double offsetLineThreshold = nonNegativeDouble(
                "gizmo.move-preview.offset-line-threshold",
                0.05D
        );
        this.offsetLineThresholdSquared = offsetLineThreshold * offsetLineThreshold;
        this.previewUpdateInterval = positiveInt("gizmo.move-preview.update-interval", 1);
        this.actionBarUpdateInterval = positiveInt(
                "gizmo.move-preview.actionbar-update-interval",
                10
        );
        this.beamSamples = positiveInt("gizmo.move-preview.beam-samples", 24);
        this.axisSamples = positiveInt("gizmo.move-preview.axis-samples", 5);
        this.offsetSamples = positiveInt("gizmo.move-preview.offset-samples", 12);

        this.airBeam = loadDustOptions(
                "gizmo.move-preview.colors.air",
                "gizmo.colors.scale",
                "gizmo.move-preview.particle-sizes.beam",
                0.65F
        );
        this.surfaceBeam = loadDustOptions(
                "gizmo.move-preview.colors.surface",
                "gizmo.colors.y-axis",
                "gizmo.move-preview.particle-sizes.beam",
                0.65F
        );
        this.missingSurfaceBeam = loadDustOptions(
                "gizmo.move-preview.colors.no-surface",
                "gizmo.colors.x-axis",
                "gizmo.move-preview.particle-sizes.beam",
                0.65F
        );
        this.axisX = loadDustOptions(
                "gizmo.colors.x-axis",
                null,
                "gizmo.move-preview.particle-sizes.axis",
                0.8F
        );
        this.axisY = loadDustOptions(
                "gizmo.colors.y-axis",
                null,
                "gizmo.move-preview.particle-sizes.axis",
                0.8F
        );
        this.axisZ = loadDustOptions(
                "gizmo.colors.z-axis",
                null,
                "gizmo.move-preview.particle-sizes.axis",
                0.8F
        );
        this.offsetLine = loadDustOptions(
                "gizmo.move-preview.colors.offset",
                null,
                "gizmo.move-preview.particle-sizes.offset",
                0.45F
        );

        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickMovePreviews,
                1L,
                previewUpdateInterval
        );
    }

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
        boolean rightClick = action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
        boolean leftClick = action == Action.LEFT_CLICK_AIR
                || action == Action.LEFT_CLICK_BLOCK;

        MoveSession moveSession = moveSessions.get(player.getUniqueId());
        if (moveSession != null) {
            if (rightClick) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    cancelMove(player, moveSession, true);
                } else {
                    confirmMove(player, moveSession);
                }
            } else if (leftClick) {
                event.setCancelled(true);
            }
            return;
        }

        GizmoManager gizmoManager = plugin.getGizmoManager();
        EditorManager editorManager = plugin.getEditorManager();
        GizmoSession gizmoSession = gizmoManager.getSession(player);
        EditorSession editorSession = editorManager.getSession(player);

        if (editorSession != null && editorSession.isTransforming() && rightClick) {
            event.setCancelled(true);
            if (player.isSneaking()) {
                cancelEditorTransformation(player, editorSession);
            } else {
                confirmEditorTransformation(player, editorSession);
            }
            return;
        }

        if (editorSession != null && editorSession.isTransforming() && leftClick) {
            event.setCancelled(true);
            return;
        }

        if (gizmoSession != null && leftClick) {
            event.setCancelled(true);
            gizmoManager.handleClick(player);
            editorManager.removeSession(player);
            originalLocations.remove(player.getUniqueId());
            return;
        }

        if (gizmoSession != null && rightClick && !player.isSneaking()) {
            event.setCancelled(true);
            startTransformation(player, gizmoSession);
            return;
        }

        if (!rightClick) {
            return;
        }

        event.setCancelled(true);
        if (player.isSneaking()) {
            handleDeleteDisplay(player);
            return;
        }

        Display targetDisplay = RayTraceUtil.rayTraceDisplay(player, selectionDistance);
        if (targetDisplay != null) {
            openEditMenu(player, targetDisplay);
        } else {
            openCreateMenu(player);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        Player player = event.getPlayer();
        if (moveSessions.containsKey(player.getUniqueId())) {
            return;
        }

        EditorSession editorSession = plugin.getEditorManager().getSession(player);
        if (editorSession == null || !editorSession.isTransforming()) {
            return;
        }

        Display display = editorSession.getDisplay();
        if (!display.isValid()) {
            plugin.getEditorManager().removeSession(player);
            originalLocations.remove(player.getUniqueId());
            return;
        }

        Location from = event.getFrom();
        if (Float.compare(from.getYaw(), to.getYaw()) == 0
                && Float.compare(from.getPitch(), to.getPitch()) == 0) {
            return;
        }

        editorSession.updateTransformation(from, to);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        MoveSession session = moveSessions.get(player.getUniqueId());
        if (session == null || !isHoldingWand(player)) {
            return;
        }

        event.setCancelled(true);

        int difference = event.getNewSlot() - event.getPreviousSlot();
        if (difference == 0) {
            return;
        }

        // Hotbar wrapping: 8 -> 0 is forward, 0 -> 8 is backward.
        if (difference > 4) {
            difference -= 9;
        } else if (difference < -4) {
            difference += 9;
        }

        double multiplier = player.isSprinting() ? fastScrollMultiplier : 1.0D;
        session.distance = clamp(
                session.distance + difference * scrollStep * multiplier,
                minMoveDistance,
                maxMoveDistance
        );
        session.surfaceTargeting = false;
        recalculateOffset(player, session);
        updateMoveTarget(player, session);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        MoveSession session = moveSessions.get(player.getUniqueId());
        if (session == null || !plugin.getWandItem().isWand(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        session.surfaceTargeting = !session.surfaceTargeting;
        recalculateOffset(player, session);
        updateMoveTarget(player, session);
        player.sendMessage(plugin.getLang().getPrefixed(
                session.surfaceTargeting
                        ? "gizmo-move-targeting-surface"
                        : "gizmo-move-targeting-air"
        ));
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        MoveSession moveSession = moveSessions.get(player.getUniqueId());
        if (moveSession != null) {
            cancelMove(player, moveSession, true);
            return;
        }

        EditorSession editorSession = plugin.getEditorManager().getSession(player);
        if (editorSession != null && editorSession.isTransforming()) {
            cancelEditorTransformation(player, editorSession);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        MoveSession moveSession = moveSessions.get(player.getUniqueId());
        if (moveSession != null) {
            cancelMove(player, moveSession, false);
        } else {
            EditorSession editorSession = plugin.getEditorManager().getSession(player);
            if (editorSession != null && editorSession.isTransforming()) {
                cancelEditorTransformation(player, editorSession, false);
            } else {
                plugin.getEditorManager().removeSession(player);
                originalLocations.remove(player.getUniqueId());
            }
        }

        plugin.getGizmoManager().stopGizmo(player);
    }

    private void startTransformation(Player player, GizmoSession gizmoSession) {
        Display display = gizmoSession.getDisplay();
        if (display == null || !display.isValid()) {
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-display-invalid"));
            plugin.getGizmoManager().stopGizmo(player);
            plugin.getEditorManager().removeSession(player);
            originalLocations.remove(player.getUniqueId());
            moveSessions.remove(player.getUniqueId());
            return;
        }

        if (gizmoSession.getMode() == GizmoMode.MOVE) {
            startMove(player, display);
        } else {
            startEditorTransformation(player, gizmoSession);
        }
    }

    private void startMove(Player player, Display display) {
        Location eye = player.getEyeLocation();
        Vector direction = normalizedDirection(eye);
        Location displayLocation = display.getLocation();
        Vector eyeToDisplay = displayLocation.toVector().subtract(eye.toVector());

        double projectedDistance = eyeToDisplay.dot(direction);
        double distance = clamp(projectedDistance, minMoveDistance, maxMoveDistance);
        Location anchor = eye.clone().add(direction.clone().multiply(distance));
        Vector offset = displayLocation.toVector().subtract(anchor.toVector());

        MoveSession session = new MoveSession(
                player,
                display,
                displayLocation.clone(),
                distance,
                offset
        );
        moveSessions.put(player.getUniqueId(), session);
        originalLocations.put(player.getUniqueId(), displayLocation.clone());
        plugin.getEditorManager().removeSession(player);

        updateMoveTarget(player, session);
        player.sendMessage(plugin.getLang().getPrefixed("gizmo-move-started"));
    }

    private void startEditorTransformation(Player player, GizmoSession gizmoSession) {
        Display display = gizmoSession.getDisplay();
        EditorManager editorManager = plugin.getEditorManager();
        EditorSession editorSession = editorManager.getSession(player);
        if (editorSession == null || editorSession.getDisplay() != display) {
            editorManager.removeSession(player);
            editorSession = editorManager.createSession(player, display);
        }

        GizmoMode mode = gizmoSession.getMode();
        originalLocations.put(player.getUniqueId(), display.getLocation().clone());
        editorSession.startTransformation(mode);

        player.sendMessage(plugin.getLang().getPrefixed(
                "gizmo-transform-started",
                "mode",
                getLocalizedModeName(mode)
        ));
    }

    private void tickMovePreviews() {
        previewTicks++;
        Iterator<Map.Entry<UUID, MoveSession>> iterator = moveSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            MoveSession session = iterator.next().getValue();
            Player player = session.player;
            Display display = session.display;

            if (!player.isOnline() || !display.isValid() || !isHoldingWand(player)) {
                if (display.isValid()) {
                    display.teleport(session.originalLocation);
                }
                originalLocations.remove(player.getUniqueId());
                iterator.remove();
                continue;
            }

            updateMoveTarget(player, session);
            renderMovePreview(player, session);
            if (previewTicks % actionBarUpdateInterval == 0L) {
                sendMoveActionBar(player, session);
            }
        }
    }

    private void updateMoveTarget(Player player, MoveSession session) {
        Location eye = player.getEyeLocation();
        Vector direction = normalizedDirection(eye);
        Location anchor = null;
        boolean surfaceFound = false;

        if (session.surfaceTargeting) {
            RayTraceResult result = player.rayTraceBlocks(maxMoveDistance, FluidCollisionMode.NEVER);
            if (result != null && result.getHitPosition() != null) {
                Vector position = result.getHitPosition().clone();
                if (result.getHitBlockFace() != null) {
                    position.add(result.getHitBlockFace().getDirection().clone().multiply(surfaceOffset));
                }
                anchor = new Location(
                        eye.getWorld(),
                        position.getX(),
                        position.getY(),
                        position.getZ()
                );
                surfaceFound = true;
            }
        }

        if (anchor == null) {
            anchor = eye.clone().add(direction.clone().multiply(session.distance));
        }

        Location current = session.display.getLocation();
        Location target = anchor.clone().add(session.offset);
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());

        session.anchor = anchor;
        session.target = target;
        session.surfaceFound = surfaceFound;
        session.display.teleport(target);
    }

    private void recalculateOffset(Player player, MoveSession session) {
        Location eye = player.getEyeLocation();
        Location anchor = null;

        if (session.surfaceTargeting) {
            RayTraceResult result = player.rayTraceBlocks(maxMoveDistance, FluidCollisionMode.NEVER);
            if (result != null && result.getHitPosition() != null) {
                Vector position = result.getHitPosition().clone();
                if (result.getHitBlockFace() != null) {
                    position.add(result.getHitBlockFace().getDirection().clone().multiply(surfaceOffset));
                }
                anchor = new Location(
                        eye.getWorld(),
                        position.getX(),
                        position.getY(),
                        position.getZ()
                );
            }
        }

        if (anchor == null) {
            anchor = eye.clone().add(
                    normalizedDirection(eye).multiply(session.distance)
            );
        }

        session.offset = session.display.getLocation().toVector().subtract(anchor.toVector());
    }

    private void renderMovePreview(Player player, MoveSession session) {
        if (session.anchor == null || session.target == null) {
            return;
        }

        Particle.DustOptions beam = session.surfaceTargeting
                ? (session.surfaceFound ? surfaceBeam : missingSurfaceBeam)
                : airBeam;

        drawParticleLine(player, player.getEyeLocation(), session.anchor, beam, beamSamples);

        Location anchor = session.anchor;
        drawParticleLine(
                player,
                anchor.clone().add(-axisLength, 0.0D, 0.0D),
                anchor.clone().add(axisLength, 0.0D, 0.0D),
                axisX,
                axisSamples
        );
        drawParticleLine(
                player,
                anchor.clone().add(0.0D, -axisLength, 0.0D),
                anchor.clone().add(0.0D, axisLength, 0.0D),
                axisY,
                axisSamples
        );
        drawParticleLine(
                player,
                anchor.clone().add(0.0D, 0.0D, -axisLength),
                anchor.clone().add(0.0D, 0.0D, axisLength),
                axisZ,
                axisSamples
        );

        if (session.anchor.distanceSquared(session.target) > offsetLineThresholdSquared) {
            drawParticleLine(player, session.anchor, session.target, offsetLine, offsetSamples);
        }
    }

    private void drawParticleLine(
            Player player,
            Location from,
            Location to,
            Particle.DustOptions dust,
            int samples
    ) {
        Vector difference = to.toVector().subtract(from.toVector());
        double length = difference.length();
        if (length == 0.0D) {
            player.spawnParticle(Particle.DUST, from, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
            return;
        }

        int count = Math.max(1, samples);
        Vector step = difference.multiply(1.0D / count);
        Location point = from.clone();
        for (int i = 0; i <= count; i++) {
            player.spawnParticle(Particle.DUST, point, 1, 0.0D, 0.0D, 0.0D, 0.0D, dust);
            point.add(step);
        }
    }

    private void sendMoveActionBar(Player player, MoveSession session) {
        double shownDistance = session.anchor == null
                ? session.distance
                : player.getEyeLocation().distance(session.anchor);

        String modeKey;
        if (!session.surfaceTargeting) {
            modeKey = "gizmo-move-mode-air";
        } else if (session.surfaceFound) {
            modeKey = "gizmo-move-mode-surface";
        } else {
            modeKey = "gizmo-move-mode-surface-no-hit";
        }

        player.sendActionBar(plugin.getLang().getColored(
                "gizmo-move-actionbar",
                "distance",
                formatDistance(shownDistance),
                "mode",
                plugin.getLang().getColored(modeKey)
        ));
    }

    private void confirmMove(Player player, MoveSession session) {
        moveSessions.remove(player.getUniqueId());
        originalLocations.remove(player.getUniqueId());
        player.sendActionBar(plugin.getLang().getColored("gizmo-move-confirmed"));
        player.sendMessage(plugin.getLang().getPrefixed("gizmo-move-confirmed"));
    }

    private void cancelMove(Player player, MoveSession session, boolean sendMessage) {
        moveSessions.remove(player.getUniqueId());
        originalLocations.remove(player.getUniqueId());

        if (session.display != null && session.display.isValid()) {
            session.display.teleport(session.originalLocation);
        }

        if (sendMessage) {
            player.sendActionBar(plugin.getLang().getColored("gizmo-move-cancelled"));
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-move-cancelled"));
        }
    }

    private void confirmEditorTransformation(Player player, EditorSession editorSession) {
        editorSession.confirmTransformation();
        plugin.getEditorManager().removeSession(player);
        originalLocations.remove(player.getUniqueId());
        player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-confirmed"));
    }

    private void cancelEditorTransformation(Player player, EditorSession editorSession) {
        cancelEditorTransformation(player, editorSession, true);
    }

    private void cancelEditorTransformation(
            Player player,
            EditorSession editorSession,
            boolean sendMessage
    ) {
        editorSession.cancelTransformation();

        Location originalLocation = originalLocations.remove(player.getUniqueId());
        Display display = editorSession.getDisplay();
        if (originalLocation != null && display != null && display.isValid()) {
            display.teleport(originalLocation);
        }

        plugin.getEditorManager().removeSession(player);
        if (sendMessage) {
            player.sendMessage(plugin.getLang().getPrefixed("gizmo-transform-cancelled"));
        }
    }

    private Particle.DustOptions loadDustOptions(
            String colorPath,
            String fallbackColorPath,
            String sizePath,
            float defaultSize
    ) {
        List<Integer> rgb = plugin.getConfig().getIntegerList(colorPath);
        if (rgb.size() < 3 && fallbackColorPath != null) {
            rgb = plugin.getConfig().getIntegerList(fallbackColorPath);
        }

        Color color;
        if (rgb.size() >= 3) {
            color = Color.fromRGB(
                    clampColor(rgb.get(0)),
                    clampColor(rgb.get(1)),
                    clampColor(rgb.get(2))
            );
        } else {
            plugin.getLogger().warning(
                    "Missing or invalid RGB color at '" + colorPath + "'; using white."
            );
            color = Color.WHITE;
        }

        float size = (float) positiveDouble(sizePath, defaultSize);
        return new Particle.DustOptions(color, size);
    }

    private double positiveDouble(String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (value > 0.0D && Double.isFinite(value)) {
            return value;
        }
        plugin.getLogger().warning(
                "Configuration value '" + path + "' must be positive; using " + fallback + "."
        );
        return fallback;
    }

    private double nonNegativeDouble(String path, double fallback) {
        double value = plugin.getConfig().getDouble(path, fallback);
        if (value >= 0.0D && Double.isFinite(value)) {
            return value;
        }
        plugin.getLogger().warning(
                "Configuration value '" + path + "' must be non-negative; using " + fallback + "."
        );
        return fallback;
    }

    private int positiveInt(String path, int fallback) {
        int value = plugin.getConfig().getInt(path, fallback);
        if (value > 0) {
            return value;
        }
        plugin.getLogger().warning(
                "Configuration value '" + path + "' must be positive; using " + fallback + "."
        );
        return fallback;
    }

    private static int clampColor(int component) {
        return Math.max(0, Math.min(255, component));
    }

    private boolean isHoldingWand(Player player) {
        return plugin.getWandItem().isWand(player.getInventory().getItemInMainHand());
    }

    private void openCreateMenu(Player player) {
        new MainMenuGUI(plugin, player).open();
    }

    private void openEditMenu(Player player, Display display) {
        player.sendMessage(plugin.getLang().getPrefixed(
                "editor-opened",
                "type",
                getDisplayTypeName(display)
        ));
        new EditMenuGUI(plugin, player, display).open();
    }

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

    private static Vector normalizedDirection(Location location) {
        Vector direction = location.getDirection();
        if (direction.lengthSquared() == 0.0D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String formatDistance(double distance) {
        return String.format(java.util.Locale.ROOT, "%.1f", distance);
    }

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

    private String getLocalizedModeName(GizmoMode mode) {
        if (mode == GizmoMode.MOVE) {
            return plugin.getLang().get("gizmo-mode-name-move");
        }
        if (mode == GizmoMode.ROTATE) {
            return plugin.getLang().get("gizmo-mode-name-rotate");
        }
        return plugin.getLang().get("gizmo-mode-name-scale");
    }

    private static final class MoveSession {
        private final Player player;
        private final Display display;
        private final Location originalLocation;
        private double distance;
        private Vector offset;
        private boolean surfaceTargeting;
        private boolean surfaceFound;
        private Location anchor;
        private Location target;

        private MoveSession(
                Player player,
                Display display,
                Location originalLocation,
                double distance,
                Vector offset
        ) {
            this.player = player;
            this.display = display;
            this.originalLocation = originalLocation;
            this.distance = distance;
            this.offset = offset;
        }
    }
}
