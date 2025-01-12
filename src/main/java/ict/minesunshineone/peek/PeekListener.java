package ict.minesunshineone.peek;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 处理与观察模式相关的事件监听器
 */
public class PeekListener implements Listener {

    private final PeekCommand peekCommand;
    private final PeekPlugin plugin;

    public PeekListener(PeekPlugin plugin, PeekCommand peekCommand) {
        if (peekCommand == null) {
            throw new IllegalArgumentException("命令执行器不能为空");
        }
        this.peekCommand = peekCommand;
        this.plugin = plugin;
    }

    /**
     * 处理玩家退出服务器事件 如果退出的玩家正在被观察或正在观察别人，需要处理相关逻辑
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();

        // 使用区域调度器来处理玩家退出
        plugin.getServer().getRegionScheduler().execute(plugin, location, () -> {
            handlePlayerQuit(player);
        });
    }

    private void handlePlayerQuit(Player player) {
        long quitTime = System.currentTimeMillis();

        // 如果退出的玩家正在观察别人，强制退出观察模式
        if (peekCommand.getPeekingPlayers().containsKey(player)) {
            PeekData data = peekCommand.getPeekingPlayers().get(player);
            if (plugin.getStatistics() != null) {
                long duration = (quitTime - data.getStartTime()) / 1000;
                plugin.getStatistics().recordPeekDuration(player, duration);
            }
            plugin.getServer().getRegionScheduler().execute(plugin,
                    player.getLocation(), () -> {
                peekCommand.handleExit(player);
            });
            // 保存离线数据
            plugin.getOfflinePeekManager().saveOfflinePlayerState(player, data);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 使用区域调度器执行恢复
        plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            plugin.getOfflinePeekManager().checkAndRestorePlayer(player);
        });
    }
}
