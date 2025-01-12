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

        if (plugin.getStateHandler().getActivePeeks().containsKey(target)) {
            plugin.getMessages().send(peeker, "target-is-peeking");
            return false;
        }

        if (plugin.getCooldownManager().isOnCooldown(peeker)) {
            plugin.getMessages().send(peeker, "cooldown-peek", "time",
                    String.valueOf(plugin.getCooldownManager().getRemainingCooldown(peeker)));
            return false;
        }

        if (plugin.getPrivacyManager().isPrivateMode(target)) {
            plugin.getPrivacyManager().sendPeekRequest(peeker, target);
            return false;
        }

        return true;
    }
}
