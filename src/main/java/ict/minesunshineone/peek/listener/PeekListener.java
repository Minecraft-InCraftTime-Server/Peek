package ict.minesunshineone.peek.listener;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;

public class PeekListener implements Listener {

    private final PeekPlugin plugin;

    public PeekListener(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 如果是观察者下线，只记录状态，不执行恢复操作
        if (plugin.getStateHandler().getActivePeeks().containsKey(player)) {
            // 只保存状态，不调用endPeek
            plugin.getStateManager().savePlayerState(player,
                    plugin.getStateHandler().getActivePeeks().get(player));

            // 移除活跃观察记录，但保留状态文件
            plugin.getStateHandler().removeActivePeek(player);

            // 停止距离检查器
            plugin.getStateHandler().stopRangeChecker(player);
        }

        // 如果是被观察者下线，结束所有观察他的玩家的观察状态
        for (Map.Entry<Player, PeekData> entry
                : new HashMap<>(plugin.getStateHandler().getActivePeeks()).entrySet()) {
            if (player.equals(entry.getValue().getTargetPlayer())) {
                plugin.getStateHandler().endPeek(entry.getKey());
            }
        }

        // 取消所有相关的请求
        plugin.getPrivacyManager().cancelAllRequests(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 检查是否有未恢复的状态
        PeekData savedState = plugin.getStateManager().getPlayerState(player);
        if (savedState != null) {
            // 恢复玩家状态
            plugin.getServer().getRegionScheduler().run(plugin,
                    savedState.getOriginalLocation(),
                    task -> {
                        player.teleportAsync(savedState.getOriginalLocation())
                                .thenAccept(success -> {
                                    if (success) {
                                        player.setGameMode(savedState.getOriginalGameMode());
                                        plugin.getStateManager().clearPlayerState(player);
                                    } else {
                                        Location spawnLoc = player.getBedSpawnLocation() != null
                                                ? player.getBedSpawnLocation()
                                                : player.getWorld().getSpawnLocation();

                                        if (spawnLoc != null) {
                                            player.teleportAsync(spawnLoc).thenAccept(spawnSuccess -> {
                                                if (spawnSuccess) {
                                                    player.setGameMode(savedState.getOriginalGameMode());
                                                }
                                                plugin.getStateManager().clearPlayerState(player);
                                            });
                                        } else {
                                            plugin.getLogger().warning(String.format("无法找到玩家 %s 的有效重生点", player.getName()));
                                            plugin.getStateManager().clearPlayerState(player);
                                        }
                                    }
                                });
                    });
        }
    }
}
