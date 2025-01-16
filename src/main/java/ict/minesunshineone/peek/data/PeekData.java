package ict.minesunshineone.peek.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffect;

public class PeekData {

    private final Location originalLocation;
    private final GameMode originalGameMode;
    private final UUID targetUUID;
    private final long startTime;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final Collection<PotionEffect> potionEffects;
    private boolean exiting;

    public PeekData(Location originalLocation, GameMode originalGameMode, UUID targetUUID,
            long startTime, double health, int foodLevel, float saturation, Collection<PotionEffect> potionEffects) {
        this.originalLocation = originalLocation;
        this.originalGameMode = originalGameMode;
        this.targetUUID = targetUUID;
        this.startTime = startTime;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.potionEffects = new ArrayList<>(potionEffects);
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

    public double getHealth() {
        return health;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public Collection<PotionEffect> getPotionEffects() {
        return Collections.unmodifiableCollection(potionEffects);
    }

    public float getSaturation() {
        return saturation;
    }
}
