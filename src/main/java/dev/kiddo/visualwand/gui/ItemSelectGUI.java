package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import dev.kiddo.visualwand.util.RayTraceUtil;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ItemSelectGUI extends BaseGUI {

    private int page = 0;
    private final List<Material> items;

    public ItemSelectGUI(VisualWand plugin, Player player) {
        super(plugin, player);
        this.items = getSelectableItems();
    }

    private List<Material> getSelectableItems() {
        List<Material> selectableItems = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem() && !material.isAir() 
                && !material.name().contains("LEGACY_")
                && !material.name().contains("SPAWN_EGG")) {
                selectableItems.add(material);
            }
        }
        return selectableItems;
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 54, Lang.colorize(plugin.getLang().get("gui-item-select-title")));
        populateItems();
    }

    private void populateItems() {
        inventory.clear();
        
        int startIndex = page * 45;
        int endIndex = Math.min(startIndex + 45, items.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Material material = items.get(i);
            inventory.setItem(i - startIndex, new ItemStack(material));
        }
        
        // Navigation buttons
        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "&a« Poprzednia strona"));
        }
        
        inventory.setItem(49, getCloseButton());
        
        // Custom Model Data button
        inventory.setItem(47, createItem(Material.COMMAND_BLOCK, 
            "&6Custom Model Data",
            "&7",
            "&fWpisz wartość CMD dla przedmiotu.",
            "&7Wymaga Resource Pack!",
            "&7",
            "&eKliknij aby ustawić!"));
        
        if (endIndex < items.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "&aNastępna strona »"));
        }
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        if (slot == 45 && page > 0) {
            page--;
            populateItems();
            return;
        }
        
        if (slot == 53 && (page + 1) * 45 < items.size()) {
            page++;
            populateItems();
            return;
        }
        
        if (slot == 47) {
            // Open CMD input
            player.closeInventory();
            plugin.getEditorManager().startCMDInput(player, null);
            return;
        }
        
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        
        if (item != null && item.getType().isItem()) {
            createItemDisplay(item.clone());
            player.closeInventory();
        }
    }

    private void createItemDisplay(ItemStack itemStack) {
        Location targetLocation = RayTraceUtil.getTargetLocation(
                player,
                plugin.getConfig().getDouble("editor.max-distance", 50)
        );

        Location spawnLocation = targetLocation.getBlock()
                .getLocation()
                .add(0.5D, 0.125D, 0.5D);

        player.getWorld().spawn(spawnLocation, ItemDisplay.class, itemDisplay -> {
            itemDisplay.setItemStack(itemStack);
            itemDisplay.setDisplayHeight(0.5F);
            itemDisplay.setDisplayWidth(0.5F);
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);

            plugin.getDisplayStorage().addDisplay(itemDisplay);
        });

        player.sendMessage(plugin.getLang().getPrefixed(
                "display-created",
                "type",
                "Item Display"
        ));
    }
}
