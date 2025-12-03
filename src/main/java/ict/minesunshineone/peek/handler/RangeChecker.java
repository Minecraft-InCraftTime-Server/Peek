package ict.minesunshineone.peek.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

/**
 * 负责管理 Peek 模式下的距离检查任务
 * 包括普通 Peek 和自我观察模式的距离监控
 */
public class RangeChecker {

    private final PeekPlugin plugin;
    private final Map<UUID, ScheduledTask> rangeCheckers = new HashMap<>();
    private final Object rangeCheckersLock = new Object();
    private final double maxPeekDistance;

    public RangeChecker(PeekPlugin plugin) {
        this.plugin = plugin;
        this.maxPeekDistance = plugin.getConfig().getDouble("limits.max-peek-distance", 50.0);
    }

    /**
     * 启动普通 Peek 的距离检查器
     * @param peeker 观察者
     * @param target 目标玩家
     * @param onRangeExceeded 超出距离时的回调
     * @param onTargetOffline 目标离线时的回调
     * @param onDistanceUpdate 距离更新时的回调
     * @param onDifferentWorld 不同世界时的回调
     */
    public void startRangeChecker(Player peeker, Player target,
                                   Runnable onRangeExceeded,
                                   Runnable onTargetOffline,
                                   Consumer<Double> onDistanceUpdate,
                                   Runnable onDifferentWorld) {
        synchronized (rangeCheckersLock) {
            // 先停止已有的检查器（如果有的话）
            ScheduledTask existingTask = rangeCheckers.remove(peeker.getUniqueId());
            if (existingTask != null) {
                existingTask.cancel();
            }

            ScheduledTask task = plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                    target.getLocation(), // 使用target的位置而不是peeker的位置
                    scheduledTask -> {
                        if (!target.isOnline()) {
                            onTargetOffline.run();
                            return;
                        }

                        try {
                            // 检查观察者是否死亡
                            if (peeker.isDead()) {
                                return;
                            }

                            if (peeker.getWorld().equals(target.getWorld())) {
                                double distance = peeker.getLocation().distance(target.getLocation());
                                onDistanceUpdate.accept(distance);
                                if (distance > maxPeekDistance) {
                                    onRangeExceeded.run();
                                }
                            } else {
                                // 跨维度
                                onDifferentWorld.run();
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning(String.format("距离检查时发生错误：%s", e.getMessage()));
                            onRangeExceeded.run();
                        }
                    },
                    1L, 10L);

            rangeCheckers.put(peeker.getUniqueId(), task);
        }
    }

    /**
     * 启动自我观察模式的距离检查器
     * @param peeker 观察者
     * @param originalLocation 原始位置
     * @param getPeekData 获取 PeekData 的函数
     * @param onRangeExceeded 超出距离时的回调
     * @param onWorldChanged 世界改变时的回调
     * @param onDistanceUpdate 距离更新时的回调
     * @param onError 发生错误时的回调
     */
    public void startSelfRangeChecker(Player peeker, Location originalLocation,
                                       java.util.function.Supplier<PeekData> getPeekData,
                                       Runnable onRangeExceeded,
                                       Runnable onWorldChanged,
                                       java.util.function.Consumer<Double> onDistanceUpdate,
                                       Runnable onError) {
        synchronized (rangeCheckersLock) {
            // 先停止已有的检查器（如果有的话）
            stopRangeChecker(peeker);

            ScheduledTask task = plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                    originalLocation, // 使用原始位置
                    scheduledTask -> {
                        // 检查玩家是否在线
                        if (!peeker.isOnline()) {
                            logDebug("Player %s went offline during self peek, ending peek", peeker.getName());
                            onError.run();
                            return;
                        }

                        try {
                            // 检查观察者是否死亡（与普通peek逻辑一致）
                            if (peeker.isDead()) {
                                logDebug("Player %s died during self peek, but continuing to monitor for respawn", peeker.getName());
                                return; // 不立即结束，等待重生处理
                            }

                            // 额外的状态检查
                            PeekData data = getPeekData.get();
                            if (data == null) {
                                logDebug("PeekData for player %s is null, stopping range checker", peeker.getName());
                                scheduledTask.cancel();
                                return;
                            }

                            // 检查是否超出距离限制（相对于原始位置）
                            if (peeker.getWorld().equals(originalLocation.getWorld())) {
                                double distance = peeker.getLocation().distance(originalLocation);
                                // 更新距离显示
                                onDistanceUpdate.accept(distance);
                                if (distance > maxPeekDistance) {
                                    logDebug("Player %s exceeded self peek distance: %.2f > %.2f", 
                                            peeker.getName(), distance, maxPeekDistance);
                                    onRangeExceeded.run();
                                }
                            } else {
                                // 如果换了世界，自动结束自我观察
                                logDebug("Player %s changed world during self peek", peeker.getName());
                                onWorldChanged.run();
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning(String.format("自我观察距离检查时发生错误：%s", e.getMessage()));
                            if (plugin.getConfig().getBoolean("debug", false)) {
                                e.printStackTrace();
                            }
                            onError.run();
                        }
                    },
                    1L, 10L);

            rangeCheckers.put(peeker.getUniqueId(), task);
            logDebug("Started self range checker for player: %s", peeker.getName());
        }
    }

    /**
     * 停止指定玩家的距离检查器
     * @param peeker 观察者
     */
    public void stopRangeChecker(Player peeker) {
        synchronized (rangeCheckersLock) {
            ScheduledTask task = rangeCheckers.remove(peeker.getUniqueId());
            if (task != null) {
                task.cancel();
            }
        }
    }

    /**
     * 获取最大 Peek 距离
     * @return 最大距离
     */
    public double getMaxPeekDistance() {
        return maxPeekDistance;
    }

    /**
     * 清理所有检查器
     */
    public void cleanup() {
        synchronized (rangeCheckersLock) {
            new HashMap<>(rangeCheckers).forEach((peeker, task) -> {
                task.cancel();
                rangeCheckers.remove(peeker);
            });
        }
    }

    private void logDebug(String message, Object... args) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(String.format(message, args));
        }
    }
}
