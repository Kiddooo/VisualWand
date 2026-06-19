package dev.kiddo.visualwand.gui;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.Lang;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PropertiesMenuGUI extends BaseGUI {

    private final Display display;

    public PropertiesMenuGUI(VisualWand plugin, Player player, Display display) {
        super(plugin, player);
        this.display = display;
    }

    @Override
    protected void createInventory() {
        inventory = Bukkit.createInventory(this, 45, Lang.colorize(plugin.getLang().get("gui-edit-properties")));
        
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Common properties
        addCommonProperties();
        
        // Type-specific properties
        if (display instanceof BlockDisplay blockDisplay) {
            addBlockDisplayProperties(blockDisplay);
        } else if (display instanceof ItemDisplay itemDisplay) {
            addItemDisplayProperties(itemDisplay);
        } else if (display instanceof TextDisplay textDisplay) {
            addTextDisplayProperties(textDisplay);
        }
        
        // Back button
        inventory.setItem(36, getBackButton());
        
        // Close button
        inventory.setItem(44, getCloseButton());
    }

    private void addCommonProperties() {
        // Billboard mode
        Display.Billboard currentBillboard = display.getBillboard();
        inventory.setItem(10, createItem(
            Material.PLAYER_HEAD,
            plugin.getLang().get("gui-prop-billboard", "value", currentBillboard.name()),
            plugin.getLang().getColoredList("gui-prop-billboard-lore")));
        
        // Glow color
        inventory.setItem(11, createItem(
            Material.GLOWSTONE_DUST,
            plugin.getLang().get("gui-prop-glow"),
            plugin.getLang().getColoredList("gui-prop-glow-lore")));
        
        // View range
        inventory.setItem(12, createItem(
            Material.SPYGLASS,
            plugin.getLang().get("gui-prop-view-range", "value", display.getViewRange()),
            plugin.getLang().getColoredList("gui-prop-view-range-lore")));
        
        // Shadow radius
        inventory.setItem(13, createItem(
            Material.BLACK_CONCRETE,
            plugin.getLang().get("gui-prop-shadow", "value", display.getShadowRadius()),
            plugin.getLang().getColoredList("gui-prop-shadow-lore")));
        
        // Brightness
        String brightnessValue = display.getBrightness() != null ? 
            "Block: " + display.getBrightness().getBlockLight() + 
            ", Sky: " + display.getBrightness().getSkyLight() : plugin.getLang().get("value-auto");
        inventory.setItem(14, createItem(
            Material.LANTERN,
            plugin.getLang().get("gui-prop-brightness"),
            plugin.getLang().getColoredList("gui-prop-brightness-lore").stream()
                .map(s -> s.replace("{value}", brightnessValue))
                .toList()));
    }

    private void addBlockDisplayProperties(BlockDisplay blockDisplay) {
        // Change block
        inventory.setItem(20, createItem(
            Material.BRICKS,
            plugin.getLang().get("gui-prop-change-block"),
            plugin.getLang().getColoredList("gui-prop-change-block-lore").stream()
                .map(s -> s.replace("{value}", blockDisplay.getBlock().getMaterial().name()))
                .toList()));
    }

    private void addItemDisplayProperties(ItemDisplay itemDisplay) {
        // Change item
        ItemStack currentItem = itemDisplay.getItemStack();
        Material displayMat = currentItem != null ? currentItem.getType() : Material.STONE;
        
        inventory.setItem(20, createItem(
            displayMat,
            plugin.getLang().get("gui-prop-change-item"),
            plugin.getLang().getColoredList("gui-prop-change-item-lore").stream()
                .map(s -> s.replace("{value}", displayMat.name()))
                .toList()));
        
        // Display transform
        inventory.setItem(21, createItem(
            Material.ARMOR_STAND,
            plugin.getLang().get("gui-prop-transform", "value", itemDisplay.getItemDisplayTransform().name()),
            plugin.getLang().getColoredList("gui-prop-transform-lore")));
        
        // Custom Model Data
        int cmd = 0;
        if (currentItem != null && currentItem.hasItemMeta()) {
            ItemMeta meta = currentItem.getItemMeta();
            if (meta.hasCustomModelData()) {
                cmd = meta.getCustomModelData();
            }
        }
        
        inventory.setItem(22, createItem(
            Material.COMMAND_BLOCK,
            plugin.getLang().get("gui-prop-cmd", "value", cmd),
            plugin.getLang().getColoredList("gui-prop-cmd-lore")));
    }

    private void addTextDisplayProperties(TextDisplay textDisplay) {
        // Change text
        inventory.setItem(20, createItem(
            Material.OAK_SIGN,
            plugin.getLang().get("gui-prop-change-text"),
            plugin.getLang().getColoredList("gui-prop-change-text-lore")));
        
        // Text color
        inventory.setItem(21, createItem(
            Material.ORANGE_DYE,
            plugin.getLang().get("gui-prop-text-color"),
            plugin.getLang().getColoredList("gui-prop-text-color-lore")));
        
        // Background toggle
        boolean hasBackground = textDisplay.getBackgroundColor() != null && 
            textDisplay.getBackgroundColor().getAlpha() > 0;
        String bgStatus = hasBackground ? plugin.getLang().get("value-enabled") : plugin.getLang().get("value-disabled");
        
        inventory.setItem(22, createItem(
            hasBackground ? Material.BLACK_STAINED_GLASS : Material.GLASS,
            plugin.getLang().get("gui-prop-background", "value", bgStatus),
            plugin.getLang().getColoredList("gui-prop-background-lore")));
        
        // See through
        String seeStatus = textDisplay.isSeeThrough() ? plugin.getLang().get("value-yes") : plugin.getLang().get("value-no");
        inventory.setItem(23, createItem(
            Material.GLASS_PANE,
            plugin.getLang().get("gui-prop-see-through", "value", seeStatus),
            plugin.getLang().getColoredList("gui-prop-see-through-lore")));
        
        // Line width
        inventory.setItem(24, createItem(
            Material.PAPER,
            plugin.getLang().get("gui-prop-line-width", "value", textDisplay.getLineWidth()),
            plugin.getLang().getColoredList("gui-prop-line-width-lore")));
        
        // Text opacity
        inventory.setItem(25, createItem(
            Material.TINTED_GLASS,
            plugin.getLang().get("gui-prop-text-opacity"),
            plugin.getLang().getColoredList("gui-prop-text-opacity-lore")));
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            // Billboard
            case 10 -> cycleBillboard();
            
            // Glow
            case 11 -> toggleGlow();
            
            // View range
            case 12 -> adjustViewRange(clickType);
            
            // Shadow
            case 13 -> adjustShadow(clickType);
            
            // Brightness
            case 14 -> adjustBrightness(clickType);
            
            // Type-specific
            case 20 -> handleSlot20(clickType);
            case 21 -> handleSlot21(clickType);
            case 22 -> handleSlot22(clickType);
            case 23 -> handleSlot23(clickType);
            case 24 -> handleSlot24(clickType);
            case 25 -> handleSlot25(clickType);
            
            // Navigation
            case 36 -> {
                player.closeInventory();
                new EditMenuGUI(plugin, player, display).open();
            }
            case 44 -> player.closeInventory();
        }
    }

    private void cycleBillboard() {
        Display.Billboard[] values = Display.Billboard.values();
        int currentIndex = display.getBillboard().ordinal();
        int nextIndex = (currentIndex + 1) % values.length;
        display.setBillboard(values[nextIndex]);
        createInventory();
    }

    private void toggleGlow() {
        display.setGlowing(!display.isGlowing());
        createInventory();
    }

    private void adjustViewRange(ClickType clickType) {
        float current = display.getViewRange();
        float newValue = clickType.isRightClick() ? current - 0.5f : current + 0.5f;
        display.setViewRange(Math.max(0.1f, Math.min(newValue, 10f)));
        createInventory();
    }

    private void adjustShadow(ClickType clickType) {
        float current = display.getShadowRadius();
        float newValue = clickType.isRightClick() ? current - 0.1f : current + 0.1f;
        display.setShadowRadius(Math.max(0f, Math.min(newValue, 5f)));
        createInventory();
    }

    private void adjustBrightness(ClickType clickType) {
        if (clickType.isShiftClick()) {
            display.setBrightness(null);
        } else {
            Display.Brightness current = display.getBrightness();
            int blockLight = current != null ? current.getBlockLight() : 7;
            int skyLight = current != null ? current.getSkyLight() : 7;
            
            int change = clickType.isRightClick() ? -1 : 1;
            blockLight = Math.max(0, Math.min(15, blockLight + change));
            skyLight = Math.max(0, Math.min(15, skyLight + change));
            
            display.setBrightness(new Display.Brightness(blockLight, skyLight));
        }
        createInventory();
    }

    private void handleSlot20(ClickType clickType) {
        if (display instanceof BlockDisplay) {
            player.closeInventory();
            new BlockSelectGUI(plugin, player) {
                @Override
                public void handleClick(int slot, ItemStack item, ClickType clickType) {
                    if (item != null && item.getType().isBlock()) {
                        ((BlockDisplay) display).setBlock(item.getType().createBlockData());
                        player.closeInventory();
                        new PropertiesMenuGUI(plugin, player, display).open();
                    } else {
                        super.handleClick(slot, item, clickType);
                    }
                }
            }.open();
        } else if (display instanceof ItemDisplay) {
            player.closeInventory();
            new ItemSelectGUI(plugin, player) {
                @Override
                public void handleClick(int slot, ItemStack item, ClickType clickType) {
                    if (item != null && item.getType().isItem() && slot < 45) {
                        ((ItemDisplay) display).setItemStack(item.clone());
                        player.closeInventory();
                        new PropertiesMenuGUI(plugin, player, display).open();
                    } else {
                        super.handleClick(slot, item, clickType);
                    }
                }
            }.open();
        } else if (display instanceof TextDisplay) {
            player.closeInventory();
            plugin.getEditorManager().startTextInput(player, (TextDisplay) display);
        }
    }

    private void handleSlot21(ClickType clickType) {
        if (display instanceof ItemDisplay itemDisplay) {
            ItemDisplay.ItemDisplayTransform[] values = ItemDisplay.ItemDisplayTransform.values();
            int currentIndex = itemDisplay.getItemDisplayTransform().ordinal();
            int nextIndex = (currentIndex + 1) % values.length;
            itemDisplay.setItemDisplayTransform(values[nextIndex]);
            createInventory();
        } else if (display instanceof TextDisplay textDisplay) {
            // Open text color GUI
            player.closeInventory();
            new TextColorGUI(plugin, player, textDisplay).open();
        }
    }

    private void handleSlot22(ClickType clickType) {
        if (display instanceof ItemDisplay) {
            player.closeInventory();
            plugin.getEditorManager().startCMDInput(player, (ItemDisplay) display);
        } else if (display instanceof TextDisplay textDisplay) {
            // Toggle background
            boolean hasBackground = textDisplay.getBackgroundColor() != null && 
                textDisplay.getBackgroundColor().getAlpha() > 0;
            
            if (hasBackground) {
                textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            } else {
                textDisplay.setBackgroundColor(Color.fromARGB(128, 0, 0, 0));
            }
            createInventory();
        }
    }

    private void handleSlot23(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
            // See through toggle
            textDisplay.setSeeThrough(!textDisplay.isSeeThrough());
            createInventory();
        }
    }

    private void handleSlot24(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
            int current = textDisplay.getLineWidth();
            int change = clickType.isRightClick() ? -10 : 10;
            textDisplay.setLineWidth(Math.max(10, current + change));
            createInventory();
        }
    }

    private void handleSlot25(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
            byte current = textDisplay.getTextOpacity();
            int change = clickType.isRightClick() ? -10 : 10;
            textDisplay.setTextOpacity((byte) Math.max(-128, Math.min(127, current + change)));
            createInventory();
        }
    }
}
