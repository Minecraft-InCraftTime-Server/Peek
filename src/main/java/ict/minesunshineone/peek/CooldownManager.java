package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

public class CooldownManager {

    private final Map<UUID, Long> peekCooldowns = new HashMap<>();
    private final Map<UUID, Long> msgCooldowns = new HashMap<>();
    private final int peekCooldown;
    private final int msgCooldown;

    public CooldownManager(PeekPlugin plugin) {
        this.peekCooldown = plugin.getConfig().getInt("cooldowns.peek", 30);
        this.msgCooldown = plugin.getConfig().getInt("cooldowns.msg", 60);
    }

    public boolean checkPeekCooldown(Player player) {
        return checkCooldown(player, peekCooldowns, peekCooldown);
    }

    public boolean checkMsgCooldown(Player player) {
        return checkCooldown(player, msgCooldowns, msgCooldown);
    }

    public int getRemainingPeekCooldown(Player player) {
        return getRemainingCooldown(player, peekCooldowns);
    }

    public int getRemainingMsgCooldown(Player player) {
        return getRemainingCooldown(player, msgCooldowns);
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

    private int getRemainingCooldown(Player player, Map<UUID, Long> cooldowns) {
        if (player.hasPermission("peek.nocooldown")) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        Long lastUsage = cooldowns.get(uuid);
        if (lastUsage == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long diff = (lastUsage + peekCooldown * 1000L - now) / 1000L;
        return diff > 0 ? (int) diff : 0;
    }
}
