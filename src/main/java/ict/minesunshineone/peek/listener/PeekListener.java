package ict.minesunshineone.peek.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.command.PeekCommand;

public class PeekListener implements Listener {

    private final PeekPlugin plugin;
    private final PeekCommand peekCommand;

    public PeekListener(PeekPlugin plugin, PeekCommand peekCommand) {
        this.plugin = plugin;
        this.peekCommand = peekCommand;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 如果退出的玩家正在观察别人，结束观察状态
        if (plugin.getStateHandler().getActivePeeks().containsKey(player)) {
            plugin.getStateHandler().endPeek(player);
        }

        // 如果退出的玩家正在被观察，通知观察者
        plugin.getStateHandler().getActivePeeks().forEach((peeker, data) -> {
            if (data.getTargetPlayer().equals(player)) {
                plugin.getStateHandler().endPeek(peeker);
                plugin.getMessages().send(peeker, "target-offline");
            }
        });

        // 如果玩家有待处理的请求，取消它们
        plugin.getPrivacyManager().cancelAllRequests(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getStateManager().restorePlayerState(player);
    }
}
