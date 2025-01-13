package ict.minesunshineone.peek.data;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;

public class PeekData {

    private final Location originalLocation;
    private final GameMode originalGameMode;
    private final UUID targetUUID;
    private final long startTime;
    private boolean exiting;

    public PeekData(Location originalLocation, GameMode originalGameMode, UUID targetUUID, long startTime) {
        this.originalLocation = originalLocation;
        this.originalGameMode = originalGameMode;
        this.targetUUID = targetUUID;
        this.startTime = startTime;
        this.exiting = false;
    }

    public Location getOriginalLocation() {
        return originalLocation;
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isExiting() {
        return exiting;
    }

    public void setExiting(boolean exiting) {
        this.exiting = exiting;
    }
}
