package dev.kiddo.visualwand.editor;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;

/**
 * Persists the global editor lock directly on a display entity.
 */
final class DisplayLockPolicy {

    private final NamespacedKey lockKey;

    DisplayLockPolicy(NamespacedKey lockKey) {
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey");
    }

    boolean isLocked(Display display) {
        return Objects.requireNonNull(display, "display")
                .getPersistentDataContainer()
                .has(lockKey, PersistentDataType.BYTE);
    }

    void lock(Display display) {
        Objects.requireNonNull(display, "display")
                .getPersistentDataContainer()
                .set(lockKey, PersistentDataType.BYTE, (byte) 1);
    }

    void unlock(Display display) {
        Objects.requireNonNull(display, "display")
                .getPersistentDataContainer()
                .remove(lockKey);
    }
}
