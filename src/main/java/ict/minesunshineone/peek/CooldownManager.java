package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

public class CooldownManager {

    private final Map<UUID, Long> peekCooldowns = new HashMap<>();
    private final int peekCooldown;
    private final PeekPlugin plugin;

    public CooldownManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.peekCooldown = plugin.getConfig().getInt("cooldowns.peek", 30);
    }

    public boolean checkPeekCooldown(Player player) {
        return checkCooldown(player, peekCooldowns, peekCooldown);
    }

    public int getRemainingPeekCooldown(Player player) {
        return getRemainingCooldown(player, peekCooldowns);
    }

    private boolean checkCooldown(Player player, Map<UUID, Long> cooldowns, int cooldownTime) {
        if (player.hasPermission("peek.nocooldown")) {
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUsage = cooldowns.get(uuid);

        if (lastUsage != null && now - lastUsage < cooldownTime * 1000L) {
            return false;
        }

        cooldowns.put(uuid, now);
        return true;
    }

    public int getRemainingCooldown(Player player, Map<UUID, Long> cooldowns) {
        if (player == null || player.hasPermission("peek.nocooldown")) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        Long lastUsage = cooldowns.get(uuid);
        if (lastUsage == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long remaining = (lastUsage + peekCooldown * 1000L - now) / 1000L;
        return Math.max(0, (int) remaining);
    }

    public void setCooldown(Player player) {
        int configuredCooldown = plugin.getConfig().getInt("cooldowns.peek", 30);
        peekCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (configuredCooldown * 1000L));
    }

    public void setCooldownAfterPeek(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        if (!player.hasPermission("peek.nocooldown")) {
            UUID uuid = player.getUniqueId();
            peekCooldowns.put(uuid, System.currentTimeMillis());

            // 定时清理过期的冷却时间
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task -> {
                peekCooldowns.remove(uuid);
            }, peekCooldown * 1000L, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
}
