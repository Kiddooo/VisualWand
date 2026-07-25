package dev.kiddo.visualwand.editor;

import org.bukkit.Material;

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
}
