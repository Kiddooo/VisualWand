package dev.kiddo.visualwand.listener;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.gui.BaseGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GUIListenerTest {

    @Test
    void playerInventorySlotDoesNotTriggerGuiAction() {
        VisualWand plugin = mock(VisualWand.class);
        BaseGUI gui = mock(BaseGUI.class);
        Inventory topInventory = mock(Inventory.class);
        Inventory playerInventory = mock(Inventory.class);
        Player player = mock(Player.class);
        InventoryClickEvent event = mock(InventoryClickEvent.class);

        when(topInventory.getHolder()).thenReturn(gui);
        when(event.getInventory()).thenReturn(topInventory);
        when(event.getClickedInventory()).thenReturn(playerInventory);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getCurrentItem()).thenReturn(mock(ItemStack.class));
        when(event.getSlot()).thenReturn(16);

        new GUIListener(plugin).onInventoryClick(event);

        verify(event).setCancelled(true);
        verify(gui, never()).handleClick(anyInt(), any(), any());
    }
}
