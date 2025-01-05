package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PeekRangeChecker {

    private final PeekPlugin plugin;
    private final PeekCommand peekCommand;
    private final double maxDistance;
    private final double maxDistanceSquared;
    private ScheduledTask checkTask;
    private static final long CHECK_INTERVAL = 100L;

    public PeekRangeChecker(PeekPlugin plugin, PeekCommand peekCommand) {
        this.plugin = plugin;
        this.peekCommand = peekCommand;
        this.maxDistance = plugin.getConfig().getDouble("limits.max-peek-distance", 50.0);
        this.maxDistanceSquared = maxDistance * maxDistance;
        startRangeCheckTask();
    }

    private void startRangeCheckTask() {
        checkTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            Map<Player, PeekData> peekingPlayers = peekCommand.getPeekingPlayers();
            if (peekingPlayers.isEmpty()) {
                return;
            }

            for (Map.Entry<Player, PeekData> entry : new HashMap<>(peekingPlayers).entrySet()) {
                Player peeker = entry.getKey();
                Player target = entry.getValue().getTargetPlayer();

                if (peeker == null || target == null || !peeker.isOnline() || !target.isOnline()
                        || entry.getValue().isExiting()) {
                    continue;
                }

                try {
                    if (!peeker.getWorld().equals(target.getWorld())) {
                        continue;
                    }

                    if (peeker.getLocation().distanceSquared(target.getLocation()) > maxDistanceSquared) {
                        handleExceedRange(peeker);
                    }
                } catch (Exception e) {
                    if (plugin.isDebugEnabled()) {
                        plugin.getLogger().warning(String.format("检查玩家距离时发生错误: %s", e.getMessage()));
                    }
                }
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    private void handleExceedRange(Player peeker) {
        if (!peeker.isOnline()) {
            return;
        }

        plugin.getServer().getRegionScheduler().execute(plugin, peeker.getLocation(), () -> {
            if (peekCommand.getPeekingPlayers().containsKey(peeker)) {
                peekCommand.handleExit(peeker);
                peekCommand.sendMessage(peeker, "range-exceeded");
            }
        });
    }

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
    }
}
