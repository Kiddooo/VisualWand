package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import dev.kiddo.visualwand.util.RayTraceUtil;
import dev.kiddo.visualwand.util.DisplayPropertyUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

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
        inventory = Bukkit.createInventory(this, 54, Lang.colorize("&8✦ &6Select Item"));
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
            inventory.setItem(45, createItem(Material.ARROW, "&a← Previous page"));
        }
        
        inventory.setItem(49, getCloseButton());
        
        
        if (endIndex < items.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "&aNext page →"));
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
        double maxDistance = plugin.getConfig().getDouble("editor.max-distance", 50);
        RayTraceResult hit = player.rayTraceBlocks(maxDistance, FluidCollisionMode.NEVER);

        Location spawnLocation;
        if (hit != null && hit.getHitBlock() != null) {
            Block block = hit.getHitBlock();
            Double surfaceY = DisplayPropertyUtil.getTopCollisionY(block);
            if (surfaceY == null) {
                surfaceY = (double) (block.getY() + 1);
            }

            spawnLocation = new Location(
                    block.getWorld(),
                    block.getX() + 0.5D,
                    surfaceY, // entity origin sits exactly on the surface
                    block.getZ() + 0.5D
            );
        } else {
            Location targetLocation = RayTraceUtil.getTargetLocation(player, maxDistance);
            Block block = targetLocation.getBlock();
            Double surfaceY = DisplayPropertyUtil.getTopCollisionY(block);
            spawnLocation = new Location(
                    player.getWorld(),
                    block.getX() + 0.5D,
                    surfaceY != null ? surfaceY : block.getY() + 1.0D,
                    block.getZ() + 0.5D
            );
        }

        player.getWorld().spawn(spawnLocation, ItemDisplay.class, itemDisplay -> {
            itemDisplay.setItemStack(itemStack);
            itemDisplay.setDisplayHeight(0.5F);
            itemDisplay.setDisplayWidth(0.5F);
            itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);

            plugin.getDisplayStorage().addDisplay(itemDisplay);
        });

        player.sendMessage(Lang.getPrefixed("&aCreated new object: &eItem Display"));
    }
}
