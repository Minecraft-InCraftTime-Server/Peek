package ict.minesunshineone.peek;

import java.util.Objects;

import org.bukkit.command.PluginCommand;
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
        PeekCommand peekCommand = new PeekCommand(this);

        // 注册命令
        PluginCommand peekCmd = Objects.requireNonNull(getCommand("peek"), "命令'peek'未在plugin.yml中注册");
        peekCmd.setExecutor(peekCommand);
        peekCmd.setTabCompleter(peekCommand);

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new PeekListener(this, peekCommand), this);

        getLogger().info("Peek插件已启用！");
    }

    @Override
    public void onDisable() {
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
}
