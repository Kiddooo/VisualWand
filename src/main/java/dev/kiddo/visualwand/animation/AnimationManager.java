package dev.kiddo.visualwand.animation;

import dev.kiddo.visualwand.VisualWand;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnimationManager {

    private final VisualWand plugin;
    private final Map<UUID, AnimationData> activeAnimations = new HashMap<>();
    private BukkitTask animationTask;

    public AnimationManager(VisualWand plugin) {
        this.plugin = plugin;
    }

    public void startAnimationTask() {
        int tickRate = plugin.getConfig().getInt("animations.tick-rate", 2);
        
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                activeAnimations.entrySet().removeIf(entry -> {
                    AnimationData data = entry.getValue();
                    Display display = data.display();
                    
                    if (display == null || !display.isValid() || display.isDead()) {
                        return true;
                    }
                    
                    updateAnimation(data);
                    return false;
                });
            }
        }.runTaskTimer(plugin, 0L, tickRate);
    }

    public void startAnimation(Display display, AnimationType type) {
        stopAnimation(display);
        
        AnimationData data = new AnimationData(
            display,
            type,
            display.getLocation().clone(),
            display.getTransformation(),
            0
        );
        
        activeAnimations.put(display.getUniqueId(), data);
    }

    public void stopAnimation(Display display) {
        if (display != null) {
            activeAnimations.remove(display.getUniqueId());
        }
    }

    public void stopAllAnimations() {
        activeAnimations.clear();
        if (animationTask != null) {
            animationTask.cancel();
        }
    }

    public boolean hasAnimation(Display display) {
        return display != null && activeAnimations.containsKey(display.getUniqueId());
    }

    public AnimationType getAnimationType(Display display) {
        AnimationData data = activeAnimations.get(display.getUniqueId());
        return data != null ? data.type() : null;
    }

    private void updateAnimation(AnimationData data) {
        Display display = data.display();
        AnimationType type = data.type();
        int tick = data.tick();
        
        switch (type) {
            case ROTATION -> updateRotation(display, data, tick);
            case LEVITATION -> updateLevitation(display, data, tick);
            case SCALE -> updateScale(display, data, tick);
        }
        
        // Update tick counter
        activeAnimations.put(display.getUniqueId(), new AnimationData(
            data.display(),
            data.type(),
            data.originalLocation(),
            data.originalTransformation(),
            tick + 1
        ));
    }

    private void updateRotation(Display display, AnimationData data, int tick) {
        double speed = plugin.getConfig().getDouble("animations.presets.slow-rotation.speed", 1.0);
        String axisStr = plugin.getConfig().getString("animations.presets.slow-rotation.axis", "Y");
        
        Transformation current = display.getTransformation();
        Quaternionf rotation = current.getLeftRotation();
        
        float angleIncrement = (float) Math.toRadians(speed);
        
        Quaternionf additionalRotation = switch (axisStr.toUpperCase()) {
            case "X" -> new Quaternionf().rotateX(angleIncrement);
            case "Z" -> new Quaternionf().rotateZ(angleIncrement);
            default -> new Quaternionf().rotateY(angleIncrement);
        };
        
        rotation.mul(additionalRotation);
        
        Transformation newTransformation = new Transformation(
            current.getTranslation(),
            rotation,
            current.getScale(),
            current.getRightRotation()
        );
        
        display.setTransformation(newTransformation);
    }

    private void updateLevitation(Display display, AnimationData data, int tick) {
        double height = plugin.getConfig().getDouble("animations.presets.levitation.height", 0.5);
        double speed = plugin.getConfig().getDouble("animations.presets.levitation.speed", 0.02);
        
        Location originalLoc = data.originalLocation();
        double offsetY = Math.sin(tick * speed * Math.PI) * height;
        
        Location newLoc = originalLoc.clone().add(0, offsetY, 0);
        display.teleport(newLoc);
    }

    private void updateScale(Display display, AnimationData data, int tick) {
        double minScale = plugin.getConfig().getDouble("animations.presets.pulsing.min-scale", 0.9);
        double maxScale = plugin.getConfig().getDouble("animations.presets.pulsing.max-scale", 1.1);
        double speed = plugin.getConfig().getDouble("animations.presets.pulsing.speed", 0.01);
        
        double range = (maxScale - minScale) / 2;
        double center = (maxScale + minScale) / 2;
        double scale = center + Math.sin(tick * speed * Math.PI * 2) * range;
        
        Transformation current = display.getTransformation();
        
        // Get original scale from original transformation
        Vector3f originalScale = data.originalTransformation().getScale();
        float newScaleX = originalScale.x * (float) scale;
        float newScaleY = originalScale.y * (float) scale;
        float newScaleZ = originalScale.z * (float) scale;
        
        Transformation newTransformation = new Transformation(
            current.getTranslation(),
            current.getLeftRotation(),
            new Vector3f(newScaleX, newScaleY, newScaleZ),
            current.getRightRotation()
        );
        
        display.setTransformation(newTransformation);
    }

    private record AnimationData(
        Display display,
        AnimationType type,
        Location originalLocation,
        Transformation originalTransformation,
        int tick
    ) {}
}
