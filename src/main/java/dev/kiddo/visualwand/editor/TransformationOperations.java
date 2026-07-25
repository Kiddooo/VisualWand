package dev.kiddo.visualwand.editor;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Pure display transformation calculations with defensive component copying.
 */
public final class TransformationOperations {

    private final double minimumScaleMagnitude;
    private final double maximumScaleMagnitude;

    public TransformationOperations(double minimumScaleMagnitude, double maximumScaleMagnitude) {
        this.minimumScaleMagnitude = requireScaleBound(
                minimumScaleMagnitude, "minimumScaleMagnitude");
        this.maximumScaleMagnitude = requireScaleBound(
                maximumScaleMagnitude, "maximumScaleMagnitude");
        if (maximumScaleMagnitude < minimumScaleMagnitude) {
            throw new IllegalArgumentException(
                    "maximumScaleMagnitude must be greater than or equal to minimumScaleMagnitude");
        }
    }

    public Location move(Location source, EditAxis axis, double signedStep) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(axis, "axis");
        requireFinite(source, "source location");
        requireFinite(signedStep, "signedStep");

        Location result = copyLocation(source);
        switch (axis) {
            case X -> result.setX(finiteSum(source.getX(), signedStep, "X coordinate"));
            case Y -> result.setY(finiteSum(source.getY(), signedStep, "Y coordinate"));
            case Z -> result.setZ(finiteSum(source.getZ(), signedStep, "Z coordinate"));
        }
        requireFinite(result, "result location");
        return result;
    }

    public Transformation rotate(
            Transformation source,
            EditCategory category,
            EditAxis axis,
            double signedDegrees) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(axis, "axis");
        requireFinite(source, "source transformation");
        requireFinite(signedDegrees, "signedDegrees");
        if (category != EditCategory.LEFT_ROTATION && category != EditCategory.RIGHT_ROTATION) {
            throw new IllegalArgumentException(
                    "Rotation category must be LEFT_ROTATION or RIGHT_ROTATION");
        }

        float radians = (float) Math.toRadians(signedDegrees % 360.0D);
        Quaternionf delta = switch (axis) {
            case X -> new Quaternionf().rotateX(radians);
            case Y -> new Quaternionf().rotateY(radians);
            case Z -> new Quaternionf().rotateZ(radians);
        };

        Vector3f translation = copyVector(source.getTranslation());
        Quaternionf leftRotation = copyQuaternion(source.getLeftRotation());
        Vector3f scale = copyVector(source.getScale());
        Quaternionf rightRotation = copyQuaternion(source.getRightRotation());
        Quaternionf selected = category == EditCategory.LEFT_ROTATION
                ? leftRotation
                : rightRotation;
        selected.mul(delta);
        normalize(selected);

        Transformation result = new Transformation(
                translation, leftRotation, scale, rightRotation);
        requireFinite(result, "result transformation");
        return result;
    }

    public Transformation scale(
            Transformation source,
            EditAxis axis,
            double signedStep) {
        Objects.requireNonNull(source, "source");
        requireFinite(source, "source transformation");
        requireFinite(signedStep, "signedStep");

        Vector3f sourceScale = source.getScale();
        float x = axis == null || axis == EditAxis.X
                ? adjustScale(sourceScale.x, signedStep)
                : sourceScale.x;
        float y = axis == null || axis == EditAxis.Y
                ? adjustScale(sourceScale.y, signedStep)
                : sourceScale.y;
        float z = axis == null || axis == EditAxis.Z
                ? adjustScale(sourceScale.z, signedStep)
                : sourceScale.z;

        Transformation result = new Transformation(
                copyVector(source.getTranslation()),
                copyQuaternion(source.getLeftRotation()),
                new Vector3f(x, y, z),
                copyQuaternion(source.getRightRotation()));
        requireFinite(result, "result transformation");
        return result;
    }

    public Location rotateEntity(Location source, EditMode mode, double signedDegrees) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(mode, "mode");
        requireFinite(source, "source location");
        requireFinite(signedDegrees, "signedDegrees");

        Location result = copyLocation(source);
        if (mode == EditMode.ENTITY_YAW) {
            result.setYaw(wrapYaw(source.getYaw() + signedDegrees));
        } else if (mode == EditMode.ENTITY_PITCH) {
            double pitch = source.getPitch() + signedDegrees;
            requireFinite(pitch, "result pitch");
            result.setPitch((float) Math.max(-90.0D, Math.min(90.0D, pitch)));
        } else {
            throw new IllegalArgumentException("Entity rotation mode must be ENTITY_YAW or ENTITY_PITCH");
        }

        requireFinite(result, "result location");
        return result;
    }

    public Transformation resetTranslation(Transformation source) {
        Objects.requireNonNull(source, "source");
        requireFinite(source, "source transformation");
        return new Transformation(
                new Vector3f(),
                copyQuaternion(source.getLeftRotation()),
                copyVector(source.getScale()),
                copyQuaternion(source.getRightRotation()));
    }

    public Transformation resetRotations(Transformation source) {
        Objects.requireNonNull(source, "source");
        requireFinite(source, "source transformation");
        return new Transformation(
                copyVector(source.getTranslation()),
                new Quaternionf(),
                copyVector(source.getScale()),
                new Quaternionf());
    }

    public Transformation resetScale(Transformation source) {
        Objects.requireNonNull(source, "source");
        requireFinite(source, "source transformation");
        return new Transformation(
                copyVector(source.getTranslation()),
                copyQuaternion(source.getLeftRotation()),
                new Vector3f(1.0F, 1.0F, 1.0F),
                copyQuaternion(source.getRightRotation()));
    }

    public Transformation resetAll() {
        return new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf());
    }

    public static Location copyLocation(Location source) {
        return Objects.requireNonNull(source, "source").clone();
    }

    public static Transformation copyTransformation(Transformation source) {
        Objects.requireNonNull(source, "source");
        return new Transformation(
                copyVector(source.getTranslation()),
                copyQuaternion(source.getLeftRotation()),
                copyVector(source.getScale()),
                copyQuaternion(source.getRightRotation()));
    }

    public static boolean isFinite(Location location) {
        return location != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ())
                && Float.isFinite(location.getYaw())
                && Float.isFinite(location.getPitch());
    }

    public static boolean isFinite(Transformation transformation) {
        if (transformation == null) {
            return false;
        }
        return isFinite(transformation.getTranslation())
                && isFinite(transformation.getLeftRotation())
                && isFinite(transformation.getScale())
                && isFinite(transformation.getRightRotation());
    }

    public static boolean isFinite(DisplayState state) {
        return state != null && state.hasFiniteComponents();
    }

    private float adjustScale(float value, double signedStep) {
        double magnitude = Math.abs((double) value) + signedStep;
        requireFinite(magnitude, "result scale magnitude");
        double clampedMagnitude = Math.max(
                minimumScaleMagnitude,
                Math.min(maximumScaleMagnitude, magnitude));
        float adjusted = (float) Math.copySign(clampedMagnitude, value);
        if (!Float.isFinite(adjusted) || adjusted == 0.0F) {
            throw new IllegalArgumentException("Result scale component must be finite and non-zero");
        }
        return adjusted;
    }

    private static float wrapYaw(double yaw) {
        requireFinite(yaw, "result yaw");
        double wrapped = yaw % 360.0D;
        if (wrapped <= -180.0D) {
            wrapped += 360.0D;
        } else if (wrapped > 180.0D) {
            wrapped -= 360.0D;
        }

        float result = (float) wrapped;
        return result <= -180.0F ? 180.0F : result;
    }

    private static double finiteSum(double value, double addend, String name) {
        double result = value + addend;
        requireFinite(result, name);
        return result;
    }

    private static void normalize(Quaternionf quaternion) {
        if (!isFinite(quaternion)) {
            throw new IllegalArgumentException("Result quaternion must contain only finite components");
        }
        double length = Math.sqrt(
                (double) quaternion.x * quaternion.x
                        + (double) quaternion.y * quaternion.y
                        + (double) quaternion.z * quaternion.z
                        + (double) quaternion.w * quaternion.w);
        if (!Double.isFinite(length) || length == 0.0D) {
            throw new IllegalArgumentException("Result quaternion must have a finite non-zero length");
        }

        float x = (float) (quaternion.x / length);
        float y = (float) (quaternion.y / length);
        float z = (float) (quaternion.z / length);
        float w = (float) (quaternion.w / length);
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(z) || !Float.isFinite(w)) {
            throw new IllegalArgumentException("Normalized quaternion must contain only finite components");
        }
        quaternion.set(x, y, z, w);
    }

    private static Vector3f copyVector(Vector3f source) {
        return new Vector3f(source.x, source.y, source.z);
    }

    private static Quaternionf copyQuaternion(Quaternionf source) {
        return new Quaternionf(source.x, source.y, source.z, source.w);
    }

    private static boolean isFinite(Vector3f vector) {
        return Float.isFinite(vector.x)
                && Float.isFinite(vector.y)
                && Float.isFinite(vector.z);
    }

    private static boolean isFinite(Quaternionf quaternion) {
        return Float.isFinite(quaternion.x)
                && Float.isFinite(quaternion.y)
                && Float.isFinite(quaternion.z)
                && Float.isFinite(quaternion.w);
    }

    private static double requireScaleBound(double value, String name) {
        float floatValue = (float) value;
        if (!Double.isFinite(value) || value <= 0.0D
                || !Float.isFinite(floatValue) || floatValue <= 0.0F) {
            throw new IllegalArgumentException(
                    name + " must be a finite positive display scale magnitude");
        }
        return value;
    }

    private static void requireFinite(Location value, String name) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException(name + " must contain only finite components");
        }
    }

    private static void requireFinite(Transformation value, String name) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException(name + " must contain only finite components");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
