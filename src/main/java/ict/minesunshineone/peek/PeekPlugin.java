package ict.minesunshineone.peek;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import ict.minesunshineone.peek.command.PeekCommand;
import ict.minesunshineone.peek.handler.PeekStateHandler;
import ict.minesunshineone.peek.handler.PeekTargetHandler;
import ict.minesunshineone.peek.listener.PeekListener;
import ict.minesunshineone.peek.manager.CooldownManager;
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

        // 如果有PlaceholderAPI，注册变量
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PeekPlaceholderExpansion(this).register();
        }

        getLogger().info("Peek插件已启用！");
    }

    @Override
    public void onDisable() {
        if (stateHandler != null) {
            new HashMap<>(stateHandler.getActivePeeks()).forEach((uuid, data) -> {
                Player player = getServer().getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    stateHandler.endPeek(player, true);
                }
            });
        }

        if (statisticsManager != null) {
            statisticsManager.saveStats();
        }

        if (privacyManager != null) {
            privacyManager.savePrivacyStates();
        }

        getLogger().info("Peek插件已禁用！");
    }

    private void validateConfig() {
        FileConfiguration config = getConfig();
        boolean needsSave = false;

        // 检查并设置必要的配置项
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("language", "zh_CN");
        defaults.put("max-peek-duration", 5);
        defaults.put("cooldowns.peek", 60);
        defaults.put("statistics.enabled", true);
        defaults.put("statistics.save-interval", 600);
        defaults.put("debug", false);
        defaults.put("limits.max-peek-distance", 50.0);
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
}
