package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Objects;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Peek插件主类 用于管理插件的生命周期和配置
 */
public class PeekPlugin extends JavaPlugin {

    private Messages messages;        // 消息管理器
    private int maxPeekDuration;     // 最大观察时间（秒）
    private Statistics statistics;
    private CooldownManager cooldownManager;
    private boolean checkTargetPermission;
    private boolean debug;
    private PeekCommand peekCommand;
    private OfflinePeekManager offlinePeekManager;

    @Override
    public void onEnable() {
        // 加载配置文件
        saveDefaultConfig();
        loadConfig();

        // 初始化各个管理器
        messages = new Messages(this);
        cooldownManager = new CooldownManager(this);

        if (getConfig().getBoolean("statistics.enabled", true)) {
            statistics = new Statistics(this);
        }

        // 创建命令执行器实例
        peekCommand = new PeekCommand(this);

        // 注册命令
        PluginCommand peekCmd = Objects.requireNonNull(getCommand("peek"), "命令'peek'未在plugin.yml中注册");
        peekCmd.setExecutor(peekCommand);
        peekCmd.setTabCompleter(peekCommand);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PeekListener(this, peekCommand), this);

        offlinePeekManager = new OfflinePeekManager(this);

        getLogger().info("Peek插件已启用！");
    }

    @Override
    public void onDisable() {
        // 结束所有玩家的观察状态
        if (peekCommand != null) {
            new HashMap<>(peekCommand.getPeekingPlayers()).forEach((player, data) -> {
                if (player.isOnline()) {
                    try {
                        // 直接设置游戏模式
                        player.setGameMode(data.getOriginalGameMode());
                        // 同步传送
                        player.teleport(data.getOriginalLocation());

                        // 记录观察时长
                        if (statistics != null) {
                            long duration = (System.currentTimeMillis() - data.getStartTime()) / 1000;
                            statistics.recordPeekDuration(player, duration);
                        }

                        // 发送消息
                        player.sendMessage(messages.get("peek-end"));

                        // 如果目标玩家在线，也发送消息给他们
                        Player target = data.getTargetPlayer();
                        if (target != null && target.isOnline()) {
                            target.sendMessage(messages.get("peek-end-target",
                                    "player", player.getName()));
                        }
                    } catch (Exception e) {
                        getLogger().warning(String.format("无法在关服时处理玩家 %s 的观察状态: %s", player.getName(), e.getMessage()));
                    }
                }
            });
            // 清空观察列表
            peekCommand.getPeekingPlayers().clear();
        }

        // 保存统计数据
        if (statistics != null) {
            statistics.saveStats();
        }

        getLogger().info("Peek插件已禁用！");
    }

    /**
     * 加载配置文件 读取最大观察时间等配置项
     */
    private void loadConfig() {
        reloadConfig();
        this.maxPeekDuration = getConfig().getInt("max-peek-duration", 300);
        this.checkTargetPermission = getConfig().getBoolean("permissions.check-target", true);
        this.debug = getConfig().getBoolean("debug", false);
    }

    public Messages getMessages() {
        return messages;
    }

    public int getMaxPeekDuration() {
        return maxPeekDuration;
    }

    public Statistics getStatistics() {
        return statistics;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public boolean isCheckTargetPermission() {
        return checkTargetPermission;
    }

    public boolean isDebugEnabled() {
        return debug;
    }

    public OfflinePeekManager getOfflinePeekManager() {
        return offlinePeekManager;
    }
}
