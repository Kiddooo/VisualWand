package dev.kiddo.visualwand;

import dev.kiddo.visualwand.animation.AnimationManager;

import dev.kiddo.visualwand.command.VisualWandCommand;
import dev.kiddo.visualwand.command.WandGiveCommand;
import dev.kiddo.visualwand.editor.EditorManager;
import dev.kiddo.visualwand.gizmo.GizmoManager;
import dev.kiddo.visualwand.listener.DisplayInteractListener;
import dev.kiddo.visualwand.listener.GUIListener;
import dev.kiddo.visualwand.listener.WandListener;
import dev.kiddo.visualwand.util.WandItem;
import org.bukkit.plugin.java.JavaPlugin;

public class VisualWand extends JavaPlugin {

    private static VisualWand instance;
    
    private WandItem wandItem;
    private EditorManager editorManager;
    private GizmoManager gizmoManager;
    private AnimationManager animationManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        wandItem = new WandItem(this);
        editorManager = new EditorManager(this);
        gizmoManager = new GizmoManager(this);
        animationManager = new AnimationManager(this);

        getCommand("visualwand").setExecutor(new VisualWandCommand(this));
        getCommand("vwgive").setExecutor(new WandGiveCommand(this));

        getServer().getPluginManager().registerEvents(new WandListener(this), this);
        getServer().getPluginManager().registerEvents(new DisplayInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        animationManager.startAnimationTask();
        gizmoManager.startRenderTask();

        getLogger().info("VisualWand has been enabled!");
    }

    @Override
    public void onDisable() {
        if (animationManager != null) {
            animationManager.stopAllAnimations();
        }
        if (gizmoManager != null) {
            gizmoManager.stopAllGizmos();
        }
        getLogger().info("VisualWand has been disabled!");
    }

    public static VisualWand getInstance() {
        return instance;
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

    public void reload() {
        reloadConfig();
        wandItem.reload();
    }
}