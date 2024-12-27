package ict.minesunshineone.peek;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class OfflinePeekManager {

    private final PeekPlugin plugin;
    private final File pendingPeeksFile;
    private YamlConfiguration pendingPeeks;
    private static final long CLEANUP_INTERVAL = 30 * 60 * 1000;
    private static final long MAX_OFFLINE_TIME = 24 * 60 * 60 * 1000;

    public OfflinePeekManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.pendingPeeksFile = new File(plugin.getDataFolder(), "pending_peeks.yml");
        loadPendingPeeks();
        validateAndCleanData();
        startCleanupTask();
    }

    private void loadPendingPeeks() {
        if (!pendingPeeksFile.exists()) {
            try {
                pendingPeeksFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("无法创建未完成观察状态文件: %s", e.getMessage()));
            }
        }
        pendingPeeks = YamlConfiguration.loadConfiguration(pendingPeeksFile);
    }

    private void startCleanupTask() {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> cleanupOldData(),
                CLEANUP_INTERVAL,
                CLEANUP_INTERVAL,
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
    }

    private void cleanupOldData() {
        long now = System.currentTimeMillis();
        boolean needsSave = false;

        for (String key : pendingPeeks.getKeys(false)) {
            long startTime = pendingPeeks.getLong(key + ".startTime");
            if (now - startTime > MAX_OFFLINE_TIME) {
                pendingPeeks.set(key, null);
                needsSave = true;
            }
        }

        if (needsSave) {
            try {
                pendingPeeks.save(pendingPeeksFile);
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("清理过期数据时保存失败: %s", e.getMessage()));
            }
        }
    }

    public void saveOfflinePlayerState(Player player, PeekData data) {
        UUID uuid = player.getUniqueId();
        String uuidString = uuid.toString();

        // 直接保存到文件
        pendingPeeks.set(uuidString + ".location.world", data.getOriginalLocation().getWorld().getName());
        pendingPeeks.set(uuidString + ".location.x", data.getOriginalLocation().getX());
        pendingPeeks.set(uuidString + ".location.y", data.getOriginalLocation().getY());
        pendingPeeks.set(uuidString + ".location.z", data.getOriginalLocation().getZ());
        pendingPeeks.set(uuidString + ".location.yaw", data.getOriginalLocation().getYaw());
        pendingPeeks.set(uuidString + ".location.pitch", data.getOriginalLocation().getPitch());
        pendingPeeks.set(uuidString + ".gamemode", data.getOriginalGameMode().name());
        pendingPeeks.set(uuidString + ".startTime", data.getStartTime());

        try {
            pendingPeeks.save(pendingPeeksFile);
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("保存玩家 %s 的观察状态失败: %s",
                    player.getName(), e.getMessage()));
        }
    }

    public void checkAndRestorePlayer(Player player) {
        String uuidString = player.getUniqueId().toString();

        if (pendingPeeks.contains(uuidString)) {
            // 直接从文件读取并恢复
            String worldName = pendingPeeks.getString(uuidString + ".location.world");
            if (worldName == null) {
                return;
            }

            World world = plugin.getServer().getWorld(worldName);
            if (world != null) {
                Location loc = new Location(
                        world,
                        pendingPeeks.getDouble(uuidString + ".location.x"),
                        pendingPeeks.getDouble(uuidString + ".location.y"),
                        pendingPeeks.getDouble(uuidString + ".location.z"),
                        (float) pendingPeeks.getDouble(uuidString + ".location.yaw"),
                        (float) pendingPeeks.getDouble(uuidString + ".location.pitch")
                );

                GameMode gameMode = GameMode.valueOf(pendingPeeks.getString(uuidString + ".gamemode"));
                long startTime = pendingPeeks.getLong(uuidString + ".startTime");

                handleRestoreAndCleanup(player, gameMode, loc, startTime);
            }
        }
    }

    private void handleRestoreAndCleanup(Player player, GameMode gameMode, Location location, long startTime) {
        plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            player.setGameMode(gameMode);
            player.teleportAsync(location).thenAccept(result -> {
                if (result) {
                    if (plugin.getStatistics() != null) {
                        long duration = (System.currentTimeMillis() - startTime) / 1000;
                        plugin.getStatistics().recordPeekDuration(player, duration);
                    }
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacyAmpersand().deserialize(plugin.getMessages().get("peek-end-offline")));

                    // 删除数据
                    String uuidString = player.getUniqueId().toString();
                    pendingPeeks.set(uuidString, null);
                    try {
                        pendingPeeks.save(pendingPeeksFile);
                    } catch (IOException e) {
                        plugin.getLogger().warning(String.format("无法删除已恢复的观察状态: %s", e.getMessage()));
                    }
                }
            });
        });
    }

    private void validateAndCleanData() {
        boolean needsSave = false;
        for (String key : pendingPeeks.getKeys(false)) {
            if (!isValidPeekData(key)) {
                pendingPeeks.set(key, null);
                needsSave = true;
                plugin.getLogger().warning(String.format("清理无效的观察数据: %s", key));
            }
        }

        if (needsSave) {
            try {
                pendingPeeks.save(pendingPeeksFile);
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("清理无效数据时保存失败: %s", e.getMessage()));
            }
        }
    }

    private boolean isValidPeekData(String key) {
        return pendingPeeks.contains(key + ".location.world")
                && pendingPeeks.contains(key + ".location.x")
                && pendingPeeks.contains(key + ".location.y")
                && pendingPeeks.contains(key + ".location.z")
                && pendingPeeks.contains(key + ".gamemode")
                && pendingPeeks.contains(key + ".startTime");
    }
}
