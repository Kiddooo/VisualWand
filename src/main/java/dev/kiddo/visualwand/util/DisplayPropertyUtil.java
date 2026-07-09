package dev.kiddo.visualwand.util;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Shared helpers for display property controls.
 *
 * The property menu needs a few calculations that are not GUI-specific: clean
 * numeric formatting, display orientation presets, and collision-surface
 * snapping. Keeping those details here keeps PropertiesMenuGUI focused on menu
 * layout and click routing.
 */
public final class DisplayPropertyUtil {
    private static final float EPSILON = 1.0E-4F;
    private static final double ITEM_FLAT_CLEARANCE = 0.0D;
    private static final double ITEM_UPRIGHT_CLEARANCE = 0.0D;
    private static final double ITEM_FLAT_HALF_THICKNESS = 0.034D;

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

    /**
     * Applies the item orientation preset used by the property menu.
     *
     * Left-click lays the item flat. Right-click restores an upright NONE-preset
     * orientation. Shift-click also snaps the item to the surface below after the
     * orientation change.
     *
     * @param display item display to change
     * @param player player receiving feedback
     * @param clickType inventory click type
     */
    public static void applyItemPreset(
            ItemDisplay display,
            Player player,
            ClickType clickType
    ) {
        if (display == null || player == null || clickType == null || !display.isValid()) {
            return;
        }

        boolean flat = !clickType.isRightClick();
        setItemOrientation(display, flat);

        // Item presets are placement presets too. After changing orientation,
        // immediately resolve the surface Y again so upright items do not remain
        // embedded where the flat preset previously placed them.
        placeOnSurface(display, player, false);
        player.sendMessage(Lang.getPrefixed(flat
                ? "&aItem laid flat."
                : "&aItem returned to an upright orientation."));
    }

    /**
     * Snaps a block or item display to the first collision surface below it.
     *
     * Left-click keeps the current horizontal position. Right-click centres the
     * display on the supporting block after the surface has been resolved.
     *
     * @param display display to move
     * @param player player receiving feedback
     * @param clickType inventory click type
     */
    public static void placeOnSurface(
            Display display,
            Player player,
            ClickType clickType
    ) {
        if (clickType == null) {
            return;
        }

        placeOnSurface(display, player, clickType.isRightClick());
    }

    private static void placeOnSurface(
            Display display,
            Player player,
            boolean centreOnBlock
    ) {
        if (display == null || player == null || !display.isValid()) {
            return;
        }

        if (!(display instanceof BlockDisplay) && !(display instanceof ItemDisplay)) {
            player.sendMessage(Lang.getPrefixed("&cThis surface preset only supports block and item displays."));
            return;
        }

        Location current = display.getLocation();
        World world = current.getWorld();
        if (world == null) {
            player.sendMessage(Lang.getPrefixed("&cNo collision surface was found below this display."));
            return;
        }

        Surface surface = findSurfaceBelow(current, world);
        if (surface == null) {
            player.sendMessage(Lang.getPrefixed("&cNo collision surface was found below this display."));
            return;
        }

        Location target = current.clone();
        if (display instanceof BlockDisplay) {
            // Block display locations are block origins, not visual centres.
            // Snapping to +0.5 would place the rendered block between four blocks.
            target.setX(surface.block.getX());
            target.setZ(surface.block.getZ());
        } else if (centreOnBlock) {
            target.setX(surface.block.getX() + 0.5D);
            target.setZ(surface.block.getZ() + 0.5D);
        }

        double clearance = display instanceof ItemDisplay itemDisplay
                ? getItemDisplaySurfaceClearance(itemDisplay)
                : 0.0D;
        target.setY(surface.y - getMinimumYOffset(display) + clearance);
        display.teleport(target);
        player.sendMessage(Lang.getPrefixed("&aDisplay placed on the surface below."));
    }

    private static void setItemOrientation(ItemDisplay display, boolean flat) {
        Transformation current = display.getTransformation();

        // The preset rotates the display transformation itself, so any built-in
        // item display context (GROUND, FIXED, etc.) would fight with it. Switch
        // to NONE and fold GROUND's 0.5 scale into the display scale so the item
        // keeps roughly the same visual size instead of doubling.
        if (display.getItemDisplayTransform() == ItemDisplay.ItemDisplayTransform.GROUND) {
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            Vector3f scale = current.getScale();
            current = new Transformation(
                    current.getTranslation(),
                    current.getLeftRotation(),
                    new Vector3f(scale.x * 0.5F, scale.y * 0.5F, scale.z * 0.5F),
                    current.getRightRotation()
            );
        }

        Quaternionf leftRotation = flat
                ? new Quaternionf().rotateX((float) -Math.PI / 2.0F)
                : new Quaternionf();

        display.setBillboard(Display.Billboard.FIXED);

        Location location = display.getLocation();
        location.setYaw(0.0F);
        location.setPitch(0.0F);
        display.teleport(location);

        display.setTransformation(new Transformation(
                current.getTranslation(),
                leftRotation,
                current.getScale(),
                current.getRightRotation()
        ));
    }

    private static Surface findSurfaceBelow(Location current, World world) {
        int blockX = floor(current.getX());
        int blockZ = floor(current.getZ());
        int startY = Math.min(world.getMaxHeight() - 1, floor(current.getY() + 1.0D));

        for (int y = startY; y >= world.getMinHeight(); y--) {
            Block candidate = world.getBlockAt(blockX, y, blockZ);
            if (candidate.getType().isAir()) {
                continue;
            }

            Double topY = getTopCollisionY(candidate);
            if (topY == null) {
                continue;
            }

            if (topY <= current.getY() + 1.501D) {
                return new Surface(candidate, topY);
            }
        }

        return null;
    }

    public static Double getTopCollisionY(Block block) {
        VoxelShape shape = getCollisionShape(block);
        if (shape == null || shape.isEmpty()) {
            return null;
        }

        double maxY = Double.NEGATIVE_INFINITY;
        for (AABB box : shape.toAabbs()) {
            maxY = Math.max(maxY, box.maxY);
        }

        if (maxY == Double.NEGATIVE_INFINITY) {
            return null;
        }

        return block.getY() + maxY;
    }

    private static VoxelShape getCollisionShape(Block block) {
        BlockData blockData = block.getBlockData();
        if (!(blockData instanceof CraftBlockData craftBlockData)) {
            return null;
        }

        CraftWorld craftWorld = (CraftWorld) block.getWorld();
        BlockPos pos = BlockPos.containing(block.getX(), block.getY(), block.getZ());
        BlockState state = craftBlockData.getState();

        return state.getCollisionShape(
                craftWorld.getHandle(),
                pos,
                CollisionContext.empty()
        );
    }

    private static double getMinimumYOffset(Display display) {
        if (display instanceof BlockDisplay blockDisplay) {
            return getBlockDisplayMinimumYOffset(blockDisplay);
        }

        if (display instanceof ItemDisplay itemDisplay) {
            return getItemDisplayMinimumYOffset(itemDisplay);
        }

        return display.getTransformation().getTranslation().y;
    }

    private static double getBlockDisplayMinimumYOffset(BlockDisplay display) {
        VoxelShape shape = getBlockDisplayShape(display);
        Transformation transformation = display.getTransformation();

        if (shape == null || shape.isEmpty()) {
            return getTransformedMinimumY(transformation, new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        }

        double minY = Double.POSITIVE_INFINITY;
        for (AABB box : shape.toAabbs()) {
            minY = Math.min(minY, getTransformedMinimumY(transformation, box));
        }

        return minY == Double.POSITIVE_INFINITY ? 0.0D : minY;
    }

    private static VoxelShape getBlockDisplayShape(BlockDisplay display) {
        if (!(display.getBlock() instanceof CraftBlockData craftBlockData)) {
            return null;
        }

        CraftWorld craftWorld = (CraftWorld) display.getWorld();
        Location location = display.getLocation();
        BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        BlockState state = craftBlockData.getState();

        return state.getShape(
                craftWorld.getHandle(),
                pos,
                CollisionContext.empty()
        );
    }

    private static double getTransformedMinimumY(Transformation transformation, AABB box) {
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        double minY = Double.POSITIVE_INFINITY;

        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vector3f point = new Vector3f((float) x, (float) y, (float) z);
                    transformation.getRightRotation().transform(point);
                    point.mul(transformation.getScale());
                    transformation.getLeftRotation().transform(point);
                    point.add(transformation.getTranslation());
                    minY = Math.min(minY, point.y);
                }
            }
        }

        return minY;
    }

    private static double getItemDisplayMinimumYOffset(ItemDisplay display) {
        Transformation transformation = display.getTransformation();
        Vector3f translation = transformation.getTranslation();
        Vector3f scale = transformation.getScale();

        if (display.getItemDisplayTransform() == ItemDisplay.ItemDisplayTransform.GROUND) {
            // GROUND display transform: translate(0, +0.125, +0.25) then scale(0.5).
            // Model bottom at -0.5 (centred, 1.0-tall model).
            // After entity transform and GROUND: ((-0.5 + ty) * sy + 0.125) * 0.5
            return ((-0.5D + translation.y) * Math.abs(scale.y) + 0.125D) * 0.5D;
        }

        // NONE/FIXED/etc. render the model using only the display transformation.
        // A flat item is a thin sprite rotated horizontal; its bottom sits just
        // below the entity origin. An upright item spans translation.y ± 0.5*scale.y.
        return isFlatItem(display)
                ? translation.y - Math.abs(scale.z) * ITEM_FLAT_HALF_THICKNESS
                : translation.y - Math.abs(scale.y) * 0.5D;
    }

    private static double getItemDisplaySurfaceClearance(ItemDisplay display) {
        return isFlatItem(display) ? ITEM_FLAT_CLEARANCE : ITEM_UPRIGHT_CLEARANCE;
    }

    private static boolean isFlatItem(ItemDisplay display) {
        if (display.getItemDisplayTransform() != ItemDisplay.ItemDisplayTransform.NONE) {
            return false;
        }

        Vector3f normal = new Vector3f(0.0F, 0.0F, 1.0F);
        display.getTransformation().getLeftRotation().transform(normal);
        return normal.y > 0.85F;
    }

    private static int floor(double value) {
        int integer = (int) value;
        return value < integer ? integer - 1 : integer;
    }

    private static boolean near(float value, float expected) {
        return Math.abs(value - expected) <= EPSILON;
    }

    private static Vector3f copy(Vector3f vector) {
        return new Vector3f(vector.x, vector.y, vector.z);
    }

    private static final class Surface {
        private final Block block;
        private final double y;

        private Surface(Block block, double y) {
            this.block = block;
            this.y = y;
        }
    }
}
