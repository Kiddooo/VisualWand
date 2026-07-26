package dev.kiddo.visualwand.listener;

import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/**
 * Exact ray tracing against display-local boxes after applying the rendered transform chain.
 */
final class DisplayHitbox {


    private DisplayHitbox() {
    }

    static double rayTrace(
            Vector rayStart,
            Vector rayDirection,
            double maximumDistance,
            Vector displayOrigin,
            Quaternionf facingRotation,
            Transformation transformation,
            ModelTransform modelTransform,
            List<BoundingBox> localBoxes) {
        Objects.requireNonNull(rayStart, "rayStart");
        Objects.requireNonNull(rayDirection, "rayDirection");
        Objects.requireNonNull(displayOrigin, "displayOrigin");
        Objects.requireNonNull(facingRotation, "facingRotation");
        Objects.requireNonNull(transformation, "transformation");
        Objects.requireNonNull(modelTransform, "modelTransform");
        Objects.requireNonNull(localBoxes, "localBoxes");
        if (!Double.isFinite(maximumDistance) || maximumDistance <= 0.0D
                || !isFinite(rayStart) || !isFinite(rayDirection) || !isFinite(displayOrigin)) {
            return -1.0D;
        }

        Vector normalizedDirection = rayDirection.clone();
        double directionLength = normalizedDirection.length();
        if (!Double.isFinite(directionLength) || directionLength <= 1.0E-8D) {
            return -1.0D;
        }
        normalizedDirection.multiply(1.0D / directionLength);
        Vector rayEnd = rayStart.clone().add(
                normalizedDirection.clone().multiply(maximumDistance));
        Vector localStart = worldToLocal(
                rayStart,
                displayOrigin,
                facingRotation,
                transformation,
                modelTransform);
        Vector localEnd = worldToLocal(
                rayEnd,
                displayOrigin,
                facingRotation,
                transformation,
                modelTransform);
        Vector localDirection = localEnd.clone().subtract(localStart);
        double localMaximumDistance = localDirection.length();
        if (!isFinite(localStart)
                || !isFinite(localDirection)
                || !Double.isFinite(localMaximumDistance)
                || localMaximumDistance <= 1.0E-8D) {
            return -1.0D;
        }
        localDirection.multiply(1.0D / localMaximumDistance);

        double bestDistance = -1.0D;
        for (BoundingBox localBox : localBoxes) {
            if (localBox == null) {
                continue;
            }
            RayTraceResult localHit = localBox.rayTrace(
                    localStart,
                    localDirection,
                    localMaximumDistance);
            if (localHit == null) {
                continue;
            }

            Vector worldHit = localToWorld(
                    localHit.getHitPosition(),
                    displayOrigin,
                    facingRotation,
                    transformation,
                    modelTransform);
            double worldDistance = worldHit.distance(rayStart);
            if (!Double.isFinite(worldDistance) || worldDistance > maximumDistance) {
                continue;
            }
            if (bestDistance < 0.0D || worldDistance < bestDistance) {
                bestDistance = worldDistance;
            }
        }
        return bestDistance;
    }

    static Quaternionf fixedFacing(float yaw, float pitch) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(pitch));
    }

    static ModelTransform itemModelTransform(
            ItemDisplay.ItemDisplayTransform displayTransform,
            boolean blockItem) {
        Objects.requireNonNull(displayTransform, "displayTransform");
        if (displayTransform != ItemDisplay.ItemDisplayTransform.GROUND) {
            return ModelTransform.IDENTITY;
        }
        return blockItem ? ModelTransform.BLOCK_GROUND : ModelTransform.GROUND;
    }

    static Quaternionf facingRotation(
            Display.Billboard billboard,
            float entityYaw,
            float entityPitch,
            float cameraYaw,
            float cameraPitch) {
        Objects.requireNonNull(billboard, "billboard");
        float entityYawRadians = (float) Math.toRadians(-entityYaw);
        float entityPitchRadians = (float) Math.toRadians(entityPitch);
        float cameraYawRadians = (float) Math.toRadians(180.0F - cameraYaw);
        float cameraPitchRadians = (float) Math.toRadians(-cameraPitch);
        return switch (billboard) {
            case FIXED -> new Quaternionf().rotationYXZ(
                    entityYawRadians,
                    entityPitchRadians,
                    0.0F);
            case VERTICAL -> new Quaternionf().rotationYXZ(
                    cameraYawRadians,
                    entityPitchRadians,
                    0.0F);
            case HORIZONTAL -> new Quaternionf().rotationYXZ(
                    entityYawRadians,
                    cameraPitchRadians,
                    0.0F);
            case CENTER -> new Quaternionf().rotationYXZ(
                    cameraYawRadians,
                    cameraPitchRadians,
                    0.0F);
        };
    }

    private static Vector worldToLocal(
            Vector worldPoint,
            Vector displayOrigin,
            Quaternionf facingRotation,
            Transformation transformation,
            ModelTransform modelTransform) {
        Vector3f point = toVector3f(worldPoint.clone().subtract(displayOrigin));
        new Quaternionf(facingRotation).invert().transform(point);
        point.sub(transformation.getTranslation());
        new Quaternionf(transformation.getLeftRotation()).invert().transform(point);

        Vector3f scale = transformation.getScale();
        point.set(
                divideByScale(point.x, scale.x),
                divideByScale(point.y, scale.y),
                divideByScale(point.z, scale.z));
        new Quaternionf(transformation.getRightRotation()).invert().transform(point);
        point.sub(modelTransform.translation);
        point.div(modelTransform.scale);
        return new Vector(point.x, point.y, point.z);
    }

    private static Vector localToWorld(
            Vector localPoint,
            Vector displayOrigin,
            Quaternionf facingRotation,
            Transformation transformation,
            ModelTransform modelTransform) {
        Vector3f point = toVector3f(localPoint);
        point.mul(modelTransform.scale);
        point.add(modelTransform.translation);
        new Quaternionf(transformation.getRightRotation()).transform(point);
        point.mul(transformation.getScale());
        new Quaternionf(transformation.getLeftRotation()).transform(point);
        point.add(transformation.getTranslation());
        new Quaternionf(facingRotation).transform(point);
        return new Vector(
                displayOrigin.getX() + point.x,
                displayOrigin.getY() + point.y,
                displayOrigin.getZ() + point.z);
    }

    private static float divideByScale(float value, float scale) {
        return scale == 0.0F ? Float.NaN : value / scale;
    }

    private static Vector3f toVector3f(Vector vector) {
        return new Vector3f(
                (float) vector.getX(),
                (float) vector.getY(),
                (float) vector.getZ());
    }

    private static boolean isFinite(Vector vector) {
        return Double.isFinite(vector.getX())
                && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }

    enum ModelTransform {
        IDENTITY(new Vector3f(), 1.0F),
        GROUND(new Vector3f(0.0F, 0.125F, 0.0F), 0.5F),
        BLOCK_GROUND(new Vector3f(0.0F, 0.1875F, 0.0F), 0.25F);

        private final Vector3f translation;
        private final float scale;

        ModelTransform(Vector3f translation, float scale) {
            this.translation = translation;
            this.scale = scale;
        }
    }
}
