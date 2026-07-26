package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditMode;
import dev.kiddo.visualwand.editor.EditorConfiguration;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.editor.EditorSession;
import dev.kiddo.visualwand.editor.StepPreset;
import dev.kiddo.visualwand.editor.StepType;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Selects click-editing modes and invokes manager-owned utilities without mutating entities.
 */
public final class TransformMenuGUI extends BaseGUI {

    private final UUID displayId;

    public TransformMenuGUI(VisualWand plugin, org.bukkit.entity.Player player, Display display) {
        super(plugin, player);
        this.displayId = Objects.requireNonNull(display, "display").getUniqueId();
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 54, Lang.getComponent("&8✦ &6Click Editing"));
        fillBorder();
        Display display = resolveDisplay();
        if (display != null && plugin.getEditorManager().isLocked(display)) {
            renderLockedItems();
            inventory.setItem(53, getCloseButton());
            fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
            return;
        }

        renderModeItems();
        renderPresetItems();
        renderUtilityItems();
        inventory.setItem(45, getBackButton());
        inventory.setItem(53, getCloseButton());
        fillEmpty(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public UUID targetDisplayId() {
        return displayId;
    }

    @Override
    public boolean allowsLockedClick(int slot) {
        return slot == 16 || slot == 53;
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        EditorManager manager = plugin.getEditorManager();
        Display display = resolveDisplay();
        if (display == null || !display.isValid()) {
            player.closeInventory();
            player.sendMessage(Lang.getPrefixed("&cThe selected display is no longer available."));
            return;
        }
        if (manager.isLocked(display)) {
            if (slot == 16 && manager.unlockDisplay(player, display)
                    && manager.select(player, display)) {
                new TransformMenuGUI(plugin, player, display).open();
            } else if (slot == 53) {
                player.closeInventory();
            }
            return;
        }
        EditMode mode = modeForSlot(slot);
        if (mode != null) {
            manager.selectMode(player, mode);
            player.closeInventory();
            return;
        }

        StepPreset preset = presetForSlot(slot);
        if (preset != null) {
            if (manager.selectPreset(player, preset)) {
                renderModeItems();
                renderPresetItems();
            }
            return;
        }


        switch (slot) {
            case 13 -> manager.undo(player);
            case 14 -> manager.copy(player);
            case 15 -> manager.paste(player);
            case 16 -> manager.lockSelectedDisplay(player);
            case 22 -> {
                manager.clear(player);
                player.closeInventory();
                player.sendMessage(Lang.getPrefixed("&eDisplay deselected."));
            }
            case 31 -> manager.resetYaw(player);
            case 40 -> manager.resetPitch(player);
            case 41 -> manager.resetTranslation(player);
            case 42 -> manager.resetRotations(player);
            case 43 -> manager.resetScale(player);
            case 49 -> manager.resetEntireTransformation(player);
            case 45 -> {
                Display selectedDisplay = manager.selectedDisplay(player);
                player.closeInventory();
                if (selectedDisplay != null && selectedDisplay.getUniqueId().equals(displayId)) {
                    new EditMenuGUI(plugin, player, selectedDisplay).open();
                }
            }
            case 53 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void renderModeItems() {
        setModeItem(10, EditMode.MOVE_X);
        setModeItem(11, EditMode.MOVE_Y);
        setModeItem(12, EditMode.MOVE_Z);

        setModeItem(19, EditMode.LEFT_ROTATION_X);
        setModeItem(20, EditMode.LEFT_ROTATION_Y);
        setModeItem(21, EditMode.LEFT_ROTATION_Z);

        setModeItem(28, EditMode.SCALE_X);
        setModeItem(29, EditMode.SCALE_Y);
        setModeItem(30, EditMode.SCALE_Z);
        setModeItem(32, EditMode.UNIFORM_SCALE);
        setModeItem(33, EditMode.ENTITY_YAW);
        setModeItem(34, EditMode.ENTITY_PITCH);

        setModeItem(37, EditMode.RIGHT_ROTATION_X);
        setModeItem(38, EditMode.RIGHT_ROTATION_Y);
        setModeItem(39, EditMode.RIGHT_ROTATION_Z);
    }

    private void setModeItem(int slot, EditMode mode) {
        EditorManager manager = plugin.getEditorManager();
        EditorSession session = manager.session(player);
        boolean active = session != null && session.mode() == mode;
        StepPreset preset = session == null ? StepPreset.NORMAL : session.preset();
        EditorConfiguration configuration = manager.configuration();
        double step = configuration.step(mode.stepType(), preset);

        inventory.setItem(slot, createItem(
                mode.material(),
                Lang.getComponent((active ? "&a✔ " : "&e") + mode.displayName()),
                Lang.getComponents(List.of(
                        "&7" + categoryLabel(mode),
                        "&fStep: &e" + preset.displayName() + " " + formatStep(mode.stepType(), step),
                        "&7Left click increases; right click decreases.",
                        active ? "&aCurrently active" : "&eClick to select and close"))));
    }

    private void renderPresetItems() {
        EditorSession session = plugin.getEditorManager().session(player);
        StepPreset selected = session == null ? StepPreset.NORMAL : session.preset();
        setPresetItem(23, StepPreset.FINE, Material.IRON_NUGGET, selected);
        setPresetItem(24, StepPreset.NORMAL, Material.GOLD_NUGGET, selected);
        setPresetItem(25, StepPreset.COARSE, Material.NETHERITE_INGOT, selected);
    }

    private void setPresetItem(
            int slot,
            StepPreset preset,
            Material material,
            StepPreset selected) {
        EditorConfiguration configuration = plugin.getEditorManager().configuration();
        inventory.setItem(slot, createItem(
                material,
                Lang.getComponent((preset == selected ? "&a✔ " : "&e") + preset.displayName() + " Steps"),
                Lang.getComponents(List.of(
                        "&7Move: &f" + formatStep(
                                StepType.TRANSLATION,
                                configuration.step(StepType.TRANSLATION, preset)),
                        "&7Rotate: &f" + formatStep(
                                StepType.ROTATION,
                                configuration.step(StepType.ROTATION, preset)),
                        "&7Scale: &f" + formatStep(
                                StepType.SCALE,
                                configuration.step(StepType.SCALE, preset)),
                        preset == selected ? "&aCurrently selected" : "&eClick to persist this preset"))));
    }

    private void renderUtilityItems() {
        inventory.setItem(13, utility(
                Material.CLOCK,
                "&e↶ Undo / Redo",
                "&7Swap the current and previous display states."));
        inventory.setItem(14, utility(
                Material.WRITABLE_BOOK,
                "&bCopy Transform",
                "&7Copy transformation only; location is excluded."));
        inventory.setItem(15, utility(
                Material.KNOWLEDGE_BOOK,
                "&bPaste Transform",
                "&7Paste the copied transformation onto this display."));
        inventory.setItem(16, utility(
                Material.REDSTONE_TORCH,
                "&cLock Display",
                "&7Block all interaction until this display is unlocked."));
        inventory.setItem(22, utility(
                Material.LEAD,
                "&cDeselect",
                "&7End editing and restore selection feedback."));
        inventory.setItem(31, utility(
                Material.NETHERITE_BLOCK,
                "&eReset Yaw",
                "&7Set entity yaw to zero."));
        inventory.setItem(40, utility(
                Material.BEDROCK,
                "&eReset Pitch",
                "&7Set entity pitch to zero."));
        inventory.setItem(41, utility(
                Material.ENDER_PEARL,
                "&eReset Transform Translation",
                "&7Set transformation translation to zero."));
        inventory.setItem(42, utility(
                Material.ENDER_EYE,
                "&eReset Rotations",
                "&7Reset both left and right quaternions."));
        inventory.setItem(43, utility(
                Material.SLIME_BALL,
                "&eReset Scale",
                "&7Set transformation scale to one."));
        inventory.setItem(49, utility(
                Material.RED_STAINED_GLASS_PANE,
                "&cReset Entire Transformation",
                "&7Reset translation, rotations, scale, yaw, and pitch."));
    }

    private void renderLockedItems() {
        inventory.setItem(16, utility(
                Material.REDSTONE_TORCH,
                "&aUnlock Display",
                "&7Allow this display to be edited again."));
    }

    private Display resolveDisplay() {
        return plugin.getServer().getEntity(displayId) instanceof Display display
                ? display
                : null;
    }

    private ItemStack utility(Material material, String name, String description) {
        return createItem(
                material,
                Lang.getComponent(name),
                Lang.getComponents(List.of(description, "&eClick to apply deliberately.")));
    }

    private static EditMode modeForSlot(int slot) {
        return switch (slot) {
            case 10 -> EditMode.MOVE_X;
            case 11 -> EditMode.MOVE_Y;
            case 12 -> EditMode.MOVE_Z;
            case 19 -> EditMode.LEFT_ROTATION_X;
            case 20 -> EditMode.LEFT_ROTATION_Y;
            case 21 -> EditMode.LEFT_ROTATION_Z;
            case 28 -> EditMode.SCALE_X;
            case 29 -> EditMode.SCALE_Y;
            case 30 -> EditMode.SCALE_Z;
            case 32 -> EditMode.UNIFORM_SCALE;
            case 33 -> EditMode.ENTITY_YAW;
            case 34 -> EditMode.ENTITY_PITCH;
            case 37 -> EditMode.RIGHT_ROTATION_X;
            case 38 -> EditMode.RIGHT_ROTATION_Y;
            case 39 -> EditMode.RIGHT_ROTATION_Z;
            default -> null;
        };
    }

    private static StepPreset presetForSlot(int slot) {
        return switch (slot) {
            case 23 -> StepPreset.FINE;
            case 24 -> StepPreset.NORMAL;
            case 25 -> StepPreset.COARSE;
            default -> null;
        };
    }

    private static String categoryLabel(EditMode mode) {
        return mode.category().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String formatStep(StepType type, double step) {
        return switch (type) {
            case TRANSLATION, SCALE -> String.format(Locale.ROOT, "%.3f", step);
            case ROTATION -> String.format(Locale.ROOT, "%.1f°", step);
        };
    }
}
