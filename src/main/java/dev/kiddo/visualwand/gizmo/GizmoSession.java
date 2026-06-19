package dev.kiddo.visualwand.gizmo;

import org.bukkit.entity.Display;
import org.bukkit.entity.Player;

public class GizmoSession {

    private final Player player;
    private final Display display;
    private GizmoMode mode;
    private GizmoAxis selectedAxis;

    public GizmoSession(Player player, Display display) {
        this.player = player;
        this.display = display;
        this.mode = GizmoMode.MOVE;
        this.selectedAxis = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Display getDisplay() {
        return display;
    }

    public GizmoMode getMode() {
        return mode;
    }

    public void setMode(GizmoMode mode) {
        this.mode = mode;
    }

    public GizmoAxis getSelectedAxis() {
        return selectedAxis;
    }

    public void setSelectedAxis(GizmoAxis axis) {
        this.selectedAxis = axis;
    }

    public void clearAxisSelection() {
        this.selectedAxis = null;
    }
}
