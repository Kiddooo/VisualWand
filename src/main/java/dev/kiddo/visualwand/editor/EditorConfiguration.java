package dev.kiddo.visualwand.editor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Immutable, validated editor settings loaded from one configuration snapshot.
 */
public final class EditorConfiguration {

    private static final double DEFAULT_MAX_DISTANCE = 50.0D;
    private static final Steps DEFAULT_TRANSLATION_STEPS = new Steps(0.01D, 0.1D, 1.0D);
    private static final Steps DEFAULT_ROTATION_STEPS = new Steps(1.0D, 5.0D, 15.0D);
    private static final Steps DEFAULT_SCALE_STEPS = new Steps(0.01D, 0.1D, 0.5D);
    private static final double DEFAULT_MINIMUM_SCALE_MAGNITUDE = 0.01D;
    private static final double DEFAULT_MAXIMUM_SCALE_MAGNITUDE = 100.0D;
    private static final int DEFAULT_INITIAL_DELAY_TICKS = 6;
    private static final int DEFAULT_REPEAT_INTERVAL_TICKS = 2;
    private static final int DEFAULT_RELEASE_GAP_TICKS = 8;
    private static final int DEFAULT_FEEDBACK_UPDATE_INTERVAL_TICKS = 10;
    private static final boolean DEFAULT_GLOW_SELECTED = true;

    private final double maxDistance;
    private final Steps translationSteps;
    private final Steps rotationSteps;
    private final Steps scaleSteps;
    private final double minimumScaleMagnitude;
    private final double maximumScaleMagnitude;
    private final int initialDelayTicks;
    private final int repeatIntervalTicks;
    private final int releaseGapTicks;
    private final int feedbackUpdateIntervalTicks;
    private final boolean glowSelected;

    private EditorConfiguration(
            double maxDistance,
            Steps translationSteps,
            Steps rotationSteps,
            Steps scaleSteps,
            double minimumScaleMagnitude,
            double maximumScaleMagnitude,
            int initialDelayTicks,
            int repeatIntervalTicks,
            int releaseGapTicks,
            int feedbackUpdateIntervalTicks,
            boolean glowSelected) {
        this.maxDistance = maxDistance;
        this.translationSteps = translationSteps;
        this.rotationSteps = rotationSteps;
        this.scaleSteps = scaleSteps;
        this.minimumScaleMagnitude = minimumScaleMagnitude;
        this.maximumScaleMagnitude = maximumScaleMagnitude;
        this.initialDelayTicks = initialDelayTicks;
        this.repeatIntervalTicks = repeatIntervalTicks;
        this.releaseGapTicks = releaseGapTicks;
        this.feedbackUpdateIntervalTicks = feedbackUpdateIntervalTicks;
        this.glowSelected = glowSelected;
    }

    public static EditorConfiguration load(JavaPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return load(plugin.getConfig(), plugin.getLogger());
    }

    static EditorConfiguration load(ConfigurationSection configuration, Logger logger) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(logger, "logger");

        double maxDistance = positiveDouble(
                configuration, logger, "editor.max-distance", DEFAULT_MAX_DISTANCE);
        Steps translationSteps = orderedSteps(
                configuration, logger, "editor.steps.translation", DEFAULT_TRANSLATION_STEPS);
        Steps rotationSteps = orderedSteps(
                configuration, logger, "editor.steps.rotation-degrees", DEFAULT_ROTATION_STEPS);
        Steps scaleSteps = orderedSteps(
                configuration, logger, "editor.steps.scale", DEFAULT_SCALE_STEPS);

        double minimumScaleMagnitude = positiveFloatMagnitude(
                configuration,
                logger,
                "editor.scale.minimum-magnitude",
                DEFAULT_MINIMUM_SCALE_MAGNITUDE);
        double maximumScaleMagnitude = positiveFloatMagnitude(
                configuration,
                logger,
                "editor.scale.maximum-magnitude",
                DEFAULT_MAXIMUM_SCALE_MAGNITUDE);
        if (maximumScaleMagnitude < minimumScaleMagnitude) {
            logger.warning("Invalid configuration ordering for 'editor.scale.minimum-magnitude' and "
                    + "'editor.scale.maximum-magnitude': expected maximum-magnitude >= "
                    + "minimum-magnitude; using fallbacks " + DEFAULT_MINIMUM_SCALE_MAGNITUDE
                    + " and " + DEFAULT_MAXIMUM_SCALE_MAGNITUDE + ".");
            minimumScaleMagnitude = DEFAULT_MINIMUM_SCALE_MAGNITUDE;
            maximumScaleMagnitude = DEFAULT_MAXIMUM_SCALE_MAGNITUDE;
        }

        int initialDelayTicks = nonNegativeInt(
                configuration,
                logger,
                "editor.input.initial-delay-ticks",
                DEFAULT_INITIAL_DELAY_TICKS);
        int repeatIntervalTicks = positiveInt(
                configuration,
                logger,
                "editor.input.repeat-interval-ticks",
                DEFAULT_REPEAT_INTERVAL_TICKS);
        int releaseGapTicks = positiveInt(
                configuration,
                logger,
                "editor.input.release-gap-ticks",
                DEFAULT_RELEASE_GAP_TICKS);
        if (releaseGapTicks <= repeatIntervalTicks) {
            logger.warning("Invalid configuration ordering for 'editor.input.repeat-interval-ticks' "
                    + "and 'editor.input.release-gap-ticks': expected release-gap-ticks > "
                    + "repeat-interval-ticks; using fallbacks " + DEFAULT_REPEAT_INTERVAL_TICKS
                    + " and " + DEFAULT_RELEASE_GAP_TICKS + ".");
            repeatIntervalTicks = DEFAULT_REPEAT_INTERVAL_TICKS;
            releaseGapTicks = DEFAULT_RELEASE_GAP_TICKS;
        }

        int feedbackUpdateIntervalTicks = positiveInt(
                configuration,
                logger,
                "editor.feedback.update-interval-ticks",
                DEFAULT_FEEDBACK_UPDATE_INTERVAL_TICKS);
        boolean glowSelected = booleanValue(
                configuration,
                logger,
                "editor.feedback.glow-selected",
                DEFAULT_GLOW_SELECTED);

        return new EditorConfiguration(
                maxDistance,
                translationSteps,
                rotationSteps,
                scaleSteps,
                minimumScaleMagnitude,
                maximumScaleMagnitude,
                initialDelayTicks,
                repeatIntervalTicks,
                releaseGapTicks,
                feedbackUpdateIntervalTicks,
                glowSelected);
    }

    public double maxDistance() {
        return maxDistance;
    }

    public double step(StepType type, StepPreset preset) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(preset, "preset");
        return switch (type) {
            case TRANSLATION -> translationSteps.value(preset);
            case ROTATION -> rotationSteps.value(preset);
            case SCALE -> scaleSteps.value(preset);
        };
    }

    public double scaleMinimumMagnitude() {
        return minimumScaleMagnitude;
    }

    public double scaleMaximumMagnitude() {
        return maximumScaleMagnitude;
    }

    public int inputInitialDelayTicks() {
        return initialDelayTicks;
    }

    public int inputRepeatIntervalTicks() {
        return repeatIntervalTicks;
    }

    public int inputReleaseGapTicks() {
        return releaseGapTicks;
    }

    public int feedbackUpdateIntervalTicks() {
        return feedbackUpdateIntervalTicks;
    }

    public boolean glowSelected() {
        return glowSelected;
    }

    private static Steps orderedSteps(
            ConfigurationSection configuration,
            Logger logger,
            String path,
            Steps defaults) {
        double fine = positiveDouble(configuration, logger, path + ".fine", defaults.fine());
        double normal = positiveDouble(configuration, logger, path + ".normal", defaults.normal());
        double coarse = positiveDouble(configuration, logger, path + ".coarse", defaults.coarse());
        if (fine <= normal && normal <= coarse) {
            return new Steps(fine, normal, coarse);
        }

        logger.warning("Invalid configuration ordering for '" + path + ".fine', '"
                + path + ".normal', and '" + path + ".coarse': expected fine <= normal <= "
                + "coarse; using fallbacks " + defaults.fine() + ", " + defaults.normal()
                + ", and " + defaults.coarse() + ".");
        return defaults;
    }

    private static double positiveDouble(
            ConfigurationSection configuration,
            Logger logger,
            String path,
            double fallback) {
        Object configured = configuration.get(path);
        if (configured instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value) && value > 0.0D) {
                return value;
            }
        }

        logFallback(logger, path, configured, "a finite number greater than zero", fallback);
        return fallback;
    }

    private static double positiveFloatMagnitude(
            ConfigurationSection configuration,
            Logger logger,
            String path,
            double fallback) {
        Object configured = configuration.get(path);
        if (configured instanceof Number number) {
            double value = number.doubleValue();
            float floatValue = (float) value;
            if (Double.isFinite(value) && value > 0.0D
                    && Float.isFinite(floatValue) && floatValue > 0.0F) {
                return value;
            }
        }

        logFallback(
                logger,
                path,
                configured,
                "a finite positive value representable by display scale components",
                fallback);
        return fallback;
    }

    private static int nonNegativeInt(
            ConfigurationSection configuration,
            Logger logger,
            String path,
            int fallback) {
        Object configured = configuration.get(path);
        if (configured instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value)
                    && value >= 0.0D
                    && value <= Integer.MAX_VALUE
                    && value == Math.rint(value)) {
                return (int) value;
            }
        }

        logFallback(logger, path, configured, "a non-negative integer", fallback);
        return fallback;
    }

    private static int positiveInt(
            ConfigurationSection configuration,
            Logger logger,
            String path,
            int fallback) {
        Object configured = configuration.get(path);
        if (configured instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value)
                    && value >= 1.0D
                    && value <= Integer.MAX_VALUE
                    && value == Math.rint(value)) {
                return (int) value;
            }
        }

        logFallback(logger, path, configured, "a positive integer", fallback);
        return fallback;
    }

    private static boolean booleanValue(
            ConfigurationSection configuration,
            Logger logger,
            String path,
            boolean fallback) {
        Object configured = configuration.get(path);
        if (configured instanceof Boolean value) {
            return value;
        }

        logFallback(logger, path, configured, "a boolean", fallback);
        return fallback;
    }

    private static void logFallback(
            Logger logger,
            String path,
            Object configured,
            String expectation,
            Object fallback) {
        String configuredText = configured == null ? "<missing>" : String.valueOf(configured);
        logger.warning("Invalid configuration value at '" + path + "': " + configuredText
                + "; expected " + expectation + "; using fallback " + fallback + ".");
    }

    private record Steps(double fine, double normal, double coarse) {
        private double value(StepPreset preset) {
            return switch (preset) {
                case FINE -> fine;
                case NORMAL -> normal;
                case COARSE -> coarse;
            };
        }
    }
}
