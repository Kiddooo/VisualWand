package dev.kiddo.visualwand.editor;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorConfigurationTest {

    private static final Logger LOGGER = Logger.getLogger(EditorConfigurationTest.class.getName());

    @Test
    void targetCycleRangeDefaultsToEight() {
        EditorConfiguration configuration = load(new YamlConfiguration());

        assertEquals(8.0D, configuration.targetCycleRange());
    }

    @Test
    void targetCycleRangeFallsBackForNaN() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("editor.targeting.cycle-range", Double.NaN);

        EditorConfiguration configuration = load(yaml);

        assertEquals(8.0D, configuration.targetCycleRange());
    }

    @Test
    void targetCycleRangeClampsToEditorMaximum() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("editor.max-distance", 5.0D);
        yaml.set("editor.targeting.cycle-range", 8.0D);

        EditorConfiguration configuration = load(yaml);

        assertEquals(5.0D, configuration.targetCycleRange());
    }

    private static EditorConfiguration load(YamlConfiguration configuration) {
        return EditorConfiguration.load(configuration, LOGGER);
    }
}
