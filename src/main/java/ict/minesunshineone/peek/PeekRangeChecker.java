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

                if (peeker == null || !peeker.isOnline() || entry.getValue().isExiting()) {
                    continue;
                }

                if (target == null || !target.isOnline()
                        || !peeker.getWorld().equals(target.getWorld())
                        || peeker.getLocation().distanceSquared(target.getLocation()) > maxDistanceSquared) {

                    plugin.getServer().getRegionScheduler().execute(plugin,
                            peeker.getLocation(), () -> {
                        peekCommand.handleExit(peeker);
                        peeker.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(plugin.getMessages().get("range-exceeded")));
                    });
                }
            }
        }, CHECK_INTERVAL, CHECK_INTERVAL);
    }

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
    }
}
