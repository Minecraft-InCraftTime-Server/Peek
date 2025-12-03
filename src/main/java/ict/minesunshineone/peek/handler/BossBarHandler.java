package ict.minesunshineone.peek.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;

/**
 * 负责管理 Peek 模式下的 BossBar 显示
 * 包括创建、更新、删除 BossBar 以及配置解析
 */
public class BossBarHandler {

    private final PeekPlugin plugin;
    private final Map<UUID, BossBar> distanceBossBars = new HashMap<>();
    
    // BossBar 配置
    private final boolean enabled;
    private final String titleFormat;
    private final BarStyle style;
    private final BarColor colorSafe;
    private final BarColor colorWarning;
    private final BarColor colorDanger;
    private final double thresholdWarning;
    private final double thresholdDanger;
    private final double maxPeekDistance;

    public BossBarHandler(PeekPlugin plugin) {
        this.plugin = plugin;
        this.maxPeekDistance = plugin.getConfig().getDouble("limits.max-peek-distance", 50.0);
        
        // 加载 BossBar 配置
        this.enabled = plugin.getConfig().getBoolean("bossbar.enabled", true);
        this.titleFormat = plugin.getConfig().getString("bossbar.title", "&d距离 &f{target}&d: &e{distance} &7/ &e{max_distance} &d格");
        this.style = parseBarStyle(plugin.getConfig().getString("bossbar.style", "SEGMENTED_10"));
        this.colorSafe = parseBarColor(plugin.getConfig().getString("bossbar.colors.safe", "GREEN"));
        this.colorWarning = parseBarColor(plugin.getConfig().getString("bossbar.colors.warning", "YELLOW"));
        this.colorDanger = parseBarColor(plugin.getConfig().getString("bossbar.colors.danger", "RED"));
        this.thresholdWarning = plugin.getConfig().getDouble("bossbar.thresholds.warning", 0.5);
        this.thresholdDanger = plugin.getConfig().getDouble("bossbar.thresholds.danger", 0.75);
    }
    
    private BarStyle parseBarStyle(String style) {
        try {
            return BarStyle.valueOf(style.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format("无效的 BossBar 样式: %s，使用默认值 SEGMENTED_10", style));
            return BarStyle.SEGMENTED_10;
        }
    }
    
    private BarColor parseBarColor(String color) {
        try {
            return BarColor.valueOf(color.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format("无效的 BossBar 颜色: %s，使用默认值 GREEN", color));
            return BarColor.GREEN;
        }
    }

    /**
     * 为观察者创建距离显示 BossBar
     * @param peeker 观察者
     * @param target 目标玩家
     */
    public void createDistanceBossBar(Player peeker, Player target) {
        if (!enabled) return;
        
        removeDistanceBossBar(peeker); // 确保没有残留
        
        String title = formatBossBarTitle(target.getName(), 0.0);
        BossBar bar = Bukkit.createBossBar(
                title,
                colorSafe,
                style
        );
        bar.setProgress(0.0);
        bar.addPlayer(peeker);
        distanceBossBars.put(peeker.getUniqueId(), bar);
        logDebug("Created BossBar for player %s, target: %s", peeker.getName(), target.getName());
    }

    /**
     * 为自我观察模式创建距离显示 BossBar
     * @param peeker 观察者（同时也是目标）
     * @param selfPeekLabel 自我观察的标签（如"原点"）
     */
    public void createSelfPeekBossBar(Player peeker, String selfPeekLabel) {
        if (!enabled) return;
        
        removeDistanceBossBar(peeker); // 确保没有残留
        
        String title = formatBossBarTitle(selfPeekLabel, 0.0);
        BossBar bar = Bukkit.createBossBar(
                title,
                colorSafe,
                style
        );
        bar.setProgress(0.0);
        bar.addPlayer(peeker);
        distanceBossBars.put(peeker.getUniqueId(), bar);
        logDebug("Created self-peek BossBar for player %s", peeker.getName());
    }
    
    private String formatBossBarTitle(String targetName, double distance) {
        return titleFormat
                .replace("{target}", targetName)
                .replace("{distance}", String.format("%.1f", distance))
                .replace("{max_distance}", String.format("%.1f", maxPeekDistance))
                .replace("&", "§");
    }

    /**
     * 更新观察者的距离 BossBar
     * @param peeker 观察者
     * @param distance 当前距离
     * @param targetName 目标玩家名称
     */
    public void updateDistanceBossBar(Player peeker, double distance, String targetName) {
        if (!enabled) return;
        
        BossBar bar = distanceBossBars.get(peeker.getUniqueId());
        if (bar == null) return;

        double progress = Math.min(distance / maxPeekDistance, 1.0);
        bar.setProgress(progress);

        // 根据配置的阈值改变颜色
        if (progress < thresholdWarning) {
            bar.setColor(colorSafe);
        } else if (progress < thresholdDanger) {
            bar.setColor(colorWarning);
        } else {
            bar.setColor(colorDanger);
        }

        bar.setTitle(formatBossBarTitle(targetName, distance));
    }

    /**
     * 更新观察者的距离 BossBar（从 PeekData 获取目标信息）
     * @param peeker 观察者
     * @param distance 当前距离
     * @param data PeekData 数据
     */
    public void updateDistanceBossBar(Player peeker, double distance, PeekData data) {
        if (!enabled || data == null) return;

        Player target = plugin.getServer().getPlayer(data.getTargetUUID());
        String targetName = target != null ? target.getName() : "未知";
        updateDistanceBossBar(peeker, distance, targetName);
    }

    /**
     * 移除观察者的距离 BossBar
     * @param peeker 观察者
     */
    public void removeDistanceBossBar(Player peeker) {
        BossBar bar = distanceBossBars.remove(peeker.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    /**
     * 安全地移除 BossBar（使用正确的线程调度）
     * @param peeker 观察者
     */
    public void safeRemoveDistanceBossBar(Player peeker) {
        // 如果插件已禁用，直接移除而不使用调度器
        if (!plugin.isEnabled()) {
            removeDistanceBossBar(peeker);
            return;
        }
        
        if (peeker.isOnline()) {
            peeker.getScheduler().run(plugin, scheduledTask -> removeDistanceBossBar(peeker), null);
        } else {
            removeDistanceBossBar(peeker);
        }
    }

    /**
     * 检查是否启用 BossBar
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 清理所有 BossBar
     */
    public void cleanup() {
        new HashMap<>(distanceBossBars).forEach((uuid, bar) -> {
            bar.removeAll();
            distanceBossBars.remove(uuid);
        });
    }

    private void logDebug(String message, Object... args) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(String.format(message, args));
        }
    }
}
