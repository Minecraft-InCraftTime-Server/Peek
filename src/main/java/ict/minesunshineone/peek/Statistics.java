package ict.minesunshineone.peek;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class Statistics {

    private final PeekPlugin plugin;
    private final File statsFile;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    public Statistics(PeekPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadStats();
        startAutoSave();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
        for (String uuidStr : config.getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            int peekCount = config.getInt(uuidStr + ".peek_count", 0);
            int peekedCount = config.getInt(uuidStr + ".peeked_count", 0);
            long peekDuration = config.getLong(uuidStr + ".peek_duration", 0);
            stats.put(uuid, new PlayerStats(peekCount, peekedCount, peekDuration));
        }
    }

    public void saveStats() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
        // 保存所有统计数据
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            String uuidStr = entry.getKey().toString();
            PlayerStats playerStats = entry.getValue();
            config.set(uuidStr + ".peek_count", playerStats.getPeekCount());
            config.set(uuidStr + ".peeked_count", playerStats.getPeekedCount());
            config.set(uuidStr + ".peek_duration", playerStats.getPeekDuration());
        }

        try {
            config.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("保存统计数据时发生错误: %s", e.getMessage()));
        }
    }

    private void startAutoSave() {
        int interval = plugin.getConfig().getInt("statistics.save-interval", 300);
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> saveStats(),
                interval * 20L,
                interval * 20L,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordPeekStart(Player peeker, Player target) {
        getPlayerStats(peeker.getUniqueId()).incrementPeekCount();
        getPlayerStats(target.getUniqueId()).incrementPeekedCount();
    }

    public void recordPeekDuration(Player player, long durationSeconds) {
        getPlayerStats(player.getUniqueId()).addPeekDuration(durationSeconds);
    }

    private PlayerStats getPlayerStats(UUID uuid) {
        return stats.computeIfAbsent(uuid, k -> new PlayerStats(0, 0, 0));
    }

    public PlayerStats getStats(Player player) {
        return getPlayerStats(player.getUniqueId());
    }

    public static class PlayerStats {

        private int peekCount;
        private int peekedCount;
        private long peekDuration;  // 以秒为单位

        public PlayerStats(int peekCount, int peekedCount, long peekDuration) {
            this.peekCount = peekCount;
            this.peekedCount = peekedCount;
            this.peekDuration = peekDuration;
        }

        public void incrementPeekCount() {
            peekCount++;
        }

        public void incrementPeekedCount() {
            peekedCount++;
        }

        public void addPeekDuration(long seconds) {
            peekDuration += seconds;
        }

        public int getPeekCount() {
            return peekCount;
        }

        public int getPeekedCount() {
            return peekedCount;
        }

        public long getPeekDuration() {
            return peekDuration;
        }
    }
}
