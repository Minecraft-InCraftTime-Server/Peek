package ict.minesunshineone.peek.data;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PeekData {

    private final Location originalLocation;
    private final GameMode originalGameMode;
    private final Player targetPlayer;
    private final long startTime;
    private boolean exiting = false;

    public PeekData(Location originalLocation, GameMode originalGameMode, Player targetPlayer, long startTime) {
        this.originalLocation = originalLocation;
        this.originalGameMode = originalGameMode;
        this.targetPlayer = targetPlayer;
        this.startTime = startTime;
    }

    public Location getOriginalLocation() {
        return originalLocation;
    }

    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    public Player getTargetPlayer() {
        return targetPlayer;
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
