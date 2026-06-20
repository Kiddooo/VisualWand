package dev.kiddo.visualwand.gui;

import java.util.List;

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
        inventory = Bukkit.createInventory(
                this,
                45,
                Lang.colorize(plugin.getLang().get("gui-edit-properties"))
        );
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        addCommonProperties();

        if (display instanceof BlockDisplay blockDisplay) {
            addBlockDisplayProperties(blockDisplay);
        } else if (display instanceof ItemDisplay itemDisplay) {
            addItemDisplayProperties(itemDisplay);
        } else if (display instanceof TextDisplay textDisplay) {
            addTextDisplayProperties(textDisplay);
        }

        inventory.setItem(36, getBackButton());
        inventory.setItem(44, getCloseButton());
    }

    private void addCommonProperties() {
        Display.Billboard billboard = display.getBillboard();
        inventory.setItem(10, createItem(
                Material.PLAYER_HEAD,
                plugin.getLang().get("gui-prop-billboard", "value", billboard.name()),
                plugin.getLang().getColoredList("gui-prop-billboard-lore")
        ));

        inventory.setItem(11, createItem(
                Material.GLOWSTONE_DUST,
                plugin.getLang().get("gui-prop-glow"),
                plugin.getLang().getColoredList("gui-prop-glow-lore")
        ));

        inventory.setItem(12, createItem(
                Material.SPYGLASS,
                plugin.getLang().get("gui-prop-view-range", "value", display.getViewRange()),
                plugin.getLang().getColoredList("gui-prop-view-range-lore")
        ));

        inventory.setItem(13, createItem(
                Material.BLACK_CONCRETE,
                plugin.getLang().get("gui-prop-shadow", "value", display.getShadowRadius()),
                plugin.getLang().getColoredList("gui-prop-shadow-lore")
        ));

        String brightnessValue;
        if (display.getBrightness() == null) {
            brightnessValue = plugin.getLang().get("value-auto");
        } else {
            brightnessValue = display.getBrightness().getBlockLight()
                    + "/"
                    + display.getBrightness().getSkyLight();
        }

        List<String> brightnessLore = plugin.getLang().getColoredList("gui-prop-brightness-lore")
                .stream()
                .map(line -> line.replace("{value}", brightnessValue))
                .toList();

        inventory.setItem(14, createItem(
                Material.LANTERN,
                plugin.getLang().get("gui-prop-brightness"),
                brightnessLore
        ));
    }

    private void addBlockDisplayProperties(BlockDisplay blockDisplay) {
        List<String> lore = plugin.getLang().getColoredList("gui-prop-change-block-lore")
                .stream()
                .map(line -> line.replace("{value}", blockDisplay.getBlock().getMaterial().name()))
                .toList();

        inventory.setItem(20, createItem(
                Material.BRICKS,
                plugin.getLang().get("gui-prop-change-block"),
                lore
        ));
    }

    private void addItemDisplayProperties(ItemDisplay itemDisplay) {
        ItemStack itemStack = itemDisplay.getItemStack();
        Material material = itemStack != null ? itemStack.getType() : Material.STONE;

        List<String> itemLore = plugin.getLang().getColoredList("gui-prop-change-item-lore")
                .stream()
                .map(line -> line.replace("{value}", material.name()))
                .toList();

        inventory.setItem(20, createItem(
                material,
                plugin.getLang().get("gui-prop-change-item"),
                itemLore
        ));

        inventory.setItem(21, createItem(
                Material.ARMOR_STAND,
                plugin.getLang().get(
                        "gui-prop-transform",
                        "value",
                        itemDisplay.getItemDisplayTransform().name()
                ),
                plugin.getLang().getColoredList("gui-prop-transform-lore")
        ));

        int customModelData = 0;
        if (itemStack != null && itemStack.hasItemMeta()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                customModelData = meta.getCustomModelData();
            }
        }

        inventory.setItem(22, createItem(
                Material.COMMAND_BLOCK,
                plugin.getLang().get("gui-prop-cmd", "value", customModelData),
                plugin.getLang().getColoredList("gui-prop-cmd-lore")
        ));
    }

    private void addTextDisplayProperties(TextDisplay textDisplay) {
        inventory.setItem(20, createItem(
                Material.OAK_SIGN,
                plugin.getLang().get("gui-prop-change-text"),
                plugin.getLang().getColoredList("gui-prop-change-text-lore")
        ));

        inventory.setItem(21, createItem(
                Material.ORANGE_DYE,
                plugin.getLang().get("gui-prop-text-color"),
                plugin.getLang().getColoredList("gui-prop-text-color-lore")
        ));

        String background = textDisplay.getBackgroundColor() == null
                ? plugin.getLang().get("value-none")
                : textDisplay.getBackgroundColor().asRGB() + "";

        inventory.setItem(22, createItem(
                Material.BLACK_DYE,
                plugin.getLang().get("gui-prop-background", "value", background),
                plugin.getLang().getColoredList("gui-prop-background-lore")
        ));

        String seeThrough = textDisplay.isSeeThrough()
                ? plugin.getLang().get("value-yes")
                : plugin.getLang().get("value-no");

        inventory.setItem(23, createItem(
                Material.GLASS_PANE,
                plugin.getLang().get("gui-prop-see-through", "value", seeThrough),
                plugin.getLang().getColoredList("gui-prop-see-through-lore")
        ));

        inventory.setItem(24, createItem(
                Material.PAPER,
                plugin.getLang().get("gui-prop-line-width", "value", textDisplay.getLineWidth()),
                plugin.getLang().getColoredList("gui-prop-line-width-lore")
        ));

        inventory.setItem(25, createItem(
                Material.TINTED_GLASS,
                plugin.getLang().get("gui-prop-text-opacity"),
                plugin.getLang().getColoredList("gui-prop-text-opacity-lore")
        ));
    }

    @Override
    public void handleClick(int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 10 -> cycleBillboard();
            case 11 -> toggleGlow();
            case 12 -> adjustViewRange(clickType);
            case 13 -> adjustShadow(clickType);
            case 14 -> adjustBrightness(clickType);
            case 20 -> handleSlot20(clickType);
            case 21 -> handleSlot21(clickType);
            case 22 -> handleSlot22(clickType);
            case 23 -> handleSlot23(clickType);
            case 24 -> handleSlot24(clickType);
            case 25 -> handleSlot25(clickType);
            case 36 -> {
                player.closeInventory();
                new EditMenuGUI(plugin, player, display).open();
            }
            case 44 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void cycleBillboard() {
        Display.Billboard[] values = Display.Billboard.values();
        int next = (display.getBillboard().ordinal() + 1) % values.length;
        display.setBillboard(values[next]);
        open();
    }

    private void toggleGlow() {
        display.setGlowing(!display.isGlowing());
        open();
    }

    private void adjustViewRange(ClickType clickType) {
        if (clickType.isShiftClick()) {
            display.setViewRange(1.0F);
            open();
            return;
        }

        float current = display.getViewRange();
        float next = clickType.isRightClick() ? current - 0.5F : current + 0.5F;
        display.setViewRange(Math.max(0.1F, Math.min(10.0F, next)));
        open();
    }

    private void adjustShadow(ClickType clickType) {
        float current = display.getShadowRadius();
        float next = clickType.isRightClick() ? current - 0.1F : current + 0.1F;
        display.setShadowRadius(Math.max(0.0F, Math.min(5.0F, next)));
        open();
    }

    private void adjustBrightness(ClickType clickType) {
        if (clickType.isShiftClick()) {
            display.setBrightness(null);
            open();
            return;
        }

        Display.Brightness brightness = display.getBrightness();
        int block = brightness != null ? brightness.getBlockLight() : 7;
        int sky = brightness != null ? brightness.getSkyLight() : 7;
        int step = clickType.isRightClick() ? -1 : 1;

        block = Math.max(0, Math.min(15, block + step));
        sky = Math.max(0, Math.min(15, sky + step));
        display.setBrightness(new Display.Brightness(block, sky));
        open();
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
        } else if (display instanceof TextDisplay textDisplay) {
            player.closeInventory();
            plugin.getEditorManager().startTextInput(player, textDisplay);
        }
    }

    private void handleSlot21(ClickType clickType) {
        if (display instanceof ItemDisplay itemDisplay) {
            if (clickType.isShiftClick()) {
                itemDisplay.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                open();
                return;
            }

            ItemDisplay.ItemDisplayTransform[] values = ItemDisplay.ItemDisplayTransform.values();
            int next = (itemDisplay.getItemDisplayTransform().ordinal() + 1) % values.length;
            itemDisplay.setItemDisplayTransform(values[next]);
            open();
        } else if (display instanceof TextDisplay textDisplay) {
            player.closeInventory();
            new TextColorGUI(plugin, player, textDisplay).open();
        }
    }

    private void handleSlot22(ClickType clickType) {
        if (display instanceof ItemDisplay itemDisplay) {
            player.closeInventory();
            plugin.getEditorManager().startCMDInput(player, itemDisplay);
        } else if (display instanceof TextDisplay textDisplay) {
            boolean enabled = textDisplay.getBackgroundColor() != null
                    && textDisplay.getBackgroundColor().getAlpha() > 0;
            textDisplay.setBackgroundColor(enabled
                    ? Color.fromARGB(0, 0, 0, 0)
                    : Color.fromARGB(128, 0, 0, 0));
            open();
        }
    }

    private void handleSlot23(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
            textDisplay.setSeeThrough(!textDisplay.isSeeThrough());
            open();
        }
    }

    private void handleSlot24(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
            int step = clickType.isRightClick() ? -10 : 10;
            textDisplay.setLineWidth(Math.max(10, textDisplay.getLineWidth() + step));
            open();
        }
    }

    private void handleSlot25(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
            int step = clickType.isRightClick() ? -10 : 10;
            int opacity = Math.max(-128, Math.min(127, textDisplay.getTextOpacity() + step));
            textDisplay.setTextOpacity((byte) opacity);
            open();
        }
    }
}
