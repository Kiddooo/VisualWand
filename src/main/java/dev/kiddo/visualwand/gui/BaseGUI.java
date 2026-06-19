package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public abstract class BaseGUI implements InventoryHolder {

    protected final VisualWand plugin;
    protected final Player player;
    protected Inventory inventory;

    public BaseGUI(VisualWand plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    protected abstract void createInventory();
    
    public abstract void handleClick(int slot, ItemStack item, ClickType clickType);
    
    public void handleClose() {
        // Override in subclasses if needed
    }

    public void open() {
        createInventory();
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(Lang.colorize(name));
            if (lore.length > 0) {
                meta.setLore(Arrays.stream(lore).map(Lang::colorize).toList());
            }
            item.setItemMeta(meta);
        }
        
        return item;
    }

    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            meta.setDisplayName(Lang.colorize(name));
            meta.setLore(lore.stream().map(Lang::colorize).toList());
            item.setItemMeta(meta);
        }
        
        return item;
    }

    protected void fillBorder(Material material) {
        ItemStack border = createItem(material, " ");
        int size = inventory.getSize();
        int rows = size / 9;
        
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, border);
            inventory.setItem(size - 9 + i, border);
        }
        
        for (int i = 1; i < rows - 1; i++) {
            inventory.setItem(i * 9, border);
            inventory.setItem(i * 9 + 8, border);
        }
    }

    protected void fillEmpty(Material material) {
        ItemStack filler = createItem(material, " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    protected ItemStack getBackButton() {
        return createItem(Material.ARROW, plugin.getLang().get("gui-back"));
    }

    protected ItemStack getCloseButton() {
        return createItem(Material.BARRIER, plugin.getLang().get("gui-close"));
    }
}
