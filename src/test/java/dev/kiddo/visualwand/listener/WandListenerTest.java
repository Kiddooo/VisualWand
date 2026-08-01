package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.editor.EditorConfiguration;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.editor.EditorSession;
import dev.kiddo.visualwand.util.WandItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WandListenerTest {

    private final UUID playerId = new UUID(1L, 2L);
    private VisualWand plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private BukkitTask hoverTask;
    private WandItem wandItem;
    private EditorManager editorManager;
    private EditorConfiguration configuration;
    private FileConfiguration bukkitConfiguration;
    private Player player;
    private PlayerInventory playerInventory;
    private InventoryView inventoryView;
    private Inventory topInventory;
    private World world;
    private Location eye;
    private Runnable hoverRunnable;
    private WandListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(VisualWand.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        hoverTask = mock(BukkitTask.class);
        wandItem = mock(WandItem.class);
        editorManager = mock(EditorManager.class);
        configuration = mock(EditorConfiguration.class);
        bukkitConfiguration = mock(FileConfiguration.class);
        player = mock(Player.class);
        playerInventory = mock(PlayerInventory.class);
        inventoryView = mock(InventoryView.class);
        topInventory = mock(Inventory.class);
        world = mock(World.class);
        ItemStack wand = mock(ItemStack.class);
        eye = new Location(world, 0.0D, 64.0D, 0.0D, 0.0F, 0.0F);

        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(plugin.getConfig()).thenReturn(bukkitConfiguration);
        when(bukkitConfiguration.getBoolean(
                "editor.targeting.highlight-enabled", true)).thenReturn(true);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
                .thenAnswer(invocation -> {
                    hoverRunnable = invocation.getArgument(1);
                    return hoverTask;
                });
        when(plugin.getWandItem()).thenReturn(wandItem);
        when(plugin.getEditorManager()).thenReturn(editorManager);
        when(editorManager.configuration()).thenReturn(configuration);
        when(configuration.targetCycleRange()).thenReturn(8.0D);
        when(configuration.maxDistance()).thenReturn(16.0D);
        when(configuration.feedbackUpdateIntervalTicks()).thenReturn(2);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.getItemInMainHand()).thenReturn(wand);
        when(wandItem.isWand(wand)).thenReturn(true);
        when(player.hasPermission("visualwand.use")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(inventoryView);
        when(inventoryView.getTopInventory()).thenReturn(topInventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getEyeLocation()).thenReturn(eye);
        when(player.isOnline()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of());

        listener = new WandListener(plugin);
    }

    @Test
    void adjacentSneakingScrollIsCancelledAndClearsExistingEditorSession() {
        when(player.isSneaking()).thenReturn(true);
        when(editorManager.session(player)).thenReturn(mock(EditorSession.class));
        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 2, 3);

        listener.onPlayerItemHeld(event);

        assertTrue(event.isCancelled());
        verify(editorManager).clear(player);
    }

    @Test
    void nonAdjacentSneakingSlotTransitionIsUntouched() {
        when(player.isSneaking()).thenReturn(true);
        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 2, 5);

        listener.onPlayerItemHeld(event);

        assertFalse(event.isCancelled());
        verify(editorManager, never()).clear(player);
    }

    @Test
    void adjacentNonSneakingSlotTransitionIsUntouched() {
        when(player.isSneaking()).thenReturn(false);
        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 2, 3);

        listener.onPlayerItemHeld(event);

        assertFalse(event.isCancelled());
        verify(editorManager, never()).clear(player);
    }

    @Test
    void initiallyDisabledHighlightDoesNotGlowCyclePreview() {
        TextDisplay display = eligibleTextDisplay(new UUID(21L, 22L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(bukkitConfiguration.getBoolean(
                "editor.targeting.highlight-enabled", true)).thenReturn(false);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);

        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 2, 3);
        listener.onPlayerItemHeld(event);

        assertTrue(event.isCancelled());
        verify(display, never()).setGlowing(true);
        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void selectedDisplayIsExcludedFromCyclePreview() {
        TextDisplay display = eligibleTextDisplay(new UUID(23L, 24L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(editorManager.isSelected(display.getUniqueId())).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        verify(editorManager).isSelected(display.getUniqueId());
        verify(display, never()).setGlowing(true);
        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void invalidatedHoverPreviewLeavesRightClickFailClosed() {
        TextDisplay display = eligibleTextDisplay(new UUID(25L, 26L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        when(display.isValid()).thenReturn(false);
        hoverRunnable.run();
        clearInvocations(player, world, editorManager);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of());
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);

        listener.onPlayerInteract(event);

        verify(world, never()).getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any());
        verify(editorManager, never()).select(any(), any());
        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void stalePreviewRightClickFailsClosedWhileSneaking() {
        TextDisplay display = eligibleTextDisplay(new UUID(3L, 4L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        clearInvocations(player);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of());
        when(server.getEntity(display.getUniqueId())).thenReturn(null);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);

        listener.onPlayerInteract(event);

        verify(player).sendActionBar(any(Component.class));
        verify(editorManager, never()).select(any(), any());
    }

    @Test
    void activePreviewRightClickSelectsWhileSneaking() {
        TextDisplay display = eligibleTextDisplay(new UUID(7L, 8L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));
        clearInvocations(editorManager);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);

        listener.onPlayerInteract(event);

        verify(editorManager).select(player, display);
    }

    @Test
    void previewOutsideCurrentConeRightClickFailsClosed() {
        TextDisplay display = eligibleTextDisplay(new UUID(15L, 16L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        Location lookingAway = new Location(world, 0.0D, 64.0D, 0.0D, 180.0F, 0.0F);
        when(player.getEyeLocation()).thenReturn(lookingAway);
        clearInvocations(editorManager, player);
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);

        listener.onPlayerInteract(event);

        verify(editorManager, never()).select(any(), any());
        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void laterAcceptedStepAlsoClearsAnExistingEditorSession() {
        TextDisplay display = eligibleTextDisplay(new UUID(17L, 18L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        when(editorManager.session(player)).thenReturn(mock(EditorSession.class));
        clearInvocations(editorManager);
        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 3, 4));

        verify(editorManager).clear(player);
    }

    @Test
    void laterStepKeepsOriginalRingAndSkipsAnIneligibleUuid() {
        TextDisplay first = eligibleTextDisplay(new UUID(9L, 10L), 3.0D);
        TextDisplay second = eligibleTextDisplay(new UUID(11L, 12L), 4.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(first, second));
        when(server.getEntity(first.getUniqueId())).thenReturn(first);
        when(server.getEntity(second.getUniqueId())).thenReturn(second);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        clearInvocations(server, world, player);
        when(first.isValid()).thenReturn(false);
        PlayerItemHeldEvent event = new PlayerItemHeldEvent(player, 3, 4);

        listener.onPlayerItemHeld(event);

        assertTrue(event.isCancelled());
        verify(world, never()).getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any());
        verify(server).getEntity(first.getUniqueId());
        verify(server, times(2)).getEntity(second.getUniqueId());
        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    void reloadClearsCyclePreviewAndRestoresGlow() {
        TextDisplay display = eligibleTextDisplay(new UUID(5L, 6L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));
        verify(display).setGlowing(true);
        clearInvocations(display);

        listener.reload();

        verify(display).setGlowing(false);
    }

    @Test
    void disabledHighlightTickClearsCycleWhenHoverContextBecomesInvalid() {
        TextDisplay display = eligibleTextDisplay(new UUID(13L, 14L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        when(bukkitConfiguration.getBoolean(
                "editor.targeting.highlight-enabled", true)).thenReturn(false);
        when(editorManager.session(player)).thenReturn(mock(EditorSession.class));
        hoverRunnable.run();
        clearInvocations(editorManager, world);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 3, 4));

        verify(editorManager).clear(player);
        verify(world, times(2)).getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void disabledHighlightTickClearsCycleWhenPreviewBecomesIneligible() {
        TextDisplay display = eligibleTextDisplay(new UUID(19L, 20L), 3.0D);
        when(player.isSneaking()).thenReturn(true);
        when(world.getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.of(display));
        when(server.getEntity(display.getUniqueId())).thenReturn(display);
        doReturn(List.of(player)).when(server).getOnlinePlayers();

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 2, 3));

        when(bukkitConfiguration.getBoolean(
                "editor.targeting.highlight-enabled", true)).thenReturn(false);
        when(display.isValid()).thenReturn(false);
        hoverRunnable.run();
        when(editorManager.session(player)).thenReturn(mock(EditorSession.class));
        clearInvocations(editorManager, world);

        listener.onPlayerItemHeld(new PlayerItemHeldEvent(player, 3, 4));

        verify(editorManager).clear(player);
        verify(world, times(2)).getNearbyEntities(
                any(Location.class), anyDouble(), anyDouble(), anyDouble(), any());
    }

    private TextDisplay eligibleTextDisplay(UUID displayId, double z) {
        TextDisplay display = mock(TextDisplay.class);
        Location location = new Location(world, 0.0D, 64.0D, z);
        Transformation transformation = new Transformation(
                new Vector3f(),
                new Quaternionf(),
                new Vector3f(1.0F, 1.0F, 1.0F),
                new Quaternionf());
        when(display.getUniqueId()).thenReturn(displayId);
        when(display.getWorld()).thenReturn(world);
        when(display.getLocation()).thenReturn(location);
        when(display.getTransformation()).thenReturn(transformation);
        when(display.getBillboard()).thenReturn(Display.Billboard.FIXED);
        when(display.isValid()).thenReturn(true);
        when(display.isGlowing()).thenReturn(false);
        when(player.hasLineOfSight(display)).thenReturn(true);
        return display;
    }
}
