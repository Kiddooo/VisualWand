package dev.kiddo.visualwand.util;

import java.util.Locale;

/**
 * Shared helpers for formatting display property values in GUI controls.
 */
public final class DisplayPropertyUtil {

    private static final float EPSILON = 1.0E-4F;

    private DisplayPropertyUtil() {
    }

    /**
     * Removes binary floating-point noise from GUI values.
     *
     * @param value value to show
     * @return compact integer, one-decimal, or two-decimal representation
     */
    public static String formatNumber(float value) {
        float rounded = Math.round(value * 100.0F) / 100.0F;

        if (near(rounded, Math.round(rounded))) {
            return Integer.toString(Math.round(rounded));
        }

        if (near(rounded * 10.0F, Math.round(rounded * 10.0F))) {
            return String.format(Locale.ROOT, "%.1f", rounded);
        }

        return String.format(Locale.ROOT, "%.2f", rounded);
    }

    /**
     * Formats Paper display view range as the stored multiplier plus its vanilla
     * approximate block distance.
     *
     * @param range display view-range multiplier
     * @return formatted multiplier and approximate distance
     */
    public static String formatViewRange(float range) {
        return formatNumber(range) + " (~" + formatNumber(range * 64.0F) + " blocks)";
    }

    /**
     * Keeps shadow-radius edits aligned to one-decimal increments.
     *
     * @param value raw value
     * @return value rounded to tenths
     */
    public static float roundTenths(float value) {
        return Math.round(value * 10.0F) / 10.0F;
    }

    private static boolean near(float value, float expected) {
        return Math.abs(value - expected) <= EPSILON;
    }
}
