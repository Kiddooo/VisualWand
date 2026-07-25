package dev.kiddo.visualwand.editor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;
import java.util.Objects;

public enum EditMode {
    MOVE_X("Move X", EditCategory.TRANSLATION, EditAxis.X, StepType.TRANSLATION, Material.RED_CONCRETE),
    MOVE_Y("Move Y", EditCategory.TRANSLATION, EditAxis.Y, StepType.TRANSLATION, Material.LIME_CONCRETE),
    MOVE_Z("Move Z", EditCategory.TRANSLATION, EditAxis.Z, StepType.TRANSLATION, Material.BLUE_CONCRETE),
    LEFT_ROTATION_X("Left Rotation X", EditCategory.LEFT_ROTATION, EditAxis.X, StepType.ROTATION, Material.RED_WOOL),
    LEFT_ROTATION_Y("Left Rotation Y", EditCategory.LEFT_ROTATION, EditAxis.Y, StepType.ROTATION, Material.LIME_WOOL),
    LEFT_ROTATION_Z("Left Rotation Z", EditCategory.LEFT_ROTATION, EditAxis.Z, StepType.ROTATION, Material.BLUE_WOOL),
    RIGHT_ROTATION_X("Right Rotation X", EditCategory.RIGHT_ROTATION, EditAxis.X, StepType.ROTATION, Material.RED_CANDLE),
    RIGHT_ROTATION_Y("Right Rotation Y", EditCategory.RIGHT_ROTATION, EditAxis.Y, StepType.ROTATION, Material.LIME_CANDLE),
    RIGHT_ROTATION_Z("Right Rotation Z", EditCategory.RIGHT_ROTATION, EditAxis.Z, StepType.ROTATION, Material.BLUE_CANDLE),
    SCALE_X("Scale X", EditCategory.SCALE, EditAxis.X, StepType.SCALE, Material.RED_DYE),
    SCALE_Y("Scale Y", EditCategory.SCALE, EditAxis.Y, StepType.SCALE, Material.LIME_DYE),
    SCALE_Z("Scale Z", EditCategory.SCALE, EditAxis.Z, StepType.SCALE, Material.BLUE_DYE),
    UNIFORM_SCALE("Uniform Scale", EditCategory.SCALE, null, StepType.SCALE, Material.MAGMA_CREAM),
    ENTITY_YAW("Entity Yaw", EditCategory.ENTITY_ORIENTATION, EditAxis.Y, StepType.ROTATION, Material.COMPASS),
    ENTITY_PITCH("Entity Pitch", EditCategory.ENTITY_ORIENTATION, EditAxis.X, StepType.ROTATION, Material.SPYGLASS);

    private final String displayName;
    private final EditCategory category;
    private final EditAxis axis;
    private final StepType stepType;
    private final Material material;

    EditMode(String displayName, EditCategory category, EditAxis axis, StepType stepType, Material material) {
        this.displayName = displayName;
        this.category = category;
        this.axis = axis;
        this.stepType = stepType;
        this.material = material;
    }

    public String displayName() {
        return displayName;
    }

    public EditCategory category() {
        return category;
    }

    public EditAxis axis() {
        return axis;
    }

    public StepType stepType() {
        return stepType;
    }

    public Material material() {
        return material;
    }

    public boolean supports(Display display) {
        return display instanceof BlockDisplay
                || display instanceof ItemDisplay
                || display instanceof TextDisplay;
    }

    public DisplayState apply(
            TransformationOperations operations,
            DisplayState state,
            double step,
            EditDirection direction) {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(step) || step <= 0.0D) {
            throw new IllegalArgumentException("step must be finite and greater than zero");
        }

        double signedStep = step * direction.sign();
        return switch (category) {
            case TRANSLATION -> state.withLocation(
                    operations.move(state.location(), axis, signedStep));
            case LEFT_ROTATION, RIGHT_ROTATION -> state.withTransformation(
                    operations.rotate(state.transformation(), category, axis, signedStep));
            case SCALE -> state.withTransformation(
                    operations.scale(state.transformation(), axis, signedStep));
            case ENTITY_ORIENTATION -> state.withLocation(
                    operations.rotateEntity(state.location(), this, signedStep));
        };
    }

    public String formatCurrent(DisplayState state) {
        Objects.requireNonNull(state, "state");
        return switch (category) {
            case TRANSLATION -> {
                Location location = state.location();
                double coordinate = switch (axis) {
                    case X -> location.getX();
                    case Y -> location.getY();
                    case Z -> location.getZ();
                };
                yield String.format(Locale.ROOT, "%s %.3f", axis, coordinate);
            }
            case LEFT_ROTATION, RIGHT_ROTATION -> {
                Transformation transformation = state.transformation();
                Quaternionf rotation = category == EditCategory.LEFT_ROTATION
                        ? transformation.getLeftRotation()
                        : transformation.getRightRotation();
                yield String.format(
                        Locale.ROOT,
                        "Quaternion %.3f %.3f %.3f %.3f",
                        rotation.x,
                        rotation.y,
                        rotation.z,
                        rotation.w);
            }
            case SCALE -> {
                Transformation transformation = state.transformation();
                Vector3f scale = transformation.getScale();
                yield String.format(
                        Locale.ROOT,
                        "X %.3f Y %.3f Z %.3f",
                        scale.x,
                        scale.y,
                        scale.z);
            }
            case ENTITY_ORIENTATION -> {
                Location location = state.location();
                boolean yaw = this == ENTITY_YAW;
                yield String.format(
                        Locale.ROOT,
                        "%s %.1f°",
                        yaw ? "Yaw" : "Pitch",
                        yaw ? location.getYaw() : location.getPitch());
            }
        };
    }

}
