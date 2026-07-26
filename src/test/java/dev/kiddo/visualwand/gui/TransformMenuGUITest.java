package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransformMenuGUITest {

    @Test
    void lockedMenuAllowsOnlyUnlockAndCloseSlots() {
        Display display = mock(Display.class);
        UUID displayId = UUID.randomUUID();
        when(display.getUniqueId()).thenReturn(displayId);
        TransformMenuGUI gui = new TransformMenuGUI(
                mock(VisualWand.class),
                mock(Player.class),
                display);

        assertEquals(displayId, gui.targetDisplayId());
        assertTrue(gui.allowsLockedClick(16));
        assertTrue(gui.allowsLockedClick(53));
        assertFalse(gui.allowsLockedClick(10));
        assertFalse(gui.allowsLockedClick(45));
    }
}
