package ict.minesunshineone.peek.handler;

import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;

public class PeekTargetHandler {

    private final PeekPlugin plugin;

    public PeekTargetHandler(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean canPeek(Player peeker, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null || !target.isOnline()) {
            plugin.getMessages().send(peeker, "player-not-found", "player", targetName);
            return false;
        }

        if (peeker.equals(target)) {
            plugin.getMessages().send(peeker, "cannot-peek-self");
            return false;
        }

        if (plugin.getStateHandler().getActivePeeks().containsKey(target.getUniqueId())) {
            plugin.getMessages().send(peeker, "target-is-peeking");
            return false;
        }

        if (plugin.getCooldownManager().isOnCooldown(peeker)) {
            plugin.getMessages().send(peeker, "cooldown-peek", "time",
                    String.valueOf(plugin.getCooldownManager().getRemainingCooldown(peeker)));
            return false;
        }

        // 检查隐私模式，但有 bypass 权限的玩家可以无视
        if (plugin.getPrivacyManager().isPrivateMode(target) && !peeker.hasPermission("peek.bypass")) {
            plugin.getPrivacyManager().sendPeekRequest(peeker, target);
            return false;
        }

        return true;
    }

    /**
     * 检查是否应该静默 peek（不通知目标）
     * 用于有 bypass 权限的玩家
     */
    public boolean shouldSilentPeek(Player peeker) {
        return peeker.hasPermission("peek.bypass");
    }
}
