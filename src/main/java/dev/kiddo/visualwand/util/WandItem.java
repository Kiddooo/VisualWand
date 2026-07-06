package dev.kiddo.visualwand.util;

import dev.kiddo.visualwand.VisualWand;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class WandItem {

    private final VisualWand plugin;
    private final NamespacedKey wandKey;
    private ItemStack wandItemStack;

    public WandItem(VisualWand plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "architect_wand");
        createWandItem();
    }

    public void reload() {
        createWandItem();
    }

    private void createWandItem() {
        String materialName = plugin.getConfig().getString("wand.material", "BLAZE_ROD");
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.BLAZE_ROD;
        }

        wandItemStack = new ItemStack(material);
        ItemMeta meta = wandItemStack.getItemMeta();

        if (meta != null) {
            // Set display name from language file
            meta.setDisplayName(Lang.colorize("&6✦ &eArchitect's Wand &6✦"));

            // Set lore from language file
            List<String> lore = Lang.colorizeList(List.of("&7", "&fRMB in air: &eCreate objects", "&fRMB on object: &eEdit", "&fShift + RMB: &cDelete", "&7"));
            meta.setLore(lore);

            // Add enchant glow
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            // Set persistent data to identify wand
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);

            wandItemStack.setItemMeta(meta);
        }
    }

    public ItemStack getWandItem() {
        return wandItemStack.clone();
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }
}
