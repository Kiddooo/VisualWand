package dev.kiddo.visualwand.gui;


import java.util.ArrayList;
import java.util.List;

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

public class BlockSelectGUI extends BaseGUI {

    private int page;
    private final List<Material> blocks;

    public BlockSelectGUI(VisualWand plugin, Player player) {
        super(plugin, player);
        this.page = 0;
        this.blocks = getSelectableBlocks();
    }

    private List<Material> getSelectableBlocks() {
        List<Material> selectable = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isBlock()
                    && material.isItem()
                    && !material.isAir()
                    && !material.name().contains("LEGACY_")
                    && !material.name().contains("COMMAND")
                    && !material.name().contains("BARRIER")
                    && !material.name().contains("STRUCTURE")) {
                selectable.add(material);
            }
        }
        return selectable;
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(
                this,
                54,
                Lang.colorize(plugin.getLang().get("gui-block-select-title"))
        );
        populateBlocks();
    }

    private void populateBlocks() {
        inventory.clear();

        int start = page * 45;
        int end = Math.min(start + 45, blocks.size());
        for (int index = start; index < end; index++) {
            inventory.setItem(index - start, new ItemStack(blocks.get(index)));
        }

        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "&a← Previous page"));
        }

        inventory.setItem(49, getCloseButton());

        if (end < blocks.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "&aNext page →"));
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
        Location targetLocation = RayTraceUtil.getTargetLocation(
                player,
                plugin.getConfig().getDouble("editor.max-distance", 50)
        );

        Location spawnLocation = targetLocation.getBlock().getLocation();
        BlockData blockData = material.createBlockData();

        player.getWorld().spawn(spawnLocation, BlockDisplay.class, blockDisplay -> {
            blockDisplay.setBlock(blockData);
            blockDisplay.setDisplayWidth(1.0F);
            blockDisplay.setDisplayHeight(1.0F);

            plugin.getDisplayStorage().addDisplay(blockDisplay);
        });
    }
}
