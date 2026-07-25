package dev.kiddo.visualwand.editor;

public enum StepPreset {
    FINE("Fine"),
    NORMAL("Normal"),
    COARSE("Coarse");

    private final String displayName;

    StepPreset(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
