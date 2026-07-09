package dev.kiddo.visualwand.gui;

import java.util.List;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.util.DisplayPropertyUtil;
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
                Lang.colorize("&8✦ &6Edit Properties")
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
                "&eBillboard: &f" + billboard.name(),
                Lang.colorizeList(List.of("&7", "&fDetermines how object rotates towards player.", "&7FIXED - Does not rotate", "&7VERTICAL - Rotates vertically", "&7HORIZONTAL - Rotates horizontally", "&7CENTER - Always faces the player", "&7", "&eClick to change!"))
        ));

        inventory.setItem(11, createItem(
                Material.GLOWSTONE_DUST,
                "&eGlow",
                Lang.colorizeList(List.of("&7", "&fToggle the object's glow effect.", "&7", "&eClick to toggle!"))
        ));

        inventory.setItem(12, createItem(
                Material.SPYGLASS,
                "&eView Range: &f" + DisplayPropertyUtil.formatViewRange(display.getViewRange()),
                Lang.colorizeList(List.of("&7", "&fHow far the object is visible.", "&7Value is a multiplier; 1 is roughly 64 blocks.", "&7", "&eLMB: +1 | RMB: -1 | Shift+LMB: +0.1", "&eShift+RMB: -0.1 | Middle: Reset")))
        );

        inventory.setItem(13, createItem(
                Material.BLACK_CONCRETE,
                "&eShadow: &f" + DisplayPropertyUtil.formatNumber(display.getShadowRadius()),
                Lang.colorizeList(List.of("&7", "&fShadow radius under the object.", "&7Values are rounded to clean 0.1 increments.", "&7", "&eLMB: +1 | RMB: -1 | Shift+LMB: +0.1", "&eShift+RMB: -0.1 | Middle: Reset")))
        );

        String brightnessValue;
        if (display.getBrightness() == null) {
            brightnessValue = "Auto";
        } else {
            brightnessValue = display.getBrightness().getBlockLight() + "/15";
        }

        List<String> brightnessLore = Lang.colorizeList(List.of(
                "&7", "&fOverrides the light level on this display.",
                "&7Range: 0 (dark) to 15 (full bright).",
                "&7", "&7Current: " + brightnessValue,
                "&7", "&eLMB: +1 | RMB: -1 | Middle: Auto"
        ));

        inventory.setItem(14, createItem(
                Material.LANTERN,
                "&eBrightness: &f" + brightnessValue,
                brightnessLore
        ));
        if (display instanceof BlockDisplay || display instanceof ItemDisplay) {
            inventory.setItem(15, createItem(
                    Material.MOSS_CARPET,
                    "&aPlace on Surface",
                    Lang.colorizeList(List.of("&7", "&fMoves this display vertically onto the", "&ffirst collision surface below it.", "&7Rotation and item pose are unchanged.", "&7", "&eLMB: &fkeep horizontal position", "&eRMB: &fcentre on supporting block"))
            ));
        }
    }

    private void addBlockDisplayProperties(BlockDisplay blockDisplay) {
        List<String> lore = Lang.colorizeList(List.of("&7", "&fCurrent: &e" + blockDisplay.getBlock().getMaterial().name(), "&7", "&eClick to change!"));

        inventory.setItem(20, createItem(
                Material.BRICKS,
                "&eChange Block",
                lore
        ));
    }

    private void addItemDisplayProperties(ItemDisplay itemDisplay) {
        ItemStack itemStack = itemDisplay.getItemStack();
        Material material = itemStack != null ? itemStack.getType() : Material.STONE;

        List<String> itemLore = Lang.colorizeList(List.of("&7", "&fCurrent: &e" + material.name(), "&7", "&eClick to change!"));

        inventory.setItem(20, createItem(
                material,
                "&eChange Item",
                itemLore
        ));

        inventory.setItem(21, createItem(
                Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                "&eItem Preset: &f" + itemDisplay.getItemDisplayTransform().name(),
                Lang.colorizeList(List.of("&7", "&fPreset item display orientation.", "&7Applies the preset and places the item", "&7onto the surface below.", "&7", "&eLMB: &flay flat and place on surface", "&eRMB: &fstand upright"))
        ));

    }

    private void addTextDisplayProperties(TextDisplay textDisplay) {
        inventory.setItem(20, createItem(
                Material.OAK_SIGN,
                "&eChange Text",
                Lang.colorizeList(List.of("&7", "&fClick and type new text in chat.", "&7", "&eClick to change!"))
        ));

        inventory.setItem(21, createItem(
                Material.ORANGE_DYE,
                "&eText Color",
                Lang.colorizeList(List.of("&7", "&fChange text color and formatting.", "&7", "&eClick to open!"))
        ));

        String background = textDisplay.getBackgroundColor() == null
                ? "None"
                : textDisplay.getBackgroundColor().asRGB() + "";

        inventory.setItem(22, createItem(
                Material.BLACK_DYE,
                "&eBackground: &f" + background,
                Lang.colorizeList(List.of("&7", "&fToggle text background.", "&7", "&eClick to toggle!"))
        ));

        String seeThrough = textDisplay.isSeeThrough()
                ? "Yes"
                : "No";

        inventory.setItem(23, createItem(
                Material.GLASS_PANE,
                "&eSee Through: &f" + seeThrough,
                Lang.colorizeList(List.of("&7", "&fWhether text is visible through blocks.", "&7", "&eClick to toggle!"))
        ));

        inventory.setItem(24, createItem(
                Material.PAPER,
                "&eLine Width: &f" + textDisplay.getLineWidth(),
                Lang.colorizeList(List.of("&7", "&fMaximum line width of text.", "&7", "&eLMB: +10 | RMB: -10"))
        ));

        inventory.setItem(25, createItem(
                Material.TINTED_GLASS,
                "&eText Opacity",
                Lang.colorizeList(List.of("&7", "&fSet text opacity.", "&7", "&eLMB: +10 | RMB: -10"))
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
            case 15 -> handlePlaceOnSurface(clickType);
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
        if (clickType == ClickType.MIDDLE) {
            display.setViewRange(1.0F);
            open();
            return;
        }

        float current = display.getViewRange();
        float next;
        if (clickType == ClickType.SHIFT_LEFT) {
            next = current + 0.1F;
        } else if (clickType == ClickType.SHIFT_RIGHT) {
            next = current - 0.1F;
        } else if (clickType == ClickType.RIGHT) {
            next = current - 1.0F;
        } else {
            next = current + 1.0F;
        }
        display.setViewRange(Math.clamp(next, 0.1F, 10.0F));
        open();
    }

    private void adjustShadow(ClickType clickType) {
        if (clickType == ClickType.MIDDLE) {
            display.setShadowRadius(0.0F);
            open();
            return;
        }

        float current = display.getShadowRadius();
        float next;
        if (clickType == ClickType.SHIFT_LEFT) {
            next = current + 0.1F;
        } else if (clickType == ClickType.SHIFT_RIGHT) {
            next = current - 0.1F;
        } else if (clickType == ClickType.RIGHT) {
            next = current - 1.0F;
        } else {
            next = current + 1.0F;
        }
        display.setShadowRadius(DisplayPropertyUtil.roundTenths(Math.clamp(next, 0.0F, 5.0F)));
        open();
    }

    private void adjustBrightness(ClickType clickType) {
        if (clickType == ClickType.MIDDLE) {
            display.setBrightness(null);
            open();
            return;
        }

        int level = display.getBrightness() != null ? display.getBrightness().getBlockLight() : 7;
        int step = clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT ? -1 : 1;
        level = Math.clamp(level + step, 0, 15);
        display.setBrightness(new Display.Brightness(level, level));
        open();
    }

    private void handlePlaceOnSurface(ClickType clickType) {
        if (display instanceof BlockDisplay || display instanceof ItemDisplay) {
            DisplayPropertyUtil.placeOnSurface(display, player, clickType);
            open();
        }
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
            DisplayPropertyUtil.applyItemPreset(itemDisplay, player, clickType);
            open();
        } else if (display instanceof TextDisplay textDisplay) {
            player.closeInventory();
            new TextColorGUI(plugin, player, textDisplay).open();
        }
    }

    private void handleSlot22(ClickType clickType) {
        if (display instanceof TextDisplay textDisplay) {
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
            int opacity = Math.clamp(textDisplay.getTextOpacity() + step, -128, 127);
            textDisplay.setTextOpacity((byte) opacity);
            open();
        }
    }
}
