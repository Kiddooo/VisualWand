package dev.kiddo.visualwand;

import dev.kiddo.visualwand.animation.AnimationManager;
import dev.kiddo.visualwand.command.VisualWandCommand;
import dev.kiddo.visualwand.command.WandGiveCommand;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.gizmo.GizmoManager;
import dev.kiddo.visualwand.listener.DisplayInteractListener;
import dev.kiddo.visualwand.listener.GUIListener;
import dev.kiddo.visualwand.listener.WandListener;
import dev.kiddo.visualwand.storage.DisplayStorage;
import dev.kiddo.visualwand.util.Lang;
import dev.kiddo.visualwand.util.WandItem;
import org.bukkit.plugin.java.JavaPlugin;

public class VisualWand extends JavaPlugin {

    private static VisualWand instance;
    
    private Lang lang;
    private WandItem wandItem;
    private EditorManager editorManager;
    private GizmoManager gizmoManager;
    private AnimationManager animationManager;
    private DisplayStorage displayStorage;

    @Override
    public void onEnable() {
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize language
        lang = new Lang(this);
        
        // Initialize managers
        wandItem = new WandItem(this);
        editorManager = new EditorManager(this);
        gizmoManager = new GizmoManager(this);
        animationManager = new AnimationManager(this);
        displayStorage = new DisplayStorage(this);
        
        // Register commands
        getCommand("visualwand").setExecutor(new VisualWandCommand(this));
        getCommand("vwgive").setExecutor(new WandGiveCommand(this));
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new DisplayInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        
        // Load saved displays
        displayStorage.loadDisplays();
        
        // Start animation task
        animationManager.startAnimationTask();
        
        // Start gizmo render task
        gizmoManager.startRenderTask();
        
        getLogger().info("VisualWand has been enabled!");
        getLogger().info("Display Entity Editor ready for use!");
    }

    @Override
    public void onDisable() {
        // Stop animations
        if (animationManager != null) {
            animationManager.stopAllAnimations();
        }
        
        // Stop gizmos
        if (gizmoManager != null) {
            gizmoManager.stopAllGizmos();
        }
        
        // Save displays
        if (displayStorage != null && getConfig().getBoolean("storage.save-on-stop", true)) {
            displayStorage.saveDisplays();
        }
        
        getLogger().info("VisualWand has been disabled!");
    }

    public static VisualWand getInstance() {
        return instance;
    }

    public Lang getLang() {
        return lang;
    }

    public WandItem getWandItem() {
        return wandItem;
    }

    public EditorManager getEditorManager() {
        return editorManager;
    }

    public GizmoManager getGizmoManager() {
        return gizmoManager;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public DisplayStorage getDisplayStorage() {
        return displayStorage;
    }

    public void reload() {
        reloadConfig();
        lang.reload();
        wandItem.reload();
        updatePlayersWands();
    }

    /**
     * Updates wand items in all online players' inventories after language change
     */
    public void updatePlayersWands() {
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                org.bukkit.inventory.ItemStack item = inv.getItem(i);
                if (wandItem.isWand(item)) {
                    inv.setItem(i, wandItem.getWandItem());
                }
            }
        }
    }
}
