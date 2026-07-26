package dev.kiddo.visualwand.listener;

import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayHitboxTest {

    private static final BoundingBox UNIT_BLOCK = new BoundingBox(0, 0, 0, 1, 1, 1);
    private static final BoundingBox UNIT_ITEM = new BoundingBox(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
    private static final double EPSILON = 1.0E-5D;

    @Test
    void entityYawRotatesHitboxWithRenderedDisplay() {
        double distance = DisplayHitbox.rayTrace(
                new Vector(-2.0D, 0.5D, 0.5D),
                new Vector(1.0D, 0.0D, 0.0D),
                10.0D,
                new Vector(),
                DisplayHitbox.fixedFacing(90.0F, 0.0F),
                identityTransformation(),
                DisplayHitbox.ModelTransform.IDENTITY,
                List.of(UNIT_BLOCK));

        assertEquals(1.0D, distance, EPSILON);
    }

    @Test
    void displayTranslationAndScaleMoveHitbox() {
        Transformation transformation = new Transformation(
                new Vector3f(2.0F, 0.0F, 0.0F),
                new Quaternionf(),
                new Vector3f(2.0F, 1.0F, 1.0F),
                new Quaternionf());

        double distance = DisplayHitbox.rayTrace(
                new Vector(0.0D, 0.5D, 0.5D),
                new Vector(1.0D, 0.0D, 0.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                transformation,
                DisplayHitbox.ModelTransform.IDENTITY,
                List.of(UNIT_BLOCK));

        assertEquals(2.0D, distance, EPSILON);
    }

    @Test
    void rightRotationUsesRenderedOrientation() {
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf().rotateY((float) (Math.PI / 2.0D)));
        BoundingBox rectangular = new BoundingBox(0, 0, 0, 2, 1, 1);

        double distance = DisplayHitbox.rayTrace(
                new Vector(0.5D, 0.5D, -3.0D),
                new Vector(0.0D, 0.0D, 1.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                transformation,
                DisplayHitbox.ModelTransform.IDENTITY,
                List.of(rectangular));

        assertEquals(1.0D, distance, EPSILON);
    }

    @Test
    void negativeScaleRetainsMirroredHitbox() {
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(-2.0F, 1.0F, 1.0F),
                new Quaternionf());

        double distance = DisplayHitbox.rayTrace(
                new Vector(-3.0D, 0.5D, 0.5D),
                new Vector(1.0D, 0.0D, 0.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                transformation,
                DisplayHitbox.ModelTransform.IDENTITY,
                List.of(UNIT_BLOCK));

        assertEquals(1.0D, distance, EPSILON);
    }

    @Test
    void zeroScaleDoesNotCreateSyntheticHitboxThickness() {
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(0.0F, 1.0F, 1.0F),
                new Quaternionf());

        double distance = DisplayHitbox.rayTrace(
                new Vector(-1.0D, 0.5D, 0.5D),
                new Vector(1.0D, 0.0D, 0.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                transformation,
                DisplayHitbox.ModelTransform.IDENTITY,
                List.of(UNIT_BLOCK));

        assertEquals(-1.0D, distance);
    }

    @Test
    void groundPresetTransformsItemBounds() {
        double distance = DisplayHitbox.rayTrace(
                new Vector(0.0D, 0.0D, -1.0D),
                new Vector(0.0D, 0.0D, 1.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                identityTransformation(),
                DisplayHitbox.ModelTransform.GROUND,
                List.of(UNIT_ITEM));

        assertEquals(0.75D, distance, EPSILON);
    }

    @Test
    void groundPresetComposesInsideDisplayTransformation() {
        Transformation transformation = new Transformation(
                new Vector3f(0.0F, 0.0F, 2.0F),
                new Quaternionf(),
                new Vector3f(1.0F, 2.0F, 1.0F),
                new Quaternionf());

        double distance = DisplayHitbox.rayTrace(
                new Vector(0.0D, 0.25D, 0.0D),
                new Vector(0.0D, 0.0D, 1.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                transformation,
                DisplayHitbox.ModelTransform.GROUND,
                List.of(UNIT_ITEM));

        assertEquals(1.75D, distance, EPSILON);
    }

    @Test
    void blockItemGroundPresetUsesVanillaBlockTransform() {
        double distance = DisplayHitbox.rayTrace(
                new Vector(0.0D, 0.1875D, -1.0D),
                new Vector(0.0D, 0.0D, 1.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                identityTransformation(),
                DisplayHitbox.ModelTransform.BLOCK_GROUND,
                List.of(UNIT_ITEM));

        assertEquals(0.875D, distance, EPSILON);
    }

    @Test
    void blockAndNonBlockItemsUseServerAvailableGroundCategories() {
        assertEquals(
                DisplayHitbox.ModelTransform.BLOCK_GROUND,
                DisplayHitbox.itemModelTransform(
                        ItemDisplay.ItemDisplayTransform.GROUND,
                        true));
        assertEquals(
                DisplayHitbox.ModelTransform.GROUND,
                DisplayHitbox.itemModelTransform(
                        ItemDisplay.ItemDisplayTransform.GROUND,
                        false));
        assertEquals(
                DisplayHitbox.ModelTransform.IDENTITY,
                DisplayHitbox.itemModelTransform(
                        ItemDisplay.ItemDisplayTransform.NONE,
                        true));
    }

    @Test
    void groundPresetRetainsRenderedVerticalExtent() {
        double distance = DisplayHitbox.rayTrace(
                new Vector(0.0D, 0.35D, -1.0D),
                new Vector(0.0D, 0.0D, 1.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                identityTransformation(),
                DisplayHitbox.ModelTransform.GROUND,
                List.of(UNIT_ITEM));

        assertEquals(0.75D, distance, EPSILON);
    }

    @Test
    void exactGeometryRejectsNearMiss() {
        double distance = DisplayHitbox.rayTrace(
                new Vector(0.0D, 0.376D, -1.0D),
                new Vector(0.0D, 0.0D, 1.0D),
                10.0D,
                new Vector(),
                new Quaternionf(),
                identityTransformation(),
                DisplayHitbox.ModelTransform.GROUND,
                List.of(UNIT_ITEM));

        assertEquals(-1.0D, distance);
    }

    @Test
    void farWorldCoordinatesPreserveHitDistance() {
        Vector origin = new Vector(29_000_001.25D, 64.0D, -17_000_001.25D);

        double distance = DisplayHitbox.rayTrace(
                new Vector(origin.getX() - 2.0D, 64.5D, origin.getZ() + 0.5D),
                new Vector(1.0D, 0.0D, 0.0D),
                10.0D,
                origin,
                new Quaternionf(),
                identityTransformation(),
                DisplayHitbox.ModelTransform.IDENTITY,
                List.of(UNIT_BLOCK));

        assertEquals(2.0D, distance, EPSILON);
    }

    @Test
    void centerBillboardUsesCameraYawAndPitch() {
        Quaternionf actual = DisplayHitbox.facingRotation(
                Display.Billboard.CENTER,
                25.0F,
                15.0F,
                90.0F,
                30.0F);

        assertQuaternionEquivalent(
                new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(90.0D),
                        (float) Math.toRadians(-30.0D),
                        0.0F),
                actual);
    }

    @Test
    void horizontalBillboardKeepsEntityYaw() {
        Quaternionf actual = DisplayHitbox.facingRotation(
                Display.Billboard.HORIZONTAL,
                25.0F,
                15.0F,
                90.0F,
                30.0F);

        assertQuaternionEquivalent(
                new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(-25.0D),
                        (float) Math.toRadians(-30.0D),
                        0.0F),
                actual);
    }

    @Test
    void verticalBillboardKeepsEntityPitch() {
        Quaternionf actual = DisplayHitbox.facingRotation(
                Display.Billboard.VERTICAL,
                25.0F,
                15.0F,
                90.0F,
                30.0F);

        assertQuaternionEquivalent(
                new Quaternionf().rotationYXZ(
                        (float) Math.toRadians(90.0D),
                        (float) Math.toRadians(15.0D),
                        0.0F),
                actual);
    }

    private static void assertQuaternionEquivalent(Quaternionf expected, Quaternionf actual) {
        assertEquals(1.0D, Math.abs(expected.dot(actual)), EPSILON);
    }

    private static Transformation identityTransformation() {
        return new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf());
    }
}
