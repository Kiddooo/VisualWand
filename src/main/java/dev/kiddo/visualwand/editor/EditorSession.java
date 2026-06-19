package dev.kiddo.visualwand.editor;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gizmo.GizmoMode;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EditorSession {

    private final VisualWand plugin;
    private final Player player;
    private final Display display;
    
    private boolean isTransforming = false;
    private GizmoMode currentMode = GizmoMode.MOVE;
    private Location startLocation;
    private Transformation originalTransformation;

    public EditorSession(VisualWand plugin, Player player, Display display) {
        this.plugin = plugin;
        this.player = player;
        this.display = display;
        this.originalTransformation = display.getTransformation();
    }

    public Player getPlayer() {
        return player;
    }

    public Display getDisplay() {
        return display;
    }

    public boolean isTransforming() {
        return isTransforming;
    }

    public void setTransforming(boolean transforming) {
        isTransforming = transforming;
    }

    public GizmoMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(GizmoMode mode) {
        this.currentMode = mode;
    }

    public void startTransformation(GizmoMode mode) {
        this.currentMode = mode;
        this.isTransforming = true;
        this.startLocation = player.getLocation().clone();
        this.originalTransformation = display.getTransformation();
    }

    public void updateTransformation(Location from, Location to) {
        if (!isTransforming || display == null || !display.isValid()) {
            return;
        }

        double deltaYaw = to.getYaw() - from.getYaw();
        double deltaPitch = to.getPitch() - from.getPitch();

        switch (currentMode) {
            case MOVE -> {
                // Move based on player's look direction change
                double moveSpeed = plugin.getConfig().getDouble("editor.move-sensitivity", 0.1);
                Location displayLoc = display.getLocation();
                displayLoc.add(
                    -deltaYaw * moveSpeed * 0.1,
                    -deltaPitch * moveSpeed * 0.1,
                    0
                );
                display.teleport(displayLoc);
            }
            case ROTATE -> {
                // Rotate based on mouse movement
                double rotateSpeed = plugin.getConfig().getDouble("editor.rotate-sensitivity", 5.0);
                Transformation current = display.getTransformation();
                Quaternionf rotation = current.getLeftRotation();
                
                float radY = (float) Math.toRadians(-deltaYaw * rotateSpeed);
                float radX = (float) Math.toRadians(-deltaPitch * rotateSpeed);
                
                Quaternionf additionalRotation = new Quaternionf().rotateXYZ(radX, radY, 0);
                rotation.mul(additionalRotation);
                
                Transformation newTransformation = new Transformation(
                    current.getTranslation(),
                    rotation,
                    current.getScale(),
                    current.getRightRotation()
                );
                display.setTransformation(newTransformation);
            }
            case SCALE -> {
                // Scale based on pitch movement
                double scaleSpeed = plugin.getConfig().getDouble("editor.scale-sensitivity", 0.05);
                Transformation current = display.getTransformation();
                Vector3f scale = current.getScale();
                
                float scaleChange = (float) (-deltaPitch * scaleSpeed * 0.1);
                float newScale = Math.max(0.1f, scale.x + scaleChange);
                
                Transformation newTransformation = new Transformation(
                    current.getTranslation(),
                    current.getLeftRotation(),
                    new Vector3f(newScale, newScale, newScale),
                    current.getRightRotation()
                );
                display.setTransformation(newTransformation);
            }
        }
    }

    public void cancelTransformation() {
        if (isTransforming && originalTransformation != null) {
            display.setTransformation(originalTransformation);
        }
        isTransforming = false;
    }

    public void confirmTransformation() {
        isTransforming = false;
        originalTransformation = display.getTransformation();
    }

    public void cleanup() {
        isTransforming = false;
    }
}
