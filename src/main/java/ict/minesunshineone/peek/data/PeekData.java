package ict.minesunshineone.peek.data;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
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

    public static PeekData fromConfig(YamlConfiguration config) {
        Location location = (Location) config.get("location");
        GameMode gameMode = GameMode.valueOf(config.getString("gamemode"));
        UUID targetUUID = UUID.fromString(config.getString("target"));
        Player target = Bukkit.getPlayer(targetUUID);
        long startTime = config.getLong("startTime");

        return new PeekData(location, gameMode, target, startTime);
    }
}
