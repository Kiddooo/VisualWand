package dev.kiddo.visualwand.gizmo;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditAxis;
import dev.kiddo.visualwand.editor.EditMode;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.editor.EditorSession;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Objects;

/**
 * Read-only particle rendering for the active sessions owned by {@link EditorManager}.
 */
public final class GizmoManager {

    private final VisualWand plugin;
    private final EditorManager editorManager;
    private BukkitTask renderTask;

    public GizmoManager(VisualWand plugin, EditorManager editorManager) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.editorManager = Objects.requireNonNull(editorManager, "editorManager");
    }

    public void start() {
        if (renderTask != null && !renderTask.isCancelled()) {
            return;
        }
        int interval = Math.max(1, plugin.getConfig().getInt("gizmo.update-interval", 2));
        renderTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::renderSessions,
                0L,
                interval);
    }

    public void stop() {
        if (renderTask != null) {
            renderTask.cancel();
            renderTask = null;
        }
    }

    private void renderSessions() {
        for (EditorSession session : editorManager.sessions()) {
            EditMode mode = session.mode();
            if (mode == null) {
                continue;
            }

            Player player = plugin.getServer().getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                continue;
            }
            Display display = editorManager.selectedDisplay(player);
            if (display != null) {
                render(player, display, mode);
            }
        }
    }

    private void render(Player player, Display display, EditMode mode) {
        Location center = display.getLocation();
        double size = positiveSize(plugin.getConfig().getDouble("gizmo.size", 1.5D));
        int density = Math.max(4, plugin.getConfig().getInt("gizmo.particle-density", 20));

        Color xColor = configuredColor("gizmo.colors.x-axis", Color.RED);
        Color yColor = configuredColor("gizmo.colors.y-axis", Color.LIME);
        Color zColor = configuredColor("gizmo.colors.z-axis", Color.BLUE);
        Color scaleColor = configuredColor("gizmo.colors.scale", Color.YELLOW);
        Color rotationColor = configuredColor("gizmo.colors.rotation", Color.FUCHSIA);

        switch (mode.category()) {
            case TRANSLATION -> drawTranslation(
                    player,
                    center,
                    size,
                    density,
                    mode.axis(),
                    xColor,
                    yColor,
                    zColor);
            case LEFT_ROTATION, RIGHT_ROTATION, ENTITY_ORIENTATION -> drawRotation(
                    player,
                    center,
                    size,
                    density,
                    mode.axis(),
                    rotationColor,
                    xColor,
                    yColor,
                    zColor);
            case SCALE -> drawScale(
                    player,
                    center,
                    size,
                    density,
                    mode,
                    scaleColor,
                    xColor,
                    yColor,
                    zColor);
        }
    }

    private void drawTranslation(
            Player player,
            Location center,
            double size,
            int density,
            EditAxis selected,
            Color xColor,
            Color yColor,
            Color zColor) {
        drawArrow(player, center, new Vector(1, 0, 0), size, axisRenderColor(EditAxis.X, selected, xColor), density);
        drawArrow(player, center, new Vector(0, 1, 0), size, axisRenderColor(EditAxis.Y, selected, yColor), density);
        drawArrow(player, center, new Vector(0, 0, 1), size, axisRenderColor(EditAxis.Z, selected, zColor), density);
    }

    private void drawRotation(
            Player player,
            Location center,
            double radius,
            int density,
            EditAxis selected,
            Color inactive,
            Color xColor,
            Color yColor,
            Color zColor) {
        drawCircle(player, center, radius, EditAxis.X, selected == EditAxis.X ? xColor : dim(inactive), density);
        drawCircle(player, center, radius, EditAxis.Y, selected == EditAxis.Y ? yColor : dim(inactive), density);
        drawCircle(player, center, radius, EditAxis.Z, selected == EditAxis.Z ? zColor : dim(inactive), density);
    }

    private void drawScale(
            Player player,
            Location center,
            double size,
            int density,
            EditMode mode,
            Color uniformColor,
            Color xColor,
            Color yColor,
            Color zColor) {
        EditAxis selected = mode.axis();
        Color renderedX = selected == null
                ? uniformColor
                : axisRenderColor(EditAxis.X, selected, xColor);
        Color renderedY = selected == null
                ? uniformColor
                : axisRenderColor(EditAxis.Y, selected, yColor);
        Color renderedZ = selected == null
                ? uniformColor
                : axisRenderColor(EditAxis.Z, selected, zColor);

        drawScaleHandle(player, center, new Vector(1, 0, 0), size, renderedX, density);
        drawScaleHandle(player, center, new Vector(0, 1, 0), size, renderedY, density);
        drawScaleHandle(player, center, new Vector(0, 0, 1), size, renderedZ, density);
    }

    private void drawArrow(
            Player player,
            Location center,
            Vector direction,
            double length,
            Color color,
            int density) {
        Location tip = center.clone().add(direction.clone().multiply(length));
        drawLine(player, center, tip, color, density);
        drawArrowhead(player, tip, direction, color);
    }

    private void drawScaleHandle(
            Player player,
            Location center,
            Vector direction,
            double length,
            Color color,
            int density) {
        Location tip = center.clone().add(direction.clone().multiply(length));
        drawLine(player, center, tip, color, density);
        drawCube(player, tip, 0.1D, color);
    }

    private void drawLine(Player player, Location from, Location to, Color color, int density) {
        Vector delta = to.toVector().subtract(from.toVector());
        for (int index = 0; index <= density; index++) {
            double fraction = (double) index / density;
            Location point = from.clone().add(delta.clone().multiply(fraction));
            spawnDust(player, point, color, 0.55F);
        }
    }

    private void drawArrowhead(Player player, Location tip, Vector direction, Color color) {
        Vector unit = direction.clone().normalize();
        Vector basis = Math.abs(unit.getY()) > 0.9D
                ? new Vector(1, 0, 0)
                : new Vector(0, 1, 0);
        Vector sideOne = unit.clone().crossProduct(basis).normalize();
        Vector sideTwo = unit.clone().crossProduct(sideOne).normalize();

        for (int index = 1; index <= 4; index++) {
            double backward = index * 0.06D;
            double width = index * 0.025D;
            Location base = tip.clone().subtract(unit.clone().multiply(backward));
            spawnDust(player, base.clone().add(sideOne.clone().multiply(width)), color, 0.9F);
            spawnDust(player, base.clone().subtract(sideOne.clone().multiply(width)), color, 0.9F);
            spawnDust(player, base.clone().add(sideTwo.clone().multiply(width)), color, 0.9F);
            spawnDust(player, base.clone().subtract(sideTwo.clone().multiply(width)), color, 0.9F);
        }
    }

    private void drawCircle(
            Player player,
            Location center,
            double radius,
            EditAxis axis,
            Color color,
            int density) {
        for (int index = 0; index < density; index++) {
            double angle = Math.PI * 2.0D * index / density;
            double first = Math.cos(angle) * radius;
            double second = Math.sin(angle) * radius;
            Location point = switch (axis) {
                case X -> center.clone().add(0.0D, first, second);
                case Y -> center.clone().add(first, 0.0D, second);
                case Z -> center.clone().add(first, second, 0.0D);
            };
            spawnDust(player, point, color, 0.55F);
        }
    }

    private void drawCube(Player player, Location center, double size, Color color) {
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    spawnDust(
                            player,
                            center.clone().add(x * size, y * size, z * size),
                            color,
                            0.95F);
                }
            }
        }
    }

    private void spawnDust(Player player, Location location, Color color, float size) {
        player.spawnParticle(
                Particle.DUST,
                location,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                new Particle.DustOptions(color, size));
    }

    private Color configuredColor(String path, Color fallback) {
        List<Integer> components = plugin.getConfig().getIntegerList(path);
        if (components.size() < 3) {
            return fallback;
        }
        return Color.fromRGB(
                clampColor(components.get(0)),
                clampColor(components.get(1)),
                clampColor(components.get(2)));
    }

    private static Color axisRenderColor(EditAxis axis, EditAxis selected, Color color) {
        return axis == selected ? color : dim(color);
    }

    private static Color dim(Color color) {
        return Color.fromRGB(
                Math.max(24, color.getRed() / 4),
                Math.max(24, color.getGreen() / 4),
                Math.max(24, color.getBlue() / 4));
    }

    private static int clampColor(int component) {
        return Math.max(0, Math.min(255, component));
    }

    private static double positiveSize(double configured) {
        return Double.isFinite(configured) && configured > 0.0D ? configured : 1.5D;
    }
}
