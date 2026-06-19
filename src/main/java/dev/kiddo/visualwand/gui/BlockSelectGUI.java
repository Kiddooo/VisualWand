package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import dev.kiddo.visualwand.util.RayTraceUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BlockSelectGUI extends BaseGUI {

    private int page = 0;
    private final List<Material> blocks;

    public BlockSelectGUI(VisualWand plugin, Player player) {
        super(plugin, player);
        this.blocks = getSelectableBlocks();
    }

    private List<Material> getSelectableBlocks() {
        List<Material> selectableBlocks = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isBlock() && material.isItem() && !material.isAir() 
                && !material.name().contains("LEGACY_")
                && !material.name().contains("COMMAND")
                && !material.name().contains("BARRIER")
                && !material.name().contains("STRUCTURE")) {
                selectableBlocks.add(material);
            }
        }
        return selectableBlocks;
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 54, Lang.colorize(plugin.getLang().get("gui-block-select-title")));
        populateBlocks();
    }

    private void populateBlocks() {
        inventory.clear();
        
        int startIndex = page * 45;
        int endIndex = Math.min(startIndex + 45, blocks.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            Material material = blocks.get(i);
            inventory.setItem(i - startIndex, new ItemStack(material));
        }
        
        // Navigation buttons
        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "&a« Poprzednia strona"));
        }
        
        inventory.setItem(49, getCloseButton());
        
        if (endIndex < blocks.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "&aNastępna strona »"));
        }
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        if (slot == 45 && page > 0) {
            page--;
            populateBlocks();
            return;
        }
        
        if (slot == 53 && (page + 1) * 45 < blocks.size()) {
            page++;
            populateBlocks();
            return;
        }
        
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        
        if (item != null && item.getType().isBlock()) {
            createBlockDisplay(item.getType());
            player.closeInventory();
        }
    }

    private void createBlockDisplay(Material material) {
        Location location = RayTraceUtil.getTargetLocation(player,
            plugin.getConfig().getDouble("editor.max-distance", 50));
        
        BlockData blockData = material.createBlockData();
        
        player.getWorld().spawn(location, BlockDisplay.class, blockDisplay -> {
            blockDisplay.setBlock(blockData);
            
            // Register in storage
            plugin.getDisplayStorage().addDisplay(blockDisplay);
        });
        
        player.sendMessage(plugin.getLang().getPrefixed("display-created", "type", "Block Display"));
    }
}
