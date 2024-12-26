package ict.minesunshineone.peek;

import java.io.File;
import java.io.IOException;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class OfflinePeekManager {

    private final PeekPlugin plugin;
    private final File pendingPeeksFile;
    private YamlConfiguration pendingPeeks;

    public OfflinePeekManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.pendingPeeksFile = new File(plugin.getDataFolder(), "pending_peeks.yml");
        loadPendingPeeks();
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

    public void saveOfflinePlayerState(Player player, PeekData data) {
        String uuid = player.getUniqueId().toString();
        Location loc = data.getOriginalLocation();

        pendingPeeks.set(uuid + ".location.world", loc.getWorld().getName());
        pendingPeeks.set(uuid + ".location.x", loc.getX());
        pendingPeeks.set(uuid + ".location.y", loc.getY());
        pendingPeeks.set(uuid + ".location.z", loc.getZ());
        pendingPeeks.set(uuid + ".location.yaw", loc.getYaw());
        pendingPeeks.set(uuid + ".location.pitch", loc.getPitch());
        pendingPeeks.set(uuid + ".gamemode", data.getOriginalGameMode().toString());
        pendingPeeks.set(uuid + ".startTime", data.getStartTime());
        pendingPeeks.set(uuid + ".playerName", player.getName());

        try {
            pendingPeeks.save(pendingPeeksFile);
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("无法保存未完成观察状态: %s", e.getMessage()));
        }
    }

    public void checkAndRestorePlayer(Player player) {
        String uuid = player.getUniqueId().toString();
        if (pendingPeeks.contains(uuid)) {
            String worldName = pendingPeeks.getString(uuid + ".location.world");
            if (worldName == null) {
                plugin.getLogger().warning("找不到世界名称");
                return;
            }
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning(String.format("找不到世界: %s", worldName));
                return;
            }
            Location loc = new Location(
                    world,
                    pendingPeeks.getDouble(uuid + ".location.x"),
                    pendingPeeks.getDouble(uuid + ".location.y"),
                    pendingPeeks.getDouble(uuid + ".location.z"),
                    (float) pendingPeeks.getDouble(uuid + ".location.yaw"),
                    (float) pendingPeeks.getDouble(uuid + ".location.pitch")
            );

            GameMode gameMode = GameMode.valueOf(pendingPeeks.getString(uuid + ".gamemode"));
            long startTime = pendingPeeks.getLong(uuid + ".startTime");

            // 使用区域调度器安全地恢复玩家状态
            plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
                player.setGameMode(gameMode);
                player.teleportAsync(loc).thenAccept(result -> {
                    if (result) {
                        // 记录观察时长
                        if (plugin.getStatistics() != null) {
                            long duration = (System.currentTimeMillis() - startTime) / 1000;
                            plugin.getStatistics().recordPeekDuration(player, duration);
                        }
                        // 使用 Adventure API 发送消息
                        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                                .legacyAmpersand().deserialize(plugin.getMessages().get("peek-end-offline")));
                    }
                });
            });

            // 恢复完成后删除数据
            pendingPeeks.set(uuid, null);
            try {
                pendingPeeks.save(pendingPeeksFile);
            } catch (IOException e) {
                plugin.getLogger().warning(String.format("无法删除已恢复的观察状态: %s", e.getMessage()));
            }
        }
    }
}
