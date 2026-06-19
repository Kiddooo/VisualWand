package dev.kiddo.visualwand.util;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.function.Predicate;

public class RayTraceUtil {

    /**
     * Ray traces from the player's eye location to find a Display entity.
     *
     * @param player The player performing the ray trace
     * @param maxDistance Maximum distance to trace
     * @return The found Display entity, or null if none found
     */
    public static Display rayTraceDisplay(Player player, double maxDistance) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        Predicate<Entity> filter = entity -> entity instanceof Display && !entity.equals(player);

        RayTraceResult result = player.getWorld().rayTraceEntities(
            eyeLocation,
            direction,
            maxDistance,
            0.5, // Ray size - slightly larger for easier selection
            filter
        );

        if (result != null && result.getHitEntity() instanceof Display display) {
            return display;
        }

        return null;
    }

    /**
     * Gets the location where the ray trace hits a block or reaches max distance.
     *
     * @param player The player performing the ray trace
     * @param maxDistance Maximum distance to trace
     * @return The target location
     */
    public static Location getTargetLocation(Player player, double maxDistance) {
        RayTraceResult result = player.rayTraceBlocks(maxDistance);
        
        if (result != null && result.getHitBlock() != null) {
            // Return the location on the face of the block
            return result.getHitPosition().toLocation(player.getWorld());
        }
        
        // If no block hit, return location at max distance
        return player.getEyeLocation().add(
            player.getEyeLocation().getDirection().multiply(maxDistance)
        );
    }

    /**
     * Gets a location slightly in front of where the player is looking.
     *
     * @param player The player
     * @param distance Distance in front of player
     * @return The location in front of the player
     */
    public static Location getLocationInFront(Player player, double distance) {
        Location eyeLocation = player.getEyeLocation();
        Vector direction = eyeLocation.getDirection().normalize();
        return eyeLocation.add(direction.multiply(distance));
    }

    /**
     * Checks if a display entity is within range and line of sight of the player.
     *
     * @param player The player
     * @param display The display entity
     * @param maxDistance Maximum allowed distance
     * @return true if in range and sight
     */
    public static boolean isInRangeAndSight(Player player, Display display, double maxDistance) {
        Location playerLoc = player.getEyeLocation();
        Location displayLoc = display.getLocation();
        
        double distance = playerLoc.distance(displayLoc);
        if (distance > maxDistance) {
            return false;
        }
        
        // Check if the display is roughly in the direction the player is looking
        Vector toDisplay = displayLoc.toVector().subtract(playerLoc.toVector()).normalize();
        Vector lookDirection = playerLoc.getDirection().normalize();
        
        double dot = toDisplay.dot(lookDirection);
        return dot > 0.7; // Within roughly 45 degrees of center
    }
}
