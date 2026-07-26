package dev.kiddo.visualwand.editor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisplayLockPolicyTest {

    private static final NamespacedKey LOCK_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("visualwand:display_locked"));

    @Test
    void readsPersistentLockMarkerFromDisplay() {
        Display display = mock(Display.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(display.getPersistentDataContainer()).thenReturn(data);
        when(data.has(LOCK_KEY, PersistentDataType.BYTE)).thenReturn(true);

        DisplayLockPolicy policy = new DisplayLockPolicy(LOCK_KEY);

        assertTrue(policy.isLocked(display));
        when(data.has(LOCK_KEY, PersistentDataType.BYTE)).thenReturn(false);
        assertFalse(policy.isLocked(display));
    }

    @Test
    void lockWritesPersistentMarker() {
        Display display = mock(Display.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(display.getPersistentDataContainer()).thenReturn(data);

        new DisplayLockPolicy(LOCK_KEY).lock(display);

        verify(data).set(LOCK_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    @Test
    void unlockRemovesPersistentMarker() {
        Display display = mock(Display.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(display.getPersistentDataContainer()).thenReturn(data);

        new DisplayLockPolicy(LOCK_KEY).unlock(display);

        verify(data).remove(LOCK_KEY);
    }
}
