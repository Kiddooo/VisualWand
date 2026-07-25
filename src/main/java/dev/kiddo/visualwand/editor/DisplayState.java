package dev.kiddo.visualwand.editor;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * An immutable defensive snapshot of a display's world and transformation state.
 */
public final class DisplayState {

    private final Location location;
    private final Transformation transformation;

    public DisplayState(Location location, Transformation transformation) {
        this.location = TransformationOperations.copyLocation(
                Objects.requireNonNull(location, "location"));
        this.transformation = TransformationOperations.copyTransformation(
                Objects.requireNonNull(transformation, "transformation"));
    }

    public static DisplayState capture(Display display) {
        Objects.requireNonNull(display, "display");
        return new DisplayState(display.getLocation(), display.getTransformation());
    }

    public Location location() {
        return TransformationOperations.copyLocation(location);
    }

    public Transformation transformation() {
        return TransformationOperations.copyTransformation(transformation);
    }

    public DisplayState withLocation(Location updatedLocation) {
        return new DisplayState(
                Objects.requireNonNull(updatedLocation, "updatedLocation"),
                transformation);
    }

    public DisplayState withTransformation(Transformation updatedTransformation) {
        return new DisplayState(
                location,
                Objects.requireNonNull(updatedTransformation, "updatedTransformation"));
    }

    public void apply(Display display) {
        Objects.requireNonNull(display, "display");
        if (!hasFiniteComponents()) {
            throw new IllegalArgumentException("Display state must contain only finite components");
        }
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Display state location must have a world");
        }

        Location currentLocation = display.getLocation();
        Transformation currentTransformation = display.getTransformation();
        boolean locationChanged = !locationsEqual(location, currentLocation);
        boolean transformationChanged = !transformationsEqual(
                transformation, currentTransformation);

        if (locationChanged) {
            display.teleport(TransformationOperations.copyLocation(location));
        }
        if (transformationChanged) {
            display.setTransformation(
                    TransformationOperations.copyTransformation(transformation));
        }
    }

    boolean hasFiniteComponents() {
        return TransformationOperations.isFinite(location)
                && TransformationOperations.isFinite(transformation);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DisplayState state)) {
            return false;
        }
        return locationsEqual(location, state.location)
                && transformationsEqual(transformation, state.transformation);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(location.getWorld());
        result = 31 * result + Double.hashCode(location.getX());
        result = 31 * result + Double.hashCode(location.getY());
        result = 31 * result + Double.hashCode(location.getZ());
        result = 31 * result + Float.hashCode(location.getYaw());
        result = 31 * result + Float.hashCode(location.getPitch());
        result = 31 * result + transformationHash(transformation);
        return result;
    }

    @Override
    public String toString() {
        return "DisplayState[location=" + location + ", transformation=" + transformation + ']';
    }

    private static boolean locationsEqual(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
                && Double.compare(first.getX(), second.getX()) == 0
                && Double.compare(first.getY(), second.getY()) == 0
                && Double.compare(first.getZ(), second.getZ()) == 0
                && Float.compare(first.getYaw(), second.getYaw()) == 0
                && Float.compare(first.getPitch(), second.getPitch()) == 0;
    }

    private static boolean transformationsEqual(
            Transformation first,
            Transformation second) {
        return vectorsEqual(first.getTranslation(), second.getTranslation())
                && quaternionsEqual(first.getLeftRotation(), second.getLeftRotation())
                && vectorsEqual(first.getScale(), second.getScale())
                && quaternionsEqual(first.getRightRotation(), second.getRightRotation());
    }

    private static boolean vectorsEqual(Vector3f first, Vector3f second) {
        return Float.compare(first.x, second.x) == 0
                && Float.compare(first.y, second.y) == 0
                && Float.compare(first.z, second.z) == 0;
    }

    private static boolean quaternionsEqual(Quaternionf first, Quaternionf second) {
        return Float.compare(first.x, second.x) == 0
                && Float.compare(first.y, second.y) == 0
                && Float.compare(first.z, second.z) == 0
                && Float.compare(first.w, second.w) == 0;
    }

    private static int transformationHash(Transformation value) {
        int result = vectorHash(value.getTranslation());
        result = 31 * result + quaternionHash(value.getLeftRotation());
        result = 31 * result + vectorHash(value.getScale());
        result = 31 * result + quaternionHash(value.getRightRotation());
        return result;
    }

    private static int vectorHash(Vector3f value) {
        int result = Float.hashCode(value.x);
        result = 31 * result + Float.hashCode(value.y);
        result = 31 * result + Float.hashCode(value.z);
        return result;
    }

    private static int quaternionHash(Quaternionf value) {
        int result = Float.hashCode(value.x);
        result = 31 * result + Float.hashCode(value.y);
        result = 31 * result + Float.hashCode(value.z);
        result = 31 * result + Float.hashCode(value.w);
        return result;
    }
}
