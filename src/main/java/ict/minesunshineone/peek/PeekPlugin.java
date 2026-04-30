package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import ict.minesunshineone.peek.command.PeekCommand;
import ict.minesunshineone.peek.data.PeekData;
import ict.minesunshineone.peek.handler.PeekStateHandler;
import ict.minesunshineone.peek.handler.PeekTargetHandler;
import ict.minesunshineone.peek.listener.PeekInteractionListener;
import ict.minesunshineone.peek.listener.PeekListener;
import ict.minesunshineone.peek.listener.PeekPacketListener;
import ict.minesunshineone.peek.manager.CooldownManager;
import java.util.UUID;
import ict.minesunshineone.peek.manager.PrivacyManager;
import ict.minesunshineone.peek.manager.StateManager;
import ict.minesunshineone.peek.manager.StatisticsManager;
import ict.minesunshineone.peek.placeholder.PeekPlaceholderExpansion;
import ict.minesunshineone.peek.util.Messages;

/**
 * Peek插件主类 用于管理插件的生命周期和配置
 */
public class PeekPlugin extends JavaPlugin {

    private Messages messages;        // 消息管理器
    private StateManager stateManager;
    private PeekStateHandler stateHandler;
    private PeekTargetHandler targetHandler;
    private CooldownManager cooldownManager;
    private PrivacyManager privacyManager;
    private StatisticsManager statisticsManager;
    private PeekPacketListener packetListener;

    @Override
    public void onEnable() {
        // 加载配置文件
        saveDefaultConfig();
        validateConfig();

        // 初始化各个管理器
        this.messages = new Messages(this);
        this.stateManager = new StateManager(this);
        this.stateHandler = new PeekStateHandler(this);
        this.targetHandler = new PeekTargetHandler(this);
        this.cooldownManager = new CooldownManager(this);
        this.privacyManager = new PrivacyManager(this);
        this.statisticsManager = new StatisticsManager(this);

        // 如果服务器有ProtocolLib，初始化数据包监听器
        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            this.packetListener = new PeekPacketListener(this);
            getLogger().info("已启用ProtocolLib支持！");
        }

        // 注册命令
        PeekCommand peekCommand = new PeekCommand(this);
        var cmd = getCommand("peek");
        if (cmd != null) {
            cmd.setExecutor(peekCommand);
            cmd.setTabCompleter(peekCommand);
        } else {
            getLogger().severe("无法注册 peek 命令，请检查 plugin.yml 配置");
        }

        // 注册监听器
        getServer().getPluginManager().registerEvents(new PeekListener(this), this);
        getServer().getPluginManager().registerEvents(new PeekInteractionListener(this), this);

        // 如果有PlaceholderAPI，注册变量
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PeekPlaceholderExpansion(this).register();
        }

        getLogger().info("Peek插件已启用！");
    }

    @Override
    public void onDisable() {
        if (stateHandler != null) {
            // 先取消所有定时任务（RangeChecker、BossBar等）
            stateHandler.cleanup();

            // 同步恢复所有观察中的玩家状态
            // 服务器关闭时不能依赖异步任务完成
            for (Map.Entry<UUID, PeekData> entry : new HashMap<>(stateHandler.getActivePeeks()).entrySet()) {
                Player player = getServer().getPlayer(entry.getKey());
                PeekData data = entry.getValue();
                if (player == null || !player.isOnline()) {
                    continue;
                }

                // 记录统计
                long duration = (System.currentTimeMillis() - data.getStartTime()) / 1000;
                statisticsManager.recordPeekEnd(player, duration);

                if (!player.isDead()) {
                    try {
                        ict.minesunshineone.peek.util.PlayerStateUtil.forceExitRidingState(player);
                        player.teleport(data.getOriginalLocation());
                        stateHandler.getStateRestorer().applyRestoredState(player, data);
                    } catch (Exception e) {
                        getLogger().warning(String.format("关服时无法恢复玩家 %s 的状态，将在重连时恢复: %s",
                                player.getName(), e.getMessage()));
                        // 状态文件保留在磁盘上，下次登录时 onPlayerJoin 自动恢复
                    }
                }
                // 死亡玩家的状态文件保留，下次登录时自动恢复
            }
        }

        if (statisticsManager != null) {
            statisticsManager.saveStats();
        }

        // 注意：隐私管理器现在使用 PersistentData，无需手动保存
        // 数据会自动随玩家数据保存，支持 HuskSync 等跨服同步插件

        if (packetListener != null) {
            packetListener.unregisterPacketListeners();
        }

        getLogger().info("Peek插件已禁用！");
    }

    private void validateConfig() {
        FileConfiguration config = getConfig();
        boolean needsSave = false;

        // 检查并设置必要的配置项
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("language", "zh_CN");
        defaults.put("limits.cooldowns", 60);
        defaults.put("statistics.enabled", true);
        defaults.put("statistics.save-interval", 600);
        defaults.put("debug", false);
        defaults.put("limits.max-peek-distance", 50.0);
        defaults.put("limits.block-container-interaction", false);
        defaults.put("privacy.request-timeout", 30);
        defaults.put("privacy.cooldown.enabled", true);
        defaults.put("privacy.cooldown.duration", 60);

        // 声音设置
        defaults.put("sounds.start-peek", "BLOCK_NOTE_BLOCK_PLING");
        defaults.put("sounds.end-peek", "BLOCK_NOTE_BLOCK_BASS");
        defaults.put("privacy.sounds.request", "BLOCK_NOTE_BLOCK_PLING");
        defaults.put("privacy.sounds.accept", "ENTITY_PLAYER_LEVELUP");
        defaults.put("privacy.sounds.deny", "ENTITY_VILLAGER_NO");

        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (!config.contains(entry.getKey())) {
                config.set(entry.getKey(), entry.getValue());
                needsSave = true;
            }
        }

        // 验证声音名称
        var soundsSection = config.getConfigurationSection("sounds");
        if (soundsSection != null) {
            for (String key : soundsSection.getKeys(false)) {
                String soundName = config.getString("sounds." + key);
                try {
                    Sound.valueOf(soundName);
                } catch (IllegalArgumentException e) {
                    getLogger().warning(String.format("配置中存在无效的音效名称：%s", soundName));
                    config.set("sounds." + key, defaults.get("sounds." + key));
                    needsSave = true;
                }
            }
        }

        if (needsSave) {
            saveConfig();
        }
    }

    // Getters
    public Messages getMessages() {
        return messages;
    }

    public StateManager getStateManager() {
        return stateManager;
    }

    public PeekStateHandler getStateHandler() {
        return stateHandler;
    }

    public PeekTargetHandler getTargetHandler() {
        return targetHandler;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public PrivacyManager getPrivacyManager() {
        return privacyManager;
    }

    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }

    /**
     * 重新加载配置和消息文件
     * 用于调试或运行时更新配置
     */
    public void reloadConfigs() {
        reloadConfig();
        if (messages != null) {
            messages.reloadMessages();
        }
        getLogger().info("配置文件已重新加载");
    }
}
