package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.TransformationOperations;
import dev.kiddo.visualwand.gui.BaseGUI;
import dev.kiddo.visualwand.gui.EditMenuGUI;
import dev.kiddo.visualwand.gui.MainMenuGUI;
import dev.kiddo.visualwand.gui.TransformMenuGUI;
import dev.kiddo.visualwand.util.Lang;
import net.kyori.adventure.text.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Normal wand creation, strict display targeting, selection, and confirmed deletion.
 * Active click editing is handled earlier by {@link EditorInputListener}.
 */
public final class WandListener implements Listener {

    private static final List<BoundingBox> ITEM_LOCAL_BOUNDS = List.of(
            new BoundingBox(-0.5D, -0.5D, -0.5D, 0.5D, 0.5D, 0.5D));
    private static final List<BoundingBox> TEXT_LOCAL_BOUNDS = List.of(
            new BoundingBox(-0.75D, -0.25D, -0.1D, 0.75D, 0.5D, 0.1D));

    private final VisualWand plugin;
    private final Map<UUID, UUID> hoverTargets = new HashMap<>();
    private final Map<UUID, HighlightState> highlightStates = new HashMap<>();
    private final Map<UUID, DeleteConfirmation> deleteConfirmations = new HashMap<>();
    private final BukkitTask hoverTask;
    private long hoverTicks;

    public WandListener(VisualWand plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.hoverTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::tickHover,
                1L,
                1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (!isHoldingWand(player)) {
            return;
        }
        if (!player.hasPermission("visualwand.use")) {
            player.sendMessage(Lang.getPrefixed("&cYou don't have permission for this action!"));
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        clearHover(player.getUniqueId());

        if (player.isSneaking()) {
            handleDeleteDisplay(player);
            return;
        }

        clearDeleteConfirmation(player);
        Display display = getTargetedDisplay(player);
        if (display == null) {
            openCreateMenu(player);
            return;
        }
        if (plugin.getEditorManager().isLocked(display)) {
            player.sendMessage(Lang.getPrefixed(
                    "&eThis display is locked. Use the redstone torch to unlock it."));
            new TransformMenuGUI(plugin, player, display).open();
            return;
        }

        if (plugin.getEditorManager().select(player, display)) {
            openEditMenu(player, display);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearHover(event.getPlayer().getUniqueId());
        clearDeleteConfirmation(event.getPlayer());
    }

    public void shutdown() {
        hoverTask.cancel();
        for (UUID playerId : List.copyOf(hoverTargets.keySet())) {
            clearHover(playerId);
        }
        restoreRemainingHighlights();
        deleteConfirmations.clear();
    }

    private void tickHover() {
        hoverTicks++;
        boolean enabled = plugin.getConfig().getBoolean("editor.targeting.highlight-enabled", true);
        int interval = Math.max(
                1,
                plugin.getConfig().getInt("editor.targeting.update-interval", 2));

        if (!enabled) {
            for (UUID playerId : List.copyOf(hoverTargets.keySet())) {
                clearHover(playerId);
            }
        } else if (hoverTicks % interval == 0L) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                updateHoverTarget(player);
            }
        }
        pruneDeleteConfirmations();
    }

    private void updateHoverTarget(Player player) {
        if (!player.isOnline()
                || !isHoldingWand(player)
                || !player.hasPermission("visualwand.use")
                || plugin.getEditorManager().session(player) != null
                || player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGUI) {
            clearHover(player.getUniqueId());
            return;
        }

        Display target = getTargetedDisplay(player);
        if (target != null && plugin.getEditorManager().isSelected(target.getUniqueId())) {
            target = null;
        }
        setHoverTarget(player.getUniqueId(), target);

        int feedbackInterval = plugin.getEditorManager()
                .configuration()
                .feedbackUpdateIntervalTicks();
        if (target != null && hoverTicks % feedbackInterval == 0L) {
            double distance = player.getEyeLocation().distance(target.getLocation());
            player.sendActionBar(describeDisplay(target, distance));
        }
    }

    private void setHoverTarget(UUID playerId, Display target) {
        UUID targetId = target == null ? null : target.getUniqueId();
        if (Objects.equals(hoverTargets.get(playerId), targetId)) {
            return;
        }

        clearHover(playerId);
        if (target == null || !target.isValid()) {
            return;
        }

        HighlightState state = highlightStates.get(targetId);
        if (state == null) {
            highlightStates.put(targetId, new HighlightState(target.isGlowing()));
        } else {
            state.references++;
        }
        target.setGlowing(true);
        hoverTargets.put(playerId, targetId);
    }

    private void clearHover(UUID playerId) {
        UUID previousId = hoverTargets.remove(playerId);
        if (previousId == null) {
            return;
        }

        HighlightState state = highlightStates.get(previousId);
        if (state == null) {
            return;
        }
        state.references--;
        if (state.references > 0) {
            return;
        }

        highlightStates.remove(previousId);
        Entity resolved = plugin.getServer().getEntity(previousId);
        if (resolved instanceof Display display && display.isValid()) {
            display.setGlowing(state.originalGlowing);
        }
    }

    private void clearHoverOfDisplay(UUID displayId) {
        for (Map.Entry<UUID, UUID> entry : List.copyOf(hoverTargets.entrySet())) {
            if (entry.getValue().equals(displayId)) {
                clearHover(entry.getKey());
            }
        }
    }

    private void restoreRemainingHighlights() {
        for (Map.Entry<UUID, HighlightState> entry : highlightStates.entrySet()) {
            Entity resolved = plugin.getServer().getEntity(entry.getKey());
            if (resolved instanceof Display display && display.isValid()) {
                display.setGlowing(entry.getValue().originalGlowing);
            }
        }
        highlightStates.clear();
        hoverTargets.clear();
    }

    /**
     * Finds the nearest display whose actual selection geometry intersects the crosshair.
     */
    private Display getTargetedDisplay(Player player) {
        Location eye = player.getEyeLocation();
        Vector rayStart = eye.toVector();
        Vector rayDirection = normalizedDirection(eye);
        double maximumDistance = plugin.getEditorManager().configuration().maxDistance();

        Display bestDisplay = null;
        double bestDistance = maximumDistance;
        World world = player.getWorld();
        for (Entity entity : world.getNearbyEntities(
                eye,
                maximumDistance,
                maximumDistance,
                maximumDistance,
                candidate -> candidate instanceof BlockDisplay
                        || candidate instanceof ItemDisplay
                        || candidate instanceof TextDisplay)) {
            Display display = (Display) entity;
            if (!display.isValid()
                    || !TransformationOperations.isFinite(display.getLocation())
                    || !TransformationOperations.isFinite(display.getTransformation())) {
                continue;
            }

            double hitDistance = getDisplayHitDistance(
                    display,
                    rayStart,
                    rayDirection,
                    maximumDistance,
                    eye.getYaw(),
                    eye.getPitch());
            if (hitDistance >= 0.0D && hitDistance < bestDistance) {
                bestDistance = hitDistance;
                bestDisplay = display;
            }
        }
        return bestDisplay;
    }

    private double getDisplayHitDistance(
            Display display,
            Vector rayStart,
            Vector rayDirection,
            double maximumDistance,
            float cameraYaw,
            float cameraPitch) {
        List<BoundingBox> localBounds;
        DisplayHitbox.ModelTransform modelTransform = DisplayHitbox.ModelTransform.IDENTITY;
        if (display instanceof BlockDisplay blockDisplay) {
            localBounds = getBlockDisplayLocalBounds(blockDisplay);
        } else if (display instanceof ItemDisplay itemDisplay) {
            localBounds = ITEM_LOCAL_BOUNDS;
            modelTransform = DisplayHitbox.itemModelTransform(
                    itemDisplay.getItemDisplayTransform(),
                    itemDisplay.getItemStack().getType().isBlock());
        } else if (display instanceof TextDisplay) {
            localBounds = TEXT_LOCAL_BOUNDS;
        } else {
            return -1.0D;
        }
        if (localBounds.isEmpty()) {
            return -1.0D;
        }

        Location location = display.getLocation();
        return DisplayHitbox.rayTrace(
                rayStart,
                rayDirection,
                maximumDistance,
                location.toVector(),
                DisplayHitbox.facingRotation(
                        display.getBillboard(),
                        location.getYaw(),
                        location.getPitch(),
                        cameraYaw,
                        cameraPitch),
                display.getTransformation(),
                modelTransform,
                localBounds);
    }

    /**
     * Returns the vanilla block state's voxel shape in display-local coordinates.
     */
    private List<BoundingBox> getBlockDisplayLocalBounds(BlockDisplay display) {
        VoxelShape shape = getBlockDisplayVoxelShape(display);
        if (shape == null || shape.isEmpty()) {
            return List.of();
        }

        List<AABB> shapeBoxes = shape.toAabbs();
        List<BoundingBox> localBounds = new ArrayList<>(shapeBoxes.size());
        for (AABB localBox : shapeBoxes) {
            localBounds.add(new BoundingBox(
                    localBox.minX,
                    localBox.minY,
                    localBox.minZ,
                    localBox.maxX,
                    localBox.maxY,
                    localBox.maxZ));
        }
        return localBounds;
    }

    private VoxelShape getBlockDisplayVoxelShape(BlockDisplay display) {
        if (!(display.getBlock() instanceof CraftBlockData craftBlockData)) {
            return null;
        }

        BlockState blockState = craftBlockData.getState();
        CraftWorld craftWorld = (CraftWorld) display.getWorld();
        Location location = display.getLocation();
        BlockPos blockPosition = BlockPos.containing(
                location.getX(),
                location.getY(),
                location.getZ());
        return blockState.getShape(craftWorld.getHandle(), blockPosition);
    }

    private Component describeDisplay(Display display, double distance) {
        String formattedDistance = String.format(java.util.Locale.ROOT, "%.2f", distance);
        if (display instanceof BlockDisplay blockDisplay) {
            return Lang.getComponent(
                    "&fBlock display: &e"
                            + blockDisplay.getBlock().getMaterial().name()
                            + " &8| &f"
                            + formattedDistance
                            + " blocks");
        }
        if (display instanceof ItemDisplay itemDisplay) {
            return Lang.getComponent(
                    "&fItem display: &e"
                            + itemDisplay.getItemStack().getType().name()
                            + " &8| &f"
                            + formattedDistance
                            + " blocks");
        }
        return Lang.getComponent("&fText display &8| &f" + formattedDistance + " blocks");
    }

    private boolean isHoldingWand(Player player) {
        return plugin.getWandItem().isWand(player.getInventory().getItemInMainHand());
    }

    private void openCreateMenu(Player player) {
        new MainMenuGUI(plugin, player).open();
    }

    private void openEditMenu(Player player, Display display) {
        player.sendMessage(Lang.getPrefixed(
                "&aOpened editor for: &e" + getDisplayTypeName(display)));
        new EditMenuGUI(plugin, player, display).open();
    }

    private void handleDeleteDisplay(Player player) {
        Display display = getTargetedDisplay(player);
        if (display == null) {
            clearDeleteConfirmation(player);
            player.sendMessage(Lang.getPrefixed("&cNo display object found in line of sight!"));
            return;
        }

        if (plugin.getEditorManager().isLocked(display)) {
            clearDeleteConfirmation(player);
            player.sendMessage(Lang.getPrefixed(
                    "&eThis display is locked. Use the redstone torch to unlock it."));
            new TransformMenuGUI(plugin, player, display).open();
            return;
        }

        UUID playerId = player.getUniqueId();
        DeleteConfirmation pending = deleteConfirmations.get(playerId);
        long now = System.currentTimeMillis();
        if (pending != null) {
            Entity pendingEntity = plugin.getServer().getEntity(pending.displayId());
            if (!(pendingEntity instanceof Display pendingDisplay)
                    || !pendingDisplay.isValid()
                    || pending.expiresAtMillis() <= now) {
                deleteConfirmations.remove(playerId);
                player.sendMessage(Lang.getPrefixed("&eDelete confirmation expired."));
            } else if (pending.displayId().equals(display.getUniqueId())) {
                deleteConfirmations.remove(playerId);
                deleteDisplay(player, display);
                return;
            } else {
                armDeleteConfirmation(player, display, true);
                return;
            }
        }
        armDeleteConfirmation(player, display, false);
    }

    private void armDeleteConfirmation(Player player, Display display, boolean targetChanged) {
        long timeoutMillis = deleteConfirmationTimeoutMillis();
        deleteConfirmations.put(
                player.getUniqueId(),
                new DeleteConfirmation(
                        display.getUniqueId(),
                        System.currentTimeMillis() + timeoutMillis));

        String seconds = formatSeconds(timeoutMillis);
        String type = getDisplayTypeName(display);
        player.sendMessage(Lang.getPrefixed(targetChanged
                ? "&eDelete target changed. &fCrouch + right-click again within &e"
                        + seconds + "s &fto confirm deleting " + type + "."
                : "&cDelete " + type + "? &fCrouch + right-click again within &e"
                        + seconds + "s &fto confirm."));
        player.sendActionBar(Lang.getComponent(
                "&cDelete armed &8| &f" + type
                        + " &8| &fCrouch + right-click again within &e" + seconds + "s"));
    }

    private void deleteDisplay(Player player, Display display) {
        UUID displayId = display.getUniqueId();
        clearHoverOfDisplay(displayId);
        clearDeleteConfirmation(player);
        plugin.getEditorManager().clearEditorsOf(displayId);
        plugin.getAnimationManager().stopAnimation(display);
        display.remove();
        player.sendMessage(Lang.getPrefixed("&cDeleted " + getDisplayTypeName(display) + "."));
    }

    private void clearDeleteConfirmation(Player player) {
        deleteConfirmations.remove(player.getUniqueId());
    }

    private void pruneDeleteConfirmations() {
        long now = System.currentTimeMillis();
        deleteConfirmations.entrySet().removeIf(entry -> {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            Entity resolved = plugin.getServer().getEntity(entry.getValue().displayId());
            return player == null
                    || !player.isOnline()
                    || !isHoldingWand(player)
                    || !(resolved instanceof Display display)
                    || !display.isValid()
                    || entry.getValue().expiresAtMillis() <= now;
        });
    }

    private long deleteConfirmationTimeoutMillis() {
        double configuredSeconds = plugin.getConfig().getDouble(
                "editor.delete-confirmation.timeout-seconds",
                5.0D);
        if (!Double.isFinite(configuredSeconds) || configuredSeconds <= 0.0D) {
            configuredSeconds = 5.0D;
        }
        return Math.max(1_000L, Math.round(configuredSeconds * 1_000.0D));
    }

    private static Vector normalizedDirection(Location location) {
        Vector direction = location.getDirection();
        if (!TransformationOperations.isFinite(location)
                || direction.lengthSquared() < 1.0E-8D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
    }

    private static String formatSeconds(long millis) {
        double seconds = millis / 1_000.0D;
        if (Math.abs(seconds - Math.rint(seconds)) < 1.0E-9D) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", seconds);
    }

    private static String getDisplayTypeName(Display display) {
        if (display instanceof BlockDisplay) {
            return "Block Display";
        }
        if (display instanceof ItemDisplay) {
            return "Item Display";
        }
        if (display instanceof TextDisplay) {
            return "Text Display";
        }
        return "Display";
    }

    private static final class HighlightState {
        private final boolean originalGlowing;
        private int references = 1;

        private HighlightState(boolean originalGlowing) {
            this.originalGlowing = originalGlowing;
        }
    }

    private record DeleteConfirmation(UUID displayId, long expiresAtMillis) {
    }
}
