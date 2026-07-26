package dev.kiddo.visualwand.editor;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class TransformationOperationsTest {

    private final TransformationOperations operations = new TransformationOperations(0.01D, 100.0D);

    @Test
    void resetAllResetsTransformationAndEntityOrientationWithoutMovingDisplay() {
        World world = mock(World.class);
        Location location = new Location(world, 12.5D, 64.0D, -7.25D, 137.0F, -42.0F);
        Transformation transformation = new Transformation(
                new Vector3f(1.0F, 2.0F, 3.0F),
                new Quaternionf().rotateXYZ(0.25F, 0.5F, 0.75F),
                new Vector3f(2.0F, -3.0F, 4.0F),
                new Quaternionf().rotateXYZ(-0.4F, 0.2F, 0.9F));

        DisplayState reset = operations.resetAll(new DisplayState(location, transformation));

        Location resetLocation = reset.location();
        assertSame(world, resetLocation.getWorld());
        assertEquals(12.5D, resetLocation.getX());
        assertEquals(64.0D, resetLocation.getY());
        assertEquals(-7.25D, resetLocation.getZ());
        assertEquals(0.0F, resetLocation.getYaw());
        assertEquals(0.0F, resetLocation.getPitch());

        Transformation resetTransformation = reset.transformation();
        assertEquals(new Vector3f(), resetTransformation.getTranslation());
        assertEquals(new Quaternionf(), resetTransformation.getLeftRotation());
        assertEquals(new Vector3f(1.0F, 1.0F, 1.0F), resetTransformation.getScale());
        assertEquals(new Quaternionf(), resetTransformation.getRightRotation());
    }
    @Test
    void resetYawPreservesPitchPositionAndTransformation() {
        World world = mock(World.class);
        DisplayState source = new DisplayState(
                new Location(world, 3.0D, 70.0D, -9.0D, 125.0F, -35.0F),
                sampleTransformation());

        DisplayState reset = operations.resetYaw(source);

        Location location = reset.location();
        assertSame(world, location.getWorld());
        assertEquals(3.0D, location.getX());
        assertEquals(70.0D, location.getY());
        assertEquals(-9.0D, location.getZ());
        assertEquals(0.0F, location.getYaw());
        assertEquals(-35.0F, location.getPitch());
        assertTransformationEquals(source.transformation(), reset.transformation());
    }

    @Test
    void resetPitchPreservesYawPositionAndTransformation() {
        World world = mock(World.class);
        DisplayState source = new DisplayState(
                new Location(world, -4.0D, 80.0D, 6.0D, -95.0F, 48.0F),
                sampleTransformation());

        DisplayState reset = operations.resetPitch(source);

        Location location = reset.location();
        assertSame(world, location.getWorld());
        assertEquals(-4.0D, location.getX());
        assertEquals(80.0D, location.getY());
        assertEquals(6.0D, location.getZ());
        assertEquals(-95.0F, location.getYaw());
        assertEquals(0.0F, location.getPitch());
        assertTransformationEquals(source.transformation(), reset.transformation());
    }

    private static Transformation sampleTransformation() {
        return new Transformation(
                new Vector3f(0.5F, -1.0F, 2.0F),
                new Quaternionf().rotateXYZ(0.2F, 0.4F, 0.6F),
                new Vector3f(1.5F, 2.0F, -0.75F),
                new Quaternionf().rotateXYZ(-0.3F, 0.7F, 0.1F));
    }

    private static void assertTransformationEquals(
            Transformation expected,
            Transformation actual) {
        assertEquals(expected.getTranslation(), actual.getTranslation());
        assertEquals(expected.getLeftRotation(), actual.getLeftRotation());
        assertEquals(expected.getScale(), actual.getScale());
        assertEquals(expected.getRightRotation(), actual.getRightRotation());
    }
}
