package dev.kiddo.visualwand.gizmo;

import dev.kiddo.visualwand.VisualWand;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GizmoManager {

    private final VisualWand plugin;
    private final Map<UUID, GizmoSession> activeSessions = new HashMap<>();
    private BukkitTask renderTask;

    public GizmoManager(VisualWand plugin) {
        this.plugin = plugin;
    }

    public void startRenderTask() {
        int interval = plugin.getConfig().getInt("gizmo.update-interval", 2);
        
        renderTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (GizmoSession session : activeSessions.values()) {
                    if (session.getDisplay().isValid()) {
                        renderGizmo(session);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, interval);
    }

    public void startGizmo(Player player, Display display) {
        GizmoSession session = new GizmoSession(player, display);
        activeSessions.put(player.getUniqueId(), session);
        player.sendMessage(plugin.getLang().getPrefixed("gizmo-mode-move"));
    }

    public void stopGizmo(Player player) {
        activeSessions.remove(player.getUniqueId());
    }

    public void stopAllGizmos() {
        activeSessions.clear();
        if (renderTask != null) {
            renderTask.cancel();
        }
    }

    public boolean hasActiveGizmo(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public GizmoSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    public void setMode(Player player, GizmoMode mode) {
        GizmoSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.setMode(mode);
        }
    }

    public void handleClick(Player player) {
        GizmoSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        // Cycle through modes
        GizmoMode[] modes = GizmoMode.values();
        int currentIndex = session.getMode().ordinal();
        int nextIndex = (currentIndex + 1) % modes.length;
        session.setMode(modes[nextIndex]);

        String modeKey = switch (modes[nextIndex]) {
            case MOVE -> "gizmo-mode-move";
            case ROTATE -> "gizmo-mode-rotate";
            case SCALE -> "gizmo-mode-scale";
        };
        player.sendMessage(plugin.getLang().getPrefixed(modeKey));
    }

    private void renderGizmo(GizmoSession session) {
        Player player = session.getPlayer();
        Display display = session.getDisplay();
        Location center = display.getLocation();
        GizmoMode mode = session.getMode();

        double size = plugin.getConfig().getDouble("gizmo.size", 1.5);
        int density = plugin.getConfig().getInt("gizmo.particle-density", 20);

        List<Integer> xColor = plugin.getConfig().getIntegerList("gizmo.colors.x-axis");
        List<Integer> yColor = plugin.getConfig().getIntegerList("gizmo.colors.y-axis");
        List<Integer> zColor = plugin.getConfig().getIntegerList("gizmo.colors.z-axis");

        Color colorX = Color.fromRGB(
            xColor.size() >= 3 ? xColor.get(0) : 255,
            xColor.size() >= 3 ? xColor.get(1) : 0,
            xColor.size() >= 3 ? xColor.get(2) : 0
        );
        Color colorY = Color.fromRGB(
            yColor.size() >= 3 ? yColor.get(0) : 0,
            yColor.size() >= 3 ? yColor.get(1) : 255,
            yColor.size() >= 3 ? yColor.get(2) : 0
        );
        Color colorZ = Color.fromRGB(
            zColor.size() >= 3 ? zColor.get(0) : 0,
            zColor.size() >= 3 ? zColor.get(1) : 0,
            zColor.size() >= 3 ? zColor.get(2) : 255
        );

        switch (mode) {
            case MOVE -> {
                // Draw axis arrows
                drawLine(player, center, center.clone().add(size, 0, 0), colorX, density);
                drawLine(player, center, center.clone().add(0, size, 0), colorY, density);
                drawLine(player, center, center.clone().add(0, 0, size), colorZ, density);
                
                // Draw arrowheads
                drawArrowhead(player, center.clone().add(size, 0, 0), new Vector(1, 0, 0), colorX);
                drawArrowhead(player, center.clone().add(0, size, 0), new Vector(0, 1, 0), colorY);
                drawArrowhead(player, center.clone().add(0, 0, size), new Vector(0, 0, 1), colorZ);
            }
            case ROTATE -> {
                // Draw rotation circles
                drawCircle(player, center, size, "Y", colorX, density); // XZ plane
                drawCircle(player, center, size, "X", colorY, density); // YZ plane
                drawCircle(player, center, size, "Z", colorZ, density); // XY plane
            }
            case SCALE -> {
                // Draw scale indicators (boxes at ends of axes)
                List<Integer> scaleColor = plugin.getConfig().getIntegerList("gizmo.colors.scale");
                Color colorScale = Color.fromRGB(
                    scaleColor.size() >= 3 ? scaleColor.get(0) : 255,
                    scaleColor.size() >= 3 ? scaleColor.get(1) : 255,
                    scaleColor.size() >= 3 ? scaleColor.get(2) : 0
                );
                
                drawLine(player, center, center.clone().add(size, 0, 0), colorX, density);
                drawLine(player, center, center.clone().add(0, size, 0), colorY, density);
                drawLine(player, center, center.clone().add(0, 0, size), colorZ, density);
                
                drawCube(player, center.clone().add(size, 0, 0), 0.1, colorScale);
                drawCube(player, center.clone().add(0, size, 0), 0.1, colorScale);
                drawCube(player, center.clone().add(0, 0, size), 0.1, colorScale);
            }
        }
    }

    private void drawLine(Player player, Location from, Location to, Color color, int density) {
        Vector direction = to.toVector().subtract(from.toVector());
        double length = direction.length();
        direction.normalize();

        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.5f);

        for (int i = 0; i <= density; i++) {
            double t = (double) i / density;
            Location point = from.clone().add(direction.clone().multiply(length * t));
            player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
        }
    }

    private void drawArrowhead(Player player, Location tip, Vector direction, Color color) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1f);
        
        // Create a simple arrowhead with particles
        Vector perpendicular1 = new Vector(-direction.getZ(), 0, direction.getX()).normalize().multiply(0.1);
        Vector perpendicular2 = new Vector(0, 1, 0).crossProduct(direction).normalize().multiply(0.1);
        
        for (int i = 0; i < 5; i++) {
            double offset = i * 0.05;
            Location point = tip.clone().subtract(direction.clone().multiply(offset * 2));
            point.add(perpendicular1.clone().multiply(offset));
            player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
            
            point = tip.clone().subtract(direction.clone().multiply(offset * 2));
            point.subtract(perpendicular1.clone().multiply(offset));
            player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
        }
    }

    private void drawCircle(Player player, Location center, double radius, String axis, Color color, int density) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 0.5f);

        for (int i = 0; i < density; i++) {
            double angle = 2 * Math.PI * i / density;
            double x = 0, y = 0, z = 0;

            switch (axis) {
                case "X" -> { // YZ plane
                    y = Math.cos(angle) * radius;
                    z = Math.sin(angle) * radius;
                }
                case "Y" -> { // XZ plane
                    x = Math.cos(angle) * radius;
                    z = Math.sin(angle) * radius;
                }
                case "Z" -> { // XY plane
                    x = Math.cos(angle) * radius;
                    y = Math.sin(angle) * radius;
                }
            }

            Location point = center.clone().add(x, y, z);
            player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
        }
    }

    private void drawCube(Player player, Location center, double size, Color color) {
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1f);
        
        // Draw 8 corners of the cube
        for (int x = -1; x <= 1; x += 2) {
            for (int y = -1; y <= 1; y += 2) {
                for (int z = -1; z <= 1; z += 2) {
                    Location corner = center.clone().add(x * size, y * size, z * size);
                    player.spawnParticle(Particle.DUST, corner, 1, 0, 0, 0, 0, dustOptions);
                }
            }
        }
    }
}
