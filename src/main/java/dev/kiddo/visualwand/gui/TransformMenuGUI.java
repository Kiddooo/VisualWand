package dev.kiddo.visualwand.gui;

import java.util.List;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gizmo.GizmoMode;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class TransformMenuGUI extends BaseGUI {

    private final Display display;
    private final double moveSensitivity;
    private final double rotateSensitivity;
    private final double scaleSensitivity;

    public TransformMenuGUI(VisualWand plugin, Player player, Display display) {
        super(plugin, player);
        this.display = display;
        this.moveSensitivity = plugin.getConfig().getDouble("editor.move-sensitivity", 0.1);
        this.rotateSensitivity = plugin.getConfig().getDouble("editor.rotate-sensitivity", 5.0);
        this.scaleSensitivity = plugin.getConfig().getDouble("editor.scale-sensitivity", 0.05);
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 54, Lang.colorize("&8✦ &6Transformations"));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // === MOVEMENT BUTTONS ===
        // +X (Right)
        inventory.setItem(11, createItem(Material.RED_CONCRETE, 
            "&c+X &7(Right)",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // -X (Left)
        inventory.setItem(10, createItem(Material.RED_STAINED_GLASS, 
            "&c-X &7(Left)",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // +Y (Up)
        inventory.setItem(4, createItem(Material.LIME_CONCRETE, 
            "&a+Y &7(Up)",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // -Y (Down)
        inventory.setItem(22, createItem(Material.LIME_STAINED_GLASS, 
            "&a-Y &7(Down)",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // +Z (Forward)
        inventory.setItem(13, createItem(Material.BLUE_CONCRETE, 
            "&9+Z &7(Forward)",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // -Z (Backward)
        inventory.setItem(12, createItem(Material.BLUE_STAINED_GLASS, 
            "&9-Z &7(Backward)",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // === ROTATION BUTTONS ===
        // Rotate X
        inventory.setItem(29, createItem(Material.RED_WOOL, 
            "&cRotate X",
            "&7Click: +15°", "&7Right-Click: -15°", "&7Shift: ±5°"));
        
        // Rotate Y
        inventory.setItem(30, createItem(Material.LIME_WOOL, 
            "&aRotate Y",
            "&7Click: +15°", "&7Right-Click: -15°", "&7Shift: ±5°"));
        
        // Rotate Z
        inventory.setItem(31, createItem(Material.BLUE_WOOL, 
            "&9Rotate Z",
            "&7Click: +15°", "&7Right-Click: -15°", "&7Shift: ±5°"));
        
        // Reset rotation
        inventory.setItem(32, createItem(Material.PURPLE_CONCRETE, 
            "&7⟲ Reset Rotation"));
        
        // === SCALE BUTTONS ===
        // Scale up
        inventory.setItem(15, createItem(Material.YELLOW_CONCRETE, 
            "&e↑ Scale Up",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // Scale down
        inventory.setItem(16, createItem(Material.YELLOW_STAINED_GLASS, 
            "&e↓ Scale Down",
            "&7Shift+Click: x0.5", "&7Click: x1", "&7Right-Click: x5"));
        
        // Reset scale
        inventory.setItem(24, createItem(Material.ORANGE_CONCRETE, 
            "&7⟲ Reset Scale"));
        
        // === GIZMO TOGGLE ===
        boolean gizmoActive = plugin.getGizmoManager().hasActiveGizmo(player);
        String gizmoStatus = gizmoActive ? 
            "&aEnabled" : 
            "&cDisabled";
        
        inventory.setItem(40, createItem(
            gizmoActive ? Material.GLOWSTONE : Material.REDSTONE_LAMP,
            "&6✦ Toggle Gizmo",
            Lang.colorizeList(List.of("&7", "&fEnables/disables visual gizmo.", "&7Currently: &e{status}", "&7", "&eClick to toggle!")).stream()
                .map(line -> line.replace("{status}", gizmoStatus))
                .toList()
        ));
        
        // Gizmo mode buttons (if gizmo is active)
        if (gizmoActive) {
            inventory.setItem(38, createItem(Material.COMPASS, 
                "&eMode: &fMove",
                "&7Click to activate move mode."));
            
            inventory.setItem(39, createItem(Material.RECOVERY_COMPASS, 
                "&eMode: &fRotate",
                "&7Click to activate rotate mode."));
            
            inventory.setItem(41, createItem(Material.SPYGLASS, 
                "&eMode: &fScale",
                "&7Click to activate scale mode."));
        }
        
        // Back button
        inventory.setItem(45, getBackButton());
        
        // Close button
        inventory.setItem(53, getCloseButton());
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        double multiplier = getMultiplier(clickType);
        
        switch (slot) {
            // Movement
            case 11 -> moveDisplay(moveSensitivity * multiplier, 0, 0);
            case 10 -> moveDisplay(-moveSensitivity * multiplier, 0, 0);
            case 4 -> moveDisplay(0, moveSensitivity * multiplier, 0);
            case 22 -> moveDisplay(0, -moveSensitivity * multiplier, 0);
            case 13 -> moveDisplay(0, 0, moveSensitivity * multiplier);
            case 12 -> moveDisplay(0, 0, -moveSensitivity * multiplier);
            
            // Rotation
            case 29 -> rotateDisplay(getRotationAmount(clickType), 0, 0);
            case 30 -> rotateDisplay(0, getRotationAmount(clickType), 0);
            case 31 -> rotateDisplay(0, 0, getRotationAmount(clickType));
            case 32 -> resetRotation();
            
            // Scale
            case 15 -> scaleDisplay(scaleSensitivity * multiplier);
            case 16 -> scaleDisplay(-scaleSensitivity * multiplier);
            case 24 -> resetScale();
            
            // Gizmo
            case 40 -> {
                if (plugin.getGizmoManager().hasActiveGizmo(player)) {
                    plugin.getGizmoManager().stopGizmo(player);
                } else {
                    plugin.getGizmoManager().startGizmo(player, display);
                }
                createInventory();
            }
            case 38 -> {
                plugin.getGizmoManager().setMode(player, GizmoMode.MOVE);
                player.sendMessage(Lang.getPrefixed("&aActivated mode: &eMove"));
            }
            case 39 -> {
                plugin.getGizmoManager().setMode(player, GizmoMode.ROTATE);
                player.sendMessage(Lang.getPrefixed("&aActivated mode: &eRotate"));
            }
            case 41 -> {
                plugin.getGizmoManager().setMode(player, GizmoMode.SCALE);
                player.sendMessage(Lang.getPrefixed("&aActivated mode: &eScale"));
            }
            
            // Navigation
            case 45 -> {
                player.closeInventory();
                new EditMenuGUI(plugin, player, display).open();
            }
            case 53 -> player.closeInventory();
        }
    }

    private double getMultiplier(ClickType clickType) {
        if (clickType.isShiftClick()) {
            return 0.5;
        } else if (clickType.isRightClick()) {
            return 5.0;
        }
        return 1.0;
    }

    private double getRotationAmount(ClickType clickType) {
        double base = clickType.isShiftClick() ? 5.0 : 15.0;
        return clickType.isRightClick() ? -base : base;
    }

    private void moveDisplay(double x, double y, double z) {
        Location loc = display.getLocation();
        loc.add(x, y, z);
        display.teleport(loc);
    }

    private void rotateDisplay(double x, double y, double z) {
        Transformation transformation = display.getTransformation();
        
        Quaternionf leftRotation = transformation.getLeftRotation();
        
        // Convert degrees to radians and apply rotation
        float radX = (float) Math.toRadians(x);
        float radY = (float) Math.toRadians(y);
        float radZ = (float) Math.toRadians(z);
        
        Quaternionf additionalRotation = new Quaternionf()
            .rotateXYZ(radX, radY, radZ);
        
        leftRotation.mul(additionalRotation);
        
        Transformation newTransformation = new Transformation(
            transformation.getTranslation(),
            leftRotation,
            transformation.getScale(),
            transformation.getRightRotation()
        );
        
        display.setTransformation(newTransformation);
    }

    private void scaleDisplay(double amount) {
        Transformation transformation = display.getTransformation();
        Vector3f scale = transformation.getScale();
        
        float newScale = Math.max(0.1f, scale.x + (float) amount);
        
        Transformation newTransformation = new Transformation(
            transformation.getTranslation(),
            transformation.getLeftRotation(),
            new Vector3f(newScale, newScale, newScale),
            transformation.getRightRotation()
        );
        
        display.setTransformation(newTransformation);
    }

    private void resetScale() {
        Transformation transformation = display.getTransformation();
        
        Transformation newTransformation = new Transformation(
            transformation.getTranslation(),
            transformation.getLeftRotation(),
            new Vector3f(1f, 1f, 1f),
            transformation.getRightRotation()
        );
        
        display.setTransformation(newTransformation);
    }

    private void resetRotation() {
        Transformation transformation = display.getTransformation();
        
        Transformation newTransformation = new Transformation(
            transformation.getTranslation(),
            new Quaternionf(0, 0, 0, 1), // Identity quaternion - no rotation
            transformation.getScale(),
            new Quaternionf(0, 0, 0, 1)
        );
        
        display.setTransformation(newTransformation);
    }
}
