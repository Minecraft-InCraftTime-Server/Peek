package ict.minesunshineone.peek.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PeekListener implements Listener {

    private final PeekPlugin plugin;

    public PeekListener(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 如果是观察者下线，只记录状态，不执行恢复操作
        if (plugin.getStateHandler().getActivePeeks().containsKey(player.getUniqueId())) {

            // 移除活跃观察记录，但保留状态文件
            plugin.getStateHandler().removeActivePeek(player);

            // 停止距离检查器并移除 BossBar
            plugin.getStateHandler().getRangeChecker().stopRangeChecker(player);
            plugin.getStateHandler().getBossBarHandler().safeRemoveDistanceBossBar(player);
        }

        // 如果是被观察者下线，结束所有观察他的玩家的观察状态
        // 但要排除自我观察的情况（观察者和被观察者是同一人）
        for (Map.Entry<UUID, PeekData> entry : new HashMap<>(plugin.getStateHandler().getActivePeeks()).entrySet()) {
            UUID peekerUUID = entry.getKey();
            UUID targetUUID = entry.getValue().getTargetUUID();
            
            // 如果被观察者下线了，且不是自我观察
            if (player.getUniqueId().equals(targetUUID) && !peekerUUID.equals(targetUUID)) {
                Player peeker = plugin.getServer().getPlayer(peekerUUID);
                if (peeker != null) {
                    plugin.getStateHandler().endPeek(peeker);
                }
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
            // 启动一个循环任务检查玩家是否还在死亡状态
            plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                    player.getLocation(),
                    task -> {
                        if (!player.isOnline()) {
                            task.cancel();
                            return;
                        }

                        if (!player.isDead()) {
                            task.cancel();
                            // 直接恢复状态
                            plugin.getStateHandler().getStateRestorer().restorePlayerState(player, savedState);
                            plugin.getStateManager().clearPlayerState(player);
                            // 发送断线重连提示
                            plugin.getMessages().send(player, "peek-end-offline");
                        }
                    },
                    1L, 20L); // 每秒检查一次
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        // 如果玩家死亡时有发出的请求，取消所有请求
        for (Map.Entry<UUID, Map<UUID, ScheduledTask>> entry
                : plugin.getPrivacyManager().getPendingRequests().entrySet()) {
            Map<UUID, ScheduledTask> requests = entry.getValue();
            if (requests.containsKey(player.getUniqueId())) {
                // 取消该玩家发出的请求
                Player target = plugin.getServer().getPlayer(entry.getKey());
                plugin.getPrivacyManager().removePendingRequest(player, target);

                // 发送消息给双方
                plugin.getMessages().send(player, "request-cancelled-death");
                if (target != null && target.isOnline()) {
                    plugin.getMessages().send(target, "request-cancelled-death-target");
                }
            }
        }
    }
}
