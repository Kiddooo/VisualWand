package dev.kiddo.visualwand.storage;

import dev.kiddo.visualwand.VisualWand;
import dev.kiddo.visualwand.animation.AnimationType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DisplayStorage {

    private final VisualWand plugin;
    private final File storageFile;
    private FileConfiguration storage;
    private final Set<UUID> trackedDisplays = new HashSet<>();

    public DisplayStorage(VisualWand plugin) {
        this.plugin = plugin;
        this.storageFile = new File(plugin.getDataFolder(), "displays.yml");
        loadStorageFile();
        startAutoSave();
    }

    private void loadStorageFile() {
        if (!storageFile.exists()) {
            try {
                storageFile.getParentFile().mkdirs();
                storageFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create displays.yml: " + e.getMessage());
            }
        }
        storage = YamlConfiguration.loadConfiguration(storageFile);
    }

    private void startAutoSave() {
        int interval = plugin.getConfig().getInt("storage.auto-save-interval", 5);
        if (interval <= 0) return;

        new BukkitRunnable() {
            @Override
            public void run() {
                saveDisplays();
            }
        }.runTaskTimerAsynchronously(plugin, interval * 60 * 20L, interval * 60 * 20L);
    }

    public void addDisplay(Display display) {
        trackedDisplays.add(display.getUniqueId());
    }

    public void removeDisplay(Display display) {
        trackedDisplays.remove(display.getUniqueId());
        storage.set("displays." + display.getUniqueId(), null);
    }

    public void saveDisplays() {
        plugin.getLogger().info("Saving display entities...");
        
        // Clear old data
        storage.set("displays", null);
        
        int saved = 0;
        for (World world : Bukkit.getWorlds()) {
            for (BlockDisplay display : world.getEntitiesByClass(BlockDisplay.class)) {
                if (trackedDisplays.contains(display.getUniqueId())) {
                    saveBlockDisplay(display);
                    saved++;
                }
            }
            for (ItemDisplay display : world.getEntitiesByClass(ItemDisplay.class)) {
                if (trackedDisplays.contains(display.getUniqueId())) {
                    saveItemDisplay(display);
                    saved++;
                }
            }
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (trackedDisplays.contains(display.getUniqueId())) {
                    saveTextDisplay(display);
                    saved++;
                }
            }
        }

        try {
            storage.save(storageFile);
            plugin.getLogger().info("Saved " + saved + " display entities.");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save displays.yml: " + e.getMessage());
        }
    }

    public void loadDisplays() {
        plugin.getLogger().info("Loading display entities...");
        
        ConfigurationSection displaysSection = storage.getConfigurationSection("displays");
        if (displaysSection == null) {
            plugin.getLogger().info("No saved displays found.");
            return;
        }

        int loaded = 0;
        for (String uuidStr : displaysSection.getKeys(false)) {
            ConfigurationSection displaySection = displaysSection.getConfigurationSection(uuidStr);
            if (displaySection == null) continue;

            String type = displaySection.getString("type");
            if (type == null) continue;

            Display display = switch (type) {
                case "BLOCK" -> loadBlockDisplay(displaySection);
                case "ITEM" -> loadItemDisplay(displaySection);
                case "TEXT" -> loadTextDisplay(displaySection);
                default -> null;
            };

            if (display != null) {
                trackedDisplays.add(display.getUniqueId());
                
                // Restore animation if any
                String animationType = displaySection.getString("animation");
                if (animationType != null) {
                    try {
                        AnimationType anim = AnimationType.valueOf(animationType);
                        plugin.getAnimationManager().startAnimation(display, anim);
                    } catch (IllegalArgumentException ignored) {}
                }
                
                loaded++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " display entities.");
    }

    private void saveBlockDisplay(BlockDisplay display) {
        String path = "displays." + display.getUniqueId();
        
        storage.set(path + ".type", "BLOCK");
        saveCommonData(display, path);
        storage.set(path + ".block", display.getBlock().getAsString());
    }

    private void saveItemDisplay(ItemDisplay display) {
        String path = "displays." + display.getUniqueId();
        
        storage.set(path + ".type", "ITEM");
        saveCommonData(display, path);
        
        ItemStack item = display.getItemStack();
        if (item != null) {
            storage.set(path + ".item.material", item.getType().name());
            if (item.hasItemMeta() && item.getItemMeta().hasCustomModelData()) {
                storage.set(path + ".item.custom-model-data", item.getItemMeta().getCustomModelData());
            }
        }
        storage.set(path + ".item-transform", display.getItemDisplayTransform().name());
    }

    private void saveTextDisplay(TextDisplay display) {
        String path = "displays." + display.getUniqueId();
        
        storage.set(path + ".type", "TEXT");
        saveCommonData(display, path);
        
        Component text = display.text();
        if (text != null) {
            storage.set(path + ".text", GsonComponentSerializer.gson().serialize(text));
        }
        storage.set(path + ".line-width", display.getLineWidth());
        storage.set(path + ".see-through", display.isSeeThrough());
        
        if (display.getBackgroundColor() != null) {
            storage.set(path + ".background-color", display.getBackgroundColor().asARGB());
        }
    }

    private void saveCommonData(Display display, String path) {
        Location loc = display.getLocation();
        storage.set(path + ".world", loc.getWorld().getName());
        storage.set(path + ".x", loc.getX());
        storage.set(path + ".y", loc.getY());
        storage.set(path + ".z", loc.getZ());
        storage.set(path + ".yaw", loc.getYaw());
        storage.set(path + ".pitch", loc.getPitch());

        Transformation t = display.getTransformation();
        storage.set(path + ".transformation.translation", vectorToString(t.getTranslation()));
        storage.set(path + ".transformation.left-rotation", quaternionToString(t.getLeftRotation()));
        storage.set(path + ".transformation.scale", vectorToString(t.getScale()));
        storage.set(path + ".transformation.right-rotation", quaternionToString(t.getRightRotation()));

        storage.set(path + ".billboard", display.getBillboard().name());
        storage.set(path + ".view-range", display.getViewRange());
        storage.set(path + ".shadow-radius", display.getShadowRadius());
        storage.set(path + ".glow", display.isGlowing());

        // Save animation state
        if (plugin.getAnimationManager().hasAnimation(display)) {
            AnimationType animType = plugin.getAnimationManager().getAnimationType(display);
            if (animType != null) {
                storage.set(path + ".animation", animType.name());
            }
        }
    }

    private BlockDisplay loadBlockDisplay(ConfigurationSection section) {
        Location loc = loadLocation(section);
        if (loc == null) return null;

        String blockDataStr = section.getString("block");
        if (blockDataStr == null) return null;

        BlockData blockData;
        try {
            blockData = Bukkit.createBlockData(blockDataStr);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid block data: " + blockDataStr);
            return null;
        }

        return loc.getWorld().spawn(loc, BlockDisplay.class, display -> {
            display.setBlock(blockData);
            loadCommonData(display, section);
        });
    }

    private ItemDisplay loadItemDisplay(ConfigurationSection section) {
        Location loc = loadLocation(section);
        if (loc == null) return null;

        String materialName = section.getString("item.material");
        if (materialName == null) return null;

        Material material = Material.matchMaterial(materialName);
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        int cmd = section.getInt("item.custom-model-data", 0);
        if (cmd > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(cmd);
                item.setItemMeta(meta);
            }
        }

        String transformStr = section.getString("item-transform", "GROUND");
        ItemDisplay.ItemDisplayTransform transform;
        try {
            transform = ItemDisplay.ItemDisplayTransform.valueOf(transformStr);
        } catch (Exception e) {
            transform = ItemDisplay.ItemDisplayTransform.GROUND;
        }

        ItemDisplay.ItemDisplayTransform finalTransform = transform;
        return loc.getWorld().spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(finalTransform);
            loadCommonData(display, section);
        });
    }

    private TextDisplay loadTextDisplay(ConfigurationSection section) {
        Location loc = loadLocation(section);
        if (loc == null) return null;

        String textJson = section.getString("text");
        Component text = Component.text("Text");
        if (textJson != null) {
            try {
                text = GsonComponentSerializer.gson().deserialize(textJson);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid text component: " + textJson);
            }
        }

        Component finalText = text;
        return loc.getWorld().spawn(loc, TextDisplay.class, display -> {
            display.text(finalText);
            display.setLineWidth(section.getInt("line-width", 200));
            display.setSeeThrough(section.getBoolean("see-through", false));
            
            int bgColor = section.getInt("background-color", 0x40000000);
            display.setBackgroundColor(org.bukkit.Color.fromARGB(bgColor));
            
            loadCommonData(display, section);
        });
    }

    private Location loadLocation(ConfigurationSection section) {
        String worldName = section.getString("world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World not found: " + worldName);
            return null;
        }

        return new Location(
            world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw"),
            (float) section.getDouble("pitch")
        );
    }

    private void loadCommonData(Display display, ConfigurationSection section) {
        // Load transformation
        ConfigurationSection transSection = section.getConfigurationSection("transformation");
        if (transSection != null) {
            Vector3f translation = stringToVector(transSection.getString("translation", "0,0,0"));
            Quaternionf leftRotation = stringToQuaternion(transSection.getString("left-rotation", "0,0,0,1"));
            Vector3f scale = stringToVector(transSection.getString("scale", "1,1,1"));
            Quaternionf rightRotation = stringToQuaternion(transSection.getString("right-rotation", "0,0,0,1"));

            display.setTransformation(new Transformation(translation, leftRotation, scale, rightRotation));
        }

        // Load other properties
        String billboardStr = section.getString("billboard", "FIXED");
        try {
            display.setBillboard(Display.Billboard.valueOf(billboardStr));
        } catch (Exception ignored) {}

        display.setViewRange((float) section.getDouble("view-range", 1.0));
        display.setShadowRadius((float) section.getDouble("shadow-radius", 0));
        display.setGlowing(section.getBoolean("glow", false));
    }

    private String vectorToString(Vector3f v) {
        return v.x + "," + v.y + "," + v.z;
    }

    private String quaternionToString(Quaternionf q) {
        return q.x + "," + q.y + "," + q.z + "," + q.w;
    }

    private Vector3f stringToVector(String s) {
        String[] parts = s.split(",");
        if (parts.length < 3) return new Vector3f(0, 0, 0);
        return new Vector3f(
            Float.parseFloat(parts[0]),
            Float.parseFloat(parts[1]),
            Float.parseFloat(parts[2])
        );
    }

    private Quaternionf stringToQuaternion(String s) {
        String[] parts = s.split(",");
        if (parts.length < 4) return new Quaternionf(0, 0, 0, 1);
        return new Quaternionf(
            Float.parseFloat(parts[0]),
            Float.parseFloat(parts[1]),
            Float.parseFloat(parts[2]),
            Float.parseFloat(parts[3])
        );
    }
}
