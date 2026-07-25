package dev.kiddo.visualwand.editor;

public enum EditDirection {
    POSITIVE(1),
    NEGATIVE(-1);

    private final int sign;

    EditDirection(int sign) {
        this.sign = sign;
    }

    public int sign() {
        return sign;
    }
}
