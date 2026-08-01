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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Normal wand creation, strict display targeting, and selection.
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
    private final Map<UUID, DisplayCycle> displayCycles = new HashMap<>();
    private final Set<UUID> failedDisplayCycles = new HashSet<>();
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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        DisplayCycle.Direction direction = DisplayCycle.Direction.fromSlots(
                event.getPreviousSlot(), event.getNewSlot());
        Player player = event.getPlayer();
        if (direction == null
                || !player.isSneaking()
                || !isHoldingWand(player)
                || !player.hasPermission("visualwand.use")
                || player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGUI) {
            return;
        }

        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        failedDisplayCycles.remove(playerId);
        if (plugin.getEditorManager().session(player) != null) {
            plugin.getEditorManager().clear(player);
        }
        DisplayCycle cycle = displayCycles.get(playerId);
        if (cycle == null) {
            startDisplayCycle(player, direction);
            return;
        }

        double range = plugin.getEditorManager().configuration().targetCycleRange();
        UUID targetId = cycle.advance(
                direction,
                candidateId -> resolveEligibleDisplay(player, candidateId, range) != null);
        Display target = targetId == null
                ? null
                : resolveEligibleDisplay(player, targetId, range);
        if (target == null) {
            showNoCycleTargets(player, range);
            clearDisplayCycle(playerId);
            return;
        }
        setCycleHoverTarget(playerId, target);
        showCycleTarget(player, target);
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

        UUID playerId = player.getUniqueId();
        DisplayCycle cycle = displayCycles.get(playerId);
        boolean hadCycle = cycle != null || failedDisplayCycles.contains(playerId);
        Display cycleTarget = cycle == null
                ? null
                : resolveEligibleDisplay(
                        player,
                        cycle.current(),
                        plugin.getEditorManager().configuration().targetCycleRange());

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
        clearDisplayCycle(playerId);

        if (hadCycle) {
            if (cycleTarget == null) {
                showUnavailableCycle(player);
                return;
            }
            selectDisplay(player, cycleTarget);
            return;
        }

        Display display = getTargetedDisplay(player);
        if (display == null) {
            openCreateMenu(player);
            return;
        }
        selectDisplay(player, display);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearDisplayCycle(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        hoverTask.cancel();
        for (UUID playerId : List.copyOf(displayCycles.keySet())) {
            clearDisplayCycle(playerId);
        }
        for (UUID playerId : List.copyOf(hoverTargets.keySet())) {
            clearHover(playerId);
        }
        restoreRemainingHighlights();
        displayCycles.clear();
        failedDisplayCycles.clear();
    }

    public void reload() {
        for (UUID playerId : List.copyOf(displayCycles.keySet())) {
            clearDisplayCycle(playerId);
        }
        for (UUID playerId : List.copyOf(hoverTargets.keySet())) {
            clearHover(playerId);
        }
        restoreRemainingHighlights();
        displayCycles.clear();
        failedDisplayCycles.clear();
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
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                UUID playerId = player.getUniqueId();
                DisplayCycle cycle = displayCycles.get(playerId);
                if (!isValidHoverContext(player)) {
                    if (cycle != null || failedDisplayCycles.contains(playerId)) {
                        clearDisplayCycle(playerId);
                    }
                } else if (cycle != null
                        && resolveEligibleDisplay(
                                player,
                                cycle.current(),
                                plugin.getEditorManager()
                                        .configuration()
                                        .targetCycleRange()) == null) {
                    failDisplayCycle(player);
                }
            }
        } else if (hoverTicks % interval == 0L) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                updateHoverTarget(player);
            }
        }
    }

    private void updateHoverTarget(Player player) {
        UUID playerId = player.getUniqueId();
        if (!isValidHoverContext(player)) {
            clearDisplayCycle(playerId);
            return;
        }
        if (failedDisplayCycles.contains(playerId)) {
            clearHover(playerId);
            return;
        }

        DisplayCycle cycle = displayCycles.get(playerId);
        if (cycle != null) {
            Display target = resolveEligibleDisplay(
                    player,
                    cycle.current(),
                    plugin.getEditorManager().configuration().targetCycleRange());
            if (target == null) {
                failDisplayCycle(player);
                return;
            }
            setCycleHoverTarget(playerId, target);
            int feedbackInterval = plugin.getEditorManager()
                    .configuration()
                    .feedbackUpdateIntervalTicks();
            if (hoverTicks % feedbackInterval == 0L) {
                showCycleTarget(player, target);
            }
            return;
        }

        Display target = getTargetedDisplay(player);
        if (target != null && plugin.getEditorManager().isSelected(target.getUniqueId())) {
            target = null;
        }
        setHoverTarget(playerId, target);

        int feedbackInterval = plugin.getEditorManager()
                .configuration()
                .feedbackUpdateIntervalTicks();
        if (target != null && hoverTicks % feedbackInterval == 0L) {
            double distance = player.getEyeLocation().distance(target.getLocation());
            player.sendActionBar(describeDisplay(target, distance));
        }
    }

    private boolean isValidHoverContext(Player player) {
        return player.isOnline()
                && isHoldingWand(player)
                && player.hasPermission("visualwand.use")
                && plugin.getEditorManager().session(player) == null
                && !(player.getOpenInventory().getTopInventory().getHolder() instanceof BaseGUI);
    }

    private void setCycleHoverTarget(UUID playerId, Display target) {
        if (plugin.getConfig().getBoolean("editor.targeting.highlight-enabled", true)) {
            setHoverTarget(playerId, target);
        } else {
            clearHover(playerId);
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

    private void clearDisplayCycle(UUID playerId) {
        displayCycles.remove(playerId);
        failedDisplayCycles.remove(playerId);
        clearHover(playerId);
    }

    private void failDisplayCycle(Player player) {
        UUID playerId = player.getUniqueId();
        displayCycles.remove(playerId);
        clearHover(playerId);
        failedDisplayCycles.add(playerId);
        showUnavailableCycle(player);
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

    private void startDisplayCycle(Player player, DisplayCycle.Direction direction) {
        double range = plugin.getEditorManager().configuration().targetCycleRange();
        Location eye = player.getEyeLocation();
        Vector viewDirection = normalizedDirection(eye);
        double rangeSquared = range * range;
        List<DisplayCycle.Candidate> candidates = new ArrayList<>();
        for (Entity entity : player.getWorld().getNearbyEntities(
                eye,
                range,
                range,
                range,
                WandListener::isSupportedDisplay)) {
            DisplayCycle.Candidate candidate = toCycleCandidate(
                    player, eye, viewDirection, rangeSquared, entity);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }

        Display exactTarget = getTargetedDisplay(player);
        DisplayCycle cycle = DisplayCycle.start(
                candidates,
                exactTarget == null ? null : exactTarget.getUniqueId(),
                direction);
        if (cycle == null) {
            showNoCycleTargets(player, range);
            clearDisplayCycle(player.getUniqueId());
            return;
        }

        UUID playerId = player.getUniqueId();
        displayCycles.put(playerId, cycle);
        Display target = resolveEligibleDisplay(player, cycle.current(), range);
        if (target == null) {
            UUID targetId = cycle.advance(
                    direction,
                    candidateId -> resolveEligibleDisplay(player, candidateId, range) != null);
            target = targetId == null
                    ? null
                    : resolveEligibleDisplay(player, targetId, range);
        }
        if (target == null) {
            showNoCycleTargets(player, range);
            clearDisplayCycle(playerId);
            return;
        }
        setCycleHoverTarget(playerId, target);
        showCycleTarget(player, target);
    }

    private static boolean isSupportedDisplay(Entity entity) {
        return entity instanceof BlockDisplay
                || entity instanceof ItemDisplay
                || entity instanceof TextDisplay;
    }

    private DisplayCycle.Candidate toCycleCandidate(
            Player player,
            Location eye,
            Vector viewDirection,
            double rangeSquared,
            Entity entity) {
        if (!(entity instanceof Display display) || !isSupportedDisplay(display)) {
            return null;
        }
        return eligibleCycleCandidate(
                player, eye, viewDirection, display, rangeSquared);
    }

    private Display resolveEligibleDisplay(Player player, UUID displayId, double range) {
        Entity resolved = plugin.getServer().getEntity(displayId);
        if (!(resolved instanceof Display display) || !isSupportedDisplay(display)) {
            return null;
        }
        Location eye = player.getEyeLocation();
        if (eligibleCycleCandidate(
                player,
                eye,
                normalizedDirection(eye),
                display,
                range * range) == null) {
            return null;
        }
        return display;
    }

    private DisplayCycle.Candidate eligibleCycleCandidate(
            Player player,
            Location eye,
            Vector viewDirection,
            Display display,
            double rangeSquared) {
        Location location = display.getLocation();
        if (!display.isValid()
                || !display.getWorld().equals(player.getWorld())
                || plugin.getEditorManager().isSelected(display.getUniqueId())
                || !TransformationOperations.isFinite(eye)
                || !TransformationOperations.isFinite(location)
                || !TransformationOperations.isFinite(display.getTransformation())
                || !Double.isFinite(rangeSquared)
                || eye.distanceSquared(location) > rangeSquared
                || !player.hasLineOfSight(display)) {
            return null;
        }
        Vector eyeToDisplay = location.toVector().subtract(eye.toVector());
        return DisplayCycle.candidate(display.getUniqueId(), viewDirection, eyeToDisplay);
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

    private void showCycleTarget(Player player, Display display) {
        double distance = player.getEyeLocation().distance(display.getLocation());
        player.sendActionBar(describeDisplay(display, distance).append(Lang.getComponent(
                " &8| &fShift+scroll to cycle &8| &fRMB to select")));
    }

    private static void showNoCycleTargets(Player player, double range) {
        player.sendActionBar(Lang.getComponent(
                "&eNo visible displays within &f" + formatRange(range) + "&e blocks."));
    }

    private static void showUnavailableCycle(Player player) {
        player.sendActionBar(Lang.getComponent("&eThat display is no longer available."));
    }

    private static String formatRange(double range) {
        return String.format(java.util.Locale.ROOT, "%.1f", range);
    }

    private void selectDisplay(Player player, Display display) {
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

    private static Vector normalizedDirection(Location location) {
        Vector direction = location.getDirection();
        if (!TransformationOperations.isFinite(location)
                || direction.lengthSquared() < 1.0E-8D) {
            return new Vector(0.0D, 0.0D, 1.0D);
        }
        return direction.normalize();
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

}
