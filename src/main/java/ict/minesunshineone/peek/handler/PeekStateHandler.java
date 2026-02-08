package ict.minesunshineone.peek.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;
import ict.minesunshineone.peek.util.PlayerStateUtil;

/**
 * 核心 Peek 状态管理器
 * 负责协调 BossBarHandler、RangeChecker 和 PlayerStateRestorer
 */
public class PeekStateHandler {

    private final PeekPlugin plugin;
    private final Map<UUID, PeekData> activePeeks = new HashMap<>();

    // 委托的处理器
    private final BossBarHandler bossBarHandler;
    private final RangeChecker rangeChecker;
    private final PlayerStateRestorer stateRestorer;

    public PeekStateHandler(PeekPlugin plugin) {
        this.plugin = plugin;
        this.bossBarHandler = new BossBarHandler(plugin);
        this.rangeChecker = new RangeChecker(plugin);
        this.stateRestorer = new PlayerStateRestorer(plugin);
    }

    /**
     * 开始 Peek 另一个玩家
     */
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
        final PeekData data;
        synchronized (activePeeks) {
            if (activePeeks.containsKey(peeker.getUniqueId())) {
                plugin.getMessages().send(peeker, "already-peeking");
                return;
            }

            logDebug("Starting peek: %s -> %s", peeker.getName(), target.getName());
            data = new PeekData(
                    peeker.getLocation().clone(),
                    peeker.getGameMode(),
                    target.getUniqueId(),
                    System.currentTimeMillis(),
                    peeker.getHealth(),
                    peeker.getFoodLevel(),
                    peeker.getSaturation(),
                    peeker.getActivePotionEffects());

            activePeeks.put(peeker.getUniqueId(), data);
        }

        plugin.getStateManager().savePlayerState(peeker, data);
        plugin.getStatisticsManager().recordPeekStart(peeker, target);

        teleportAndSetGameMode(peeker, target);

        // 检查是否静默 peek（有 bypass 权限）
        boolean silentPeek = plugin.getTargetHandler().shouldSilentPeek(peeker);

        // 发送消息给观察者
        plugin.getMessages().send(peeker, "peek-start", "player", target.getName());

        // 只有非静默模式才通知目标
        if (!silentPeek) {
            plugin.getMessages().send(target, "being-peeked", "player", peeker.getName());
            playSound(target, "start-peek");
            updateActionBar(target);
        }
    }

    /**
     * 开始自我观察模式（原地进入观察者模式）
     */
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
                        peeker.getActivePotionEffects());

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
                // 创建 BossBar 并启动自我观察距离检查器
                bossBarHandler.createSelfPeekBossBar(peeker, plugin.getMessages().get("self-peek-origin", "原点"));
                startSelfRangeChecker(peeker, originalLocation);

                logDebug("Self peek started successfully for player: %s", peeker.getName());

            } catch (Exception e) {
                plugin.getLogger()
                        .warning(String.format("启动自我观察时发生错误，玩家: %s，错误: %s", peeker.getName(), e.getMessage()));
                if (plugin.getConfig().getBoolean("debug", false)) {
                    e.printStackTrace();
                }

                // 清理可能的残留状态
                activePeeks.remove(peeker.getUniqueId());
                // 通知玩家发生错误
                rangeChecker.stopRangeChecker(peeker);
                plugin.getMessages().send(peeker, "command-error");
            }
        }
    }

    /**
     * 结束 Peek 状态
     */
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

            // 停止距离检查器并移除 BossBar
            stopRangeCheckerAndBossBar(peeker);

            if (shouldRestore && peeker.isOnline()) {
                if (peeker.isDead()) {
                    startRespawnWatcher(peeker, data);
                } else {
                    // 玩家未死亡，直接恢复状态
                    stateRestorer.restorePlayerState(peeker, data);
                }
            }

            if (shouldRestore && !peeker.isDead()) {
                plugin.getStateManager().clearPlayerState(peeker);
            }

            // 发送消息
            if (peeker.isOnline()) {
                plugin.getMessages().send(peeker, "peek-end");
            }

            // 检查是否是自我观察模式
            boolean isSelfPeek = target != null && target.getUniqueId().equals(peeker.getUniqueId());
            boolean silentPeek = peeker.isOnline() && plugin.getTargetHandler().shouldSilentPeek(peeker);

            // 非自我观察且非静默模式才通知目标
            if (target != null && target.isOnline() && !isSelfPeek && !silentPeek) {
                plugin.getMessages().send(target, "peek-end-target", "player", peeker.getName());
                playSound(target, "end-peek");
            }

            activePeeks.remove(peeker.getUniqueId());

            if (target != null && target.isOnline() && !isSelfPeek && !silentPeek) {
                updateActionBar(target);
            }
        }
    }

    public void endPeek(Player peeker) {
        endPeek(peeker, true);
    }

    // ==================== 私有方法 ====================

    private void teleportAndSetGameMode(Player peeker, Player target) {
        final Runnable onFailed = () -> {
            plugin.getMessages().send(peeker, "teleport-failed");
            endPeek(peeker);
        };

        // 先切换游戏模式
        final boolean firstRoundScheduled = peeker.getScheduler().execute(plugin, () -> {
            // 清理骑乘状态，准备进入旁观者模式
            PlayerStateUtil.prepareForSpectatorMode(peeker);

            // 设置为旁观模式
            peeker.setGameMode(GameMode.SPECTATOR);

            // 等待2 tick后再传送
            final boolean secondRoundScheduled = peeker.getScheduler().execute(plugin, () -> {
                peeker.teleportAsync(target.getLocation(), TeleportCause.PLUGIN).thenAccept(success -> {
                    if (!success) {
                        onFailed.run();
                    } else {
                        // 传送成功后，使用玩家的实体调度器确保在正确线程执行
                        bossBarHandler.createDistanceBossBar(peeker, target);
                        startNormalRangeChecker(peeker, target);
                    }
                });
            }, onFailed, 2L); // 2 tick 延迟

            if (!secondRoundScheduled) {
                onFailed.run();
            }
        }, onFailed, 1L);

        if (!firstRoundScheduled) {
            onFailed.run();
        }
    }

    private void setSelfPeekGameMode(Player peeker) {
        peeker.getScheduler().execute(plugin, () -> {
            // 这里如果玩家离线了调度器会自动退役
            /*if (!peeker.isOnline()) {
                plugin.getLogger().warning(String.format("玩家 %s 在设置自我观察模式时已离线", peeker.getName()));
                endPeek(peeker, false);
                return;
            }*/

            if (peeker.isDead()) {
                plugin.getSLF4JLogger().warn("玩家 {} 在设置自我观察模式时已死亡", peeker.getName());
                plugin.getMessages().send(peeker, "cannot-peek-while-dead");
                endPeek(peeker, false);
                return;
            }

            // 清理骑乘状态，准备进入旁观者模式
            PlayerStateUtil.prepareForSpectatorMode(peeker);

            peeker.setGameMode(GameMode.SPECTATOR);

            logDebug("Successfully set self peek game mode for player: %s", peeker.getName());
        }, () -> {
            plugin.getSLF4JLogger().warn("玩家 {} 在设置自我观察模式时已离线", peeker.getName());
            endPeek(peeker, false);
        }, 1L);
    }

    private void startNormalRangeChecker(Player peeker, Player target) {
        rangeChecker.startRangeChecker(peeker, target,
                // 超出范围时
                () -> {
                    plugin.getMessages().send(peeker, "range-exceeded");
                    endPeek(peeker);
                },
                // 目标离线时
                () -> endPeek(peeker),
                // 距离更新时
                (distance) -> {
                    PeekData data = activePeeks.get(peeker.getUniqueId());
                    bossBarHandler.updateDistanceBossBar(peeker, distance, data);
                },
                // 不同世界时
                () -> {
                    plugin.getMessages().send(peeker, "target-in-different-world");
                    teleportAndSetGameMode(peeker, target);
                });
    }

    private void startSelfRangeChecker(Player peeker, Location originalLocation) {
        String selfPeekLabel = plugin.getMessages().get("self-peek-origin", "原点");
        rangeChecker.startSelfRangeChecker(peeker, originalLocation,
                // 获取 Peek 数据
                () -> activePeeks.get(peeker.getUniqueId()),
                // 超出自我观察范围时
                () -> {
                    plugin.getMessages().send(peeker, "self-peek-range-exceeded");
                    endPeek(peeker);
                },
                // 世界改变时
                () -> {
                    plugin.getMessages().send(peeker, "self-peek-world-changed");
                    endPeek(peeker);
                },
                // 距离更新时（用于更新 BossBar）
                (distance) -> bossBarHandler.updateDistanceBossBar(peeker, distance, selfPeekLabel),
                // 发生错误时
                () -> endPeek(peeker));
    }

    private void stopRangeCheckerAndBossBar(Player peeker) {
        rangeChecker.stopRangeChecker(peeker);
        bossBarHandler.safeRemoveDistanceBossBar(peeker);
    }

    private void startRespawnWatcher(Player peeker, PeekData data) {
        plugin.getServer().getRegionScheduler().runAtFixedRate(plugin,
                peeker.getLocation(),
                task -> {
                    if (!peeker.isOnline()) {
                        task.cancel();
                        return;
                    }

                    if (!peeker.isDead()) {
                        task.cancel();
                        stateRestorer.restorePlayerState(peeker, data);
                        plugin.getStateManager().clearPlayerState(peeker);
                        plugin.getMessages().send(peeker, "peek-end-respawn");
                    }
                },
                1L, 20L);
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

    private void logDebug(String message, Object... args) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info(String.format(message, args));
        }
    }

    // ==================== 公共查询方法 ====================

    public Map<UUID, PeekData> getActivePeeks() {
        return Collections.unmodifiableMap(activePeeks);
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

    /**
     * 清理所有资源
     */
    public void cleanup() {
        rangeChecker.cleanup();
        bossBarHandler.cleanup();
    }

    // ==================== Getter 方法 ====================

    public BossBarHandler getBossBarHandler() {
        return bossBarHandler;
    }

    public RangeChecker getRangeChecker() {
        return rangeChecker;
    }

    public PlayerStateRestorer getStateRestorer() {
        return stateRestorer;
    }
}
