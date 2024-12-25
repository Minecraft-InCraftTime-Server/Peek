package ict.minesunshineone.peek;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

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
        new BukkitRunnable() {
            @Override
            public void run() {
                Player player = event.getPlayer();
                handlePlayerQuit(player);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void handlePlayerQuit(Player player) {
        long quitTime = System.currentTimeMillis();

        // 如果退出的玩家正在被观察，强制所有观察者退出
        peekCommand.getPeekingPlayers().entrySet().removeIf(entry -> {
            if (entry.getValue().getTargetPlayer().equals(player)) {
                Player peeker = entry.getKey();
                // 记录观察时长
                if (plugin.getStatistics() != null) {
                    long duration = (quitTime - entry.getValue().getStartTime()) / 1000;
                    plugin.getStatistics().recordPeekDuration(peeker, duration);
                }
                peekCommand.handleExit(peeker);
                peeker.sendMessage(plugin.getMessages().get("target-offline"));
                return true;
            }
            return false;
        });

        // 如果退出的玩家正在观察别人，强制退出观察模式
        if (peekCommand.getPeekingPlayers().containsKey(player)) {
            // 记录观察时长
            PeekData data = peekCommand.getPeekingPlayers().get(player);
            if (plugin.getStatistics() != null) {
                long duration = (quitTime - data.getStartTime()) / 1000;
                plugin.getStatistics().recordPeekDuration(player, duration);
            }
            peekCommand.handleExit(player);
        }
    }

    /**
     * 处理玩家游戏模式改变事件 如果是被观察的玩家，需要处理相关逻辑
     */
    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (peekCommand.getPeekingPlayers().containsKey(player)) {
            // 如果是管理员改变游戏模式（而不是插件内部改变）
            if (!event.getNewGameMode().equals(GameMode.SPECTATOR)) {
                peekCommand.handleExit(player);
                player.sendMessage("§c由于游戏模式被改变，已强制退出观察模式。");
            }
        }
    }
}
