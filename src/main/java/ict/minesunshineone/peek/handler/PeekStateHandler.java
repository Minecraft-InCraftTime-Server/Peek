package ict.minesunshineone.peek.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PeekStateHandler {

    private final PeekPlugin plugin;
    private final Map<UUID, PeekData> activePeeks = new HashMap<>();
    private final Map<UUID, ScheduledTask> rangeCheckers = new HashMap<>();
    private final Object rangeCheckersLock = new Object();
    private final double maxPeekDistance;

    public PeekStateHandler(PeekPlugin plugin) {
        this.plugin = plugin;
        this.maxPeekDistance = plugin.getConfig().getDouble("limits.max-peek-distance", 50.0);
    }

    public void startPeek(Player peeker, Player target) {
        if (peeker == null || target == null) {
            plugin.getLogger().warning("尝试对空玩家使用贴贴功能");
            return;
        }

        // 添加死亡检查
        if (peeker.isDead()) {
            plugin.getMessages().send(peeker, "cannot-peek-while-dead");
            return;
        }

        // 防止并发修改
        synchronized (activePeeks) {
            if (activePeeks.containsKey(peeker.getUniqueId())) {
                plugin.getMessages().send(peeker, "already-peeking");
                return;
            }

            logDebug("Starting peek: %s -> %s", peeker.getName(), target.getName());
            PeekData data = new PeekData(
                    peeker.getLocation().clone(),
                    peeker.getGameMode(),
                    target.getUniqueId(),
                    System.currentTimeMillis(),
                    peeker.getHealth(),
                    peeker.getFoodLevel(),
                    peeker.getSaturation(),
                    peeker.getActivePotionEffects()
            );

            activePeeks.put(peeker.getUniqueId(), data);
            plugin.getStateManager().savePlayerState(peeker, data);
            plugin.getStatisticsManager().recordPeekStart(peeker, target);

            teleportAndSetGameMode(peeker, target);
        }

        // 发送消息
        plugin.getMessages().send(peeker, "peek-start", "player", target.getName());
        plugin.getMessages().send(target, "being-peeked", "player", peeker.getName());

        // 播放声音
        playSound(target, "start-peek");

        updateActionBar(target);
    }

    public void startSelfPeek(Player peeker) {
        if (peeker == null) {
            plugin.getLogger().warning("尝试对空玩家使用自我观察功能");
            return;
        }

        // 添加死亡检查
        if (peeker.isDead()) {
            plugin.getMessages().send(peeker, "cannot-peek-while-dead");
            return;
        }

        // 添加在线检查
        if (!peeker.isOnline()) {
            plugin.getLogger().warning(String.format("尝试对离线玩家 %s 使用自我观察功能", peeker.getName()));
            return;
        }

        // 防止并发修改
        synchronized (activePeeks) {
            if (activePeeks.containsKey(peeker.getUniqueId())) {
                plugin.getMessages().send(peeker, "already-peeking");
                return;
            }

            try {
                logDebug("Starting self peek: %s", peeker.getName());
                
                // 保存当前位置作为原始位置
                Location originalLocation = peeker.getLocation().clone();
                
                // 创建特殊的 PeekData，目标设为自己
                PeekData data = new PeekData(
                        originalLocation,
                        peeker.getGameMode(),
                        peeker.getUniqueId(), // 目标设为自己
                        System.currentTimeMillis(),
                        peeker.getHealth(),
                        peeker.getFoodLevel(),
                        peeker.getSaturation(),
                        peeker.getActivePotionEffects()
                );

                activePeeks.put(peeker.getUniqueId(), data);
                plugin.getStateManager().savePlayerState(peeker, data);
                plugin.getStatisticsManager().recordPeekStart(peeker, peeker); // 统计中目标也是自己

                // 设置为观察者模式但不传送
                setSelfPeekGameMode(peeker);
                
                // 发送消息
                plugin.getMessages().send(peeker, "self-peek-start");

                // 播放声音
                playSound(peeker, "start-peek");

                // 启动self距离检查器，确保不会超出距离限制
                startSelfRangeChecker(peeker, originalLocation);
                
                logDebug("Self peek started successfully for player: %s", peeker.getName());
                
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("启动自我观察时发生错误，玩家: %s，错误: %s", peeker.getName(), e.getMessage()));
                if (plugin.getConfig().getBoolean("debug", false)) {
                    e.printStackTrace();
                }
                
                // 清理可能的残留状态
                activePeeks.remove(peeker.getUniqueId());
                stopRangeChecker(peeker);
                
                // 通知玩家发生错误
                plugin.getMessages().send(peeker, "command-error");
            }
        }
    }

    public void endPeek(Player peeker, boolean shouldRestore) {
        if (peeker == null) {
            plugin.getLogger().warning("尝试对空玩家结束贴贴功能");
            return;
        }

        synchronized (activePeeks) {
            PeekData data = activePeeks.get(peeker.getUniqueId());
            if (data == null) {
                return;
            }

            if (data.isExiting()) {
                return;
            }

            data.setExiting(true);
            Player target = plugin.getServer().getPlayer(data.getTargetUUID());

            // 记录统计
            long duration = (System.currentTimeMillis() - data.getStartTime()) / 1000;
            plugin.getStatisticsManager().recordPeekEnd(peeker, duration);

            // 设置冷却
            plugin.getCooldownManager().setCooldown(peeker);

            // 停止距离检查器
            stopRangeChecker(peeker);

            if (shouldRestore && peeker.isOnline()) {
                if (peeker.isDead()) {
                    // 启动重生检查任务
                    plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                            peeker.getLocation(),
                            task -> {
                                if (!peeker.isOnline()) {
                                    task.cancel();
                                    return;
                                }

                                if (!peeker.isDead()) {
                                    task.cancel();
                                    // 玩家重生后恢复状态
                                    restorePlayerState(peeker, data);
                                    plugin.getStateManager().clearPlayerState(peeker);
                                    // 发送重生后恢复提示
                                    plugin.getMessages().send(peeker, "peek-end-respawn");
                                }
                            },
                            1L, 20L); // 每秒检查一次
                } else {
                    // 玩家未死亡，直接恢复状态
                    restorePlayerState(peeker, data);
                }
            }

            if (shouldRestore && !peeker.isDead()) {
                plugin.getStateManager().clearPlayerState(peeker);
            }

            // 发送消息
            if (peeker.isOnline()) {
                plugin.getMessages().send(peeker, "peek-end");
            }
            
            // 检查是否是自我观察模式（target是自己）
            boolean isSelfPeek = target != null && target.getUniqueId().equals(peeker.getUniqueId());
            
            // 只有在非自我观察模式下才给目标发送消息
            if (target != null && target.isOnline() && !isSelfPeek) {
                plugin.getMessages().send(target, "peek-end-target", "player", peeker.getName());
                playSound(target, "end-peek");
            }

            // 最后才移除活动peek
            activePeeks.remove(peeker.getUniqueId());

            // 更新目标玩家的actionbar（自我观察时不需要更新）
            if (target != null && target.isOnline() && !isSelfPeek) {
                updateActionBar(target);
            }
        }
    }

    public void endPeek(Player peeker) {
        endPeek(peeker, true);
    }

    private void teleportAndSetGameMode(Player peeker, Player target) {
        // 先切换游戏模式
        plugin.getServer().getRegionScheduler().run(plugin, peeker.getLocation(), task -> {
            try {
                // 如果玩家在睡觉，先让他离开床
                if (peeker.isSleeping()) {
                    peeker.wakeup(false);
                }

                // 传送之前先设置为蹲下
                peeker.setSneaking(true);

                // 如果玩家在附身状态，先退出附身
                if (peeker.getGameMode() == GameMode.SPECTATOR && peeker.getSpectatorTarget() != null) {
                    peeker.setSpectatorTarget(null);
                }

                // 设置为旁观模式
                peeker.setGameMode(GameMode.SPECTATOR);

                // 等待1 tick后再传送
                plugin.getServer().getRegionScheduler().runDelayed(plugin, peeker.getLocation(), delayedTask -> {
                    peeker.teleportAsync(target.getLocation()).thenAccept(success -> {
                        if (!success) {
                            plugin.getMessages().send(peeker, "teleport-failed");
                            endPeek(peeker);
                        } else {
                            startRangeChecker(peeker, target);
                        }
                    });
                }, 2L); // 2 tick 延迟
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("为玩家 %s 切换游戏模式时发生错误", peeker.getName()));
                endPeek(peeker);
            }
        });
    }

    public void restorePlayerState(Player peeker, PeekData data) {
        plugin.getServer().getRegionScheduler().run(plugin, data.getOriginalLocation(), task -> {

            // 如果玩家在附身状态，先退出附身
            if (peeker.getGameMode() == GameMode.SPECTATOR && peeker.getSpectatorTarget() != null) {
                peeker.setSpectatorTarget(null);
            }

            // 在传送前先清除动量
            peeker.setVelocity(new Vector(0, 0, 0));

            peeker.teleportAsync(data.getOriginalLocation()).thenAccept(success -> {
                if (success) {
                    // 传送成功后再改变游戏模式
                    plugin.getServer().getRegionScheduler().run(plugin, data.getOriginalLocation(), modeTask -> {
                        // 再次确保动量为0
                        peeker.setVelocity(new Vector(0, 0, 0));

                        peeker.setGameMode(data.getOriginalGameMode());
                        // 恢复新增状态
                        peeker.setHealth(Math.min(data.getHealth(), 20));
                        peeker.setFoodLevel(data.getFoodLevel());
                        peeker.setSaturation(data.getSaturation());

                        // 清除现有效果并应用保存的效果
                        peeker.getActivePotionEffects().forEach(effect
                                -> peeker.removePotionEffect(effect.getType()));
                        data.getPotionEffects().forEach(effect
                                -> peeker.addPotionEffect(effect));
                    });
                } else {
                    plugin.getLogger().warning(String.format(
                            "无法将玩家 %s 传送回原位置，正在尝试传送到重生点",
                            peeker.getName()
                    ));

                    Location spawnLoc = peeker.getBedSpawnLocation() != null
                            ? peeker.getBedSpawnLocation()
                            : peeker.getWorld().getSpawnLocation();

                    if (spawnLoc != null) {
                        plugin.getServer().getRegionScheduler().run(plugin, spawnLoc, spawnTask -> {
                            peeker.teleportAsync(spawnLoc).thenAccept(spawnSuccess -> {
                                if (spawnSuccess) {
                                    // 传送到重生点成功后再改变游戏模式
                                    plugin.getServer().getRegionScheduler().run(plugin, spawnLoc, modeTask -> {
                                        peeker.setGameMode(data.getOriginalGameMode());
                                    });
                                } else {
                                    plugin.getLogger().severe(String.format(
                                            "无法将玩家 %s 传送到任何安全位置",
                                            peeker.getName()
                                    ));
                                }
                            });
                        });
                    }
                    plugin.getMessages().send(peeker, "teleport-failed");
                }
            });
        });
    }

    public Map<UUID, PeekData> getActivePeeks() {
        return Collections.unmodifiableMap(activePeeks);
    }

    private void logDebug(String message, Object... args) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(String.format(message, args));
        }
    }

    private void playSound(Player player, String soundKey) {
        String soundName = plugin.getConfig().getString("sounds." + soundKey);
        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(String.format("无效的音效名称：%s", soundName));
            }
        }
    }

    private void startRangeChecker(Player peeker, Player target) {
        synchronized (rangeCheckersLock) {
            // 先停止已有的检查器（如果有的话）
            stopRangeChecker(peeker);

            ScheduledTask task = plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                    target.getLocation(), // 使用target的位置而不是peeker的位置
                    scheduledTask -> {
                        if (!target.isOnline()) {
                            endPeek(peeker);
                            return;
                        }

                        try {
                            // 检查观察者是否死亡
                            if (peeker.isDead()) {
                                return;
                            }

                            if (peeker.getWorld().equals(target.getWorld())) {
                                double distance = peeker.getLocation().distance(target.getLocation());
                                if (distance > maxPeekDistance) {
                                    plugin.getMessages().send(peeker, "range-exceeded");
                                    endPeek(peeker);
                                }
                            } else {
                                // 发送跨维度传送提示
                                plugin.getMessages().send(peeker, "target-in-different-world");
                                teleportAndSetGameMode(peeker, target);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning(String.format("距离检查时发生错误：%s", e.getMessage()));
                            endPeek(peeker);
                        }
                    },
                    1L, 10L);

            rangeCheckers.put(peeker.getUniqueId(), task);
        }
    }

    public void stopRangeChecker(Player peeker) {
        synchronized (rangeCheckersLock) {
            ScheduledTask task = rangeCheckers.remove(peeker.getUniqueId());
            if (task != null) {
                task.cancel();
            }
        }
    }

    public void cleanup() {
        synchronized (rangeCheckersLock) {
            new HashMap<>(rangeCheckers).forEach((peeker, task) -> {
                task.cancel();
                rangeCheckers.remove(peeker);
            });
        }
    }

    public void removeActivePeek(Player player) {
        activePeeks.remove(player.getUniqueId());
    }

    public long getPeekerCount(UUID targetUUID) {
        return activePeeks.values().stream()
                .filter(data -> data.getTargetUUID().equals(targetUUID))
                .count();
    }

    public void updateActionBar(Player target) {
        long peekerCount = getPeekerCount(target.getUniqueId());
        if (peekerCount > 0) {
            plugin.getMessages().sendActionBar(target, "being-peeked-actionbar",
                    "count", String.valueOf(peekerCount));
        }
    }

    private void setSelfPeekGameMode(Player peeker) {
        plugin.getServer().getRegionScheduler().run(plugin, peeker.getLocation(), task -> {
            try {
                // 额外的在线检查
                if (!peeker.isOnline()) {
                    plugin.getLogger().warning(String.format("玩家 %s 在设置自我观察模式时已离线", peeker.getName()));
                    endPeek(peeker, false);
                    return;
                }

                // 死亡检查
                if (peeker.isDead()) {
                    plugin.getLogger().warning(String.format("玩家 %s 在设置自我观察模式时已死亡", peeker.getName()));
                    plugin.getMessages().send(peeker, "cannot-peek-while-dead");
                    endPeek(peeker, false);
                    return;
                }

                // 如果玩家在睡觉，先让他离开床
                if (peeker.isSleeping()) {
                    peeker.wakeup(false);
                }

                // 如果玩家在附身状态，先退出附身
                if (peeker.getGameMode() == GameMode.SPECTATOR && peeker.getSpectatorTarget() != null) {
                    peeker.setSpectatorTarget(null);
                }

                // 设置为旁观模式，但不传送
                peeker.setGameMode(GameMode.SPECTATOR);
                
                logDebug("Successfully set self peek game mode for player: %s", peeker.getName());
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("为玩家 %s 设置自我观察模式时发生错误: %s", peeker.getName(), e.getMessage()));
                if (plugin.getConfig().getBoolean("debug", false)) {
                    e.printStackTrace();
                }
                endPeek(peeker);
            }
        });
    }

    private void startSelfRangeChecker(Player peeker, Location originalLocation) {
        synchronized (rangeCheckersLock) {
            // 先停止已有的检查器（如果有的话）
            stopRangeChecker(peeker);

            ScheduledTask task = plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                    originalLocation, // 使用原始位置
                    scheduledTask -> {
                        // 检查玩家是否在线
                        if (!peeker.isOnline()) {
                            logDebug("Player %s went offline during self peek, ending peek", peeker.getName());
                            endPeek(peeker);
                            return;
                        }

                        try {
                            // 检查观察者是否死亡（与普通peek逻辑一致）
                            if (peeker.isDead()) {
                                logDebug("Player %s died during self peek, but continuing to monitor for respawn", peeker.getName());
                                return; // 不立即结束，等待重生处理
                            }

                            // 额外的状态检查
                            PeekData data = activePeeks.get(peeker.getUniqueId());
                            if (data == null) {
                                logDebug("PeekData for player %s is null, stopping range checker", peeker.getName());
                                scheduledTask.cancel();
                                return;
                            }

                            // 检查是否超出距离限制（相对于原始位置）
                            if (peeker.getWorld().equals(originalLocation.getWorld())) {
                                double distance = peeker.getLocation().distance(originalLocation);
                                if (distance > maxPeekDistance) {
                                    logDebug("Player %s exceeded self peek distance: %.2f > %.2f", 
                                            peeker.getName(), distance, maxPeekDistance);
                                    plugin.getMessages().send(peeker, "self-peek-range-exceeded");
                                    endPeek(peeker);
                                }
                            } else {
                                // 如果换了世界，自动结束自我观察
                                logDebug("Player %s changed world during self peek", peeker.getName());
                                plugin.getMessages().send(peeker, "self-peek-world-changed");
                                endPeek(peeker);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning(String.format("自我观察距离检查时发生错误：%s", e.getMessage()));
                            if (plugin.getConfig().getBoolean("debug", false)) {
                                e.printStackTrace();
                            }
                            endPeek(peeker);
                        }
                    },
                    1L, 10L);

            rangeCheckers.put(peeker.getUniqueId(), task);
            logDebug("Started self range checker for player: %s", peeker.getName());
        }
    }
}
