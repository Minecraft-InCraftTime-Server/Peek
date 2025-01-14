package ict.minesunshineone.peek.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;

public class CooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final int cooldownDuration;

    public CooldownManager(PeekPlugin plugin) {
        this.cooldownDuration = plugin.getConfig().getInt("cooldowns.peek", 60);
    }

    public void setCooldown(Player player) {
        if (!player.hasPermission("peek.nocooldown")) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    public boolean isOnCooldown(Player player) {
        if (player.hasPermission("peek.nocooldown")) {
            return false;
        }

        Long lastUsage = cooldowns.get(player.getUniqueId());
        if (lastUsage == null) {
            return false;
        }

        return System.currentTimeMillis() - lastUsage < cooldownDuration * 1000L;
    }

    public int getRemainingCooldown(Player player) {
        if (player.hasPermission("peek.nocooldown")) {
            return 0;
        }

        Long lastUsage = cooldowns.get(player.getUniqueId());
        if (lastUsage == null) {
            return 0;
        }

        long remaining = (lastUsage + cooldownDuration * 1000L - System.currentTimeMillis()) / 1000L;
        return Math.max(0, (int) remaining);
    }

    public void clearCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}
