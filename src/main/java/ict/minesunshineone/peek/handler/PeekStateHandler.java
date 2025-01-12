package ict.minesunshineone.peek.handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PeekStateHandler {

    private final PeekPlugin plugin;
    private final Map<Player, PeekData> activePeeks = new HashMap<>();
    private final Map<Player, ScheduledTask> rangeCheckers = new HashMap<>();
    private final Object rangeCheckersLock = new Object();
    private final double maxPeekDistance;

    public PeekStateHandler(PeekPlugin plugin) {
        this.plugin = plugin;
        this.maxPeekDistance = plugin.getConfig().getDouble("limits.max-peek-distance", 50.0);
    }

    public void startPeek(Player peeker, Player target) {
        if (peeker == null || target == null) {
            plugin.getLogger().warning("Attempted to start peek with null player");
            return;
        }

        // 防止并发修改
        synchronized (activePeeks) {
            if (activePeeks.containsKey(peeker)) {
                plugin.getMessages().send(peeker, "already-peeking");
                return;
            }

            logDebug("Starting peek: %s -> %s", peeker.getName(), target.getName());
            PeekData data = new PeekData(
                    peeker.getLocation().clone(),
                    peeker.getGameMode(),
                    target,
                    System.currentTimeMillis()
            );

            activePeeks.put(peeker, data);
            plugin.getStateManager().savePlayerState(peeker, data);
            plugin.getStatisticsManager().recordPeekStart(peeker, target);

            teleportAndSetGameMode(peeker, target);
        }

        // 发送消息
        plugin.getMessages().send(peeker, "peek-start", "player", target.getName());
        plugin.getMessages().send(target, "being-peeked", "player", peeker.getName());

        // 播放声音
        playSound(target, "start-peek");
    }

    public void endPeek(Player peeker, boolean shouldRestore) {
        if (peeker == null) {
            plugin.getLogger().warning("Attempted to end peek with null player");
            return;
        }

        synchronized (activePeeks) {
            PeekData data = activePeeks.get(peeker);
            if (data == null) {
                return;
            }

            if (data.isExiting()) {
                return;
            }

            data.setExiting(true);
            Player target = data.getTargetPlayer();

            // 记录统计
            long duration = (System.currentTimeMillis() - data.getStartTime()) / 1000;
            plugin.getStatisticsManager().recordPeekEnd(peeker, duration);

            // 设置冷却
            plugin.getCooldownManager().setCooldown(peeker);

            // 停止距离检查器
            stopRangeChecker(peeker);

            if (shouldRestore && peeker.isOnline()) {
                restorePlayerState(peeker, data);
            }

            if (shouldRestore) {
                plugin.getStateManager().clearPlayerState(peeker);
            }

            // 发送消息
            if (peeker.isOnline()) {
                plugin.getMessages().send(peeker, "peek-end");
            }
            if (target != null && target.isOnline()) {
                plugin.getMessages().send(target, "peek-end-target", "player", peeker.getName());
                playSound(target, "end-peek");
            }

            // 最后才移除活动peek
            activePeeks.remove(peeker);
        }
    }

    public void endPeek(Player peeker) {
        endPeek(peeker, true);
    }

    private void teleportAndSetGameMode(Player peeker, Player target) {
        // 先切换游戏模式
        plugin.getServer().getRegionScheduler().run(plugin, peeker.getLocation(), task -> {
            try {
                peeker.setGameMode(GameMode.SPECTATOR);

                // 切换完成后再传送
                peeker.teleportAsync(target.getLocation()).thenAccept(success -> {
                    if (!success) {
                        plugin.getMessages().send(peeker, "teleport-failed");
                        endPeek(peeker);
                    } else {
                        // 传送成功后再启动距离检查器
                        startRangeChecker(peeker, target);
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to change gamemode for " + peeker.getName());
                endPeek(peeker);
            }
        });
    }

    private void restorePlayerState(Player peeker, PeekData data) {
        plugin.getServer().getRegionScheduler().run(plugin, data.getOriginalLocation(), task -> {
            peeker.teleportAsync(data.getOriginalLocation()).thenAccept(success -> {
                if (success) {
                    peeker.setGameMode(data.getOriginalGameMode());
                } else {
                    plugin.getLogger().warning(String.format(
                            "Failed to teleport %s back to original location, using spawn location",
                            peeker.getName()
                    ));

                    Location spawnLoc = peeker.getBedSpawnLocation() != null
                            ? peeker.getBedSpawnLocation()
                            : peeker.getWorld().getSpawnLocation();

                    plugin.getServer().getRegionScheduler().run(plugin, spawnLoc, spawnTask -> {
                        peeker.teleportAsync(spawnLoc).thenAccept(spawnSuccess -> {
                            if (spawnSuccess) {
                                peeker.setGameMode(data.getOriginalGameMode());
                            } else {
                                plugin.getLogger().severe(String.format(
                                        "Failed to teleport %s to any safe location",
                                        peeker.getName()
                                ));
                            }
                        });
                    });
                    plugin.getMessages().send(peeker, "teleport-failed");
                }
            });
        });
    }

    public Map<Player, PeekData> getActivePeeks() {
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
                plugin.getLogger().warning("Invalid sound name: " + soundName);
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
                        if (!peeker.isOnline() || !target.isOnline()) {
                            endPeek(peeker);
                            return;
                        }

                        try {
                            // 如果在同一个世界才检查距离
                            if (peeker.getWorld().equals(target.getWorld())) {
                                double distance = peeker.getLocation().distance(target.getLocation());
                                if (distance > maxPeekDistance) {
                                    plugin.getMessages().send(peeker, "range-exceeded");
                                    endPeek(peeker);
                                }
                            } // 不同世界时自动跟随传送
                            else {
                                teleportAndSetGameMode(peeker, target);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error in range checker: " + e.getMessage());
                            endPeek(peeker);
                        }
                    },
                    1L, 100L);

            rangeCheckers.put(peeker, task);
        }
    }

    public void stopRangeChecker(Player peeker) {
        synchronized (rangeCheckersLock) {
            ScheduledTask task = rangeCheckers.remove(peeker);
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
        activePeeks.remove(player);
    }
}