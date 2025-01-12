package ict.minesunshineone.peek.manager;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class StatisticsManager {

    private final PeekPlugin plugin;
    private final Map<UUID, PlayerStats> stats = new ConcurrentHashMap<>();
    private final File statsFile;
    private ScheduledTask autoSaveTask;
    private final boolean enabled;

    public StatisticsManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        this.enabled = plugin.getConfig().getBoolean("statistics.enabled", true);

        if (enabled) {
            loadStats();
            startAutoSave();
        }
    }

    public void recordPeekStart(Player peeker, Player target) {
        if (!enabled) {
            return;
        }

        stats.computeIfAbsent(peeker.getUniqueId(), k -> new PlayerStats())
                .incrementPeekCount();
        stats.computeIfAbsent(target.getUniqueId(), k -> new PlayerStats())
                .incrementPeekedCount();
    }

    public void recordPeekEnd(Player peeker, long durationSeconds) {
        if (!enabled || peeker == null) {
            return;
        }

        if (durationSeconds < 0) {
            plugin.getLogger().warning(String.format(
                    "Negative peek duration detected for player %s: %d",
                    peeker.getName(), durationSeconds
            ));
            durationSeconds = 0;
        }

        stats.computeIfAbsent(peeker.getUniqueId(), k -> new PlayerStats())
                .addPeekDuration(durationSeconds);
    }

    public PlayerStats getPlayerStats(Player player) {
        return stats.getOrDefault(player.getUniqueId(), new PlayerStats());
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            return;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(statsFile);
            for (String uuidStr : config.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    int peekCount = Math.max(0, config.getInt(uuidStr + ".peek_count", 0));
                    int peekedCount = Math.max(0, config.getInt(uuidStr + ".peeked_count", 0));
                    long totalDuration = Math.max(0, config.getLong(uuidStr + ".total_duration", 0));

                    PlayerStats playerStats = new PlayerStats();
                    playerStats.setPeekCount(peekCount);
                    playerStats.setPeekedCount(peekedCount);
                    playerStats.setTotalDuration(totalDuration);
                    stats.put(uuid, playerStats);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in stats file: " + uuidStr);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load stats: " + e.getMessage());
        }
    }

    public void saveStats() {
        if (!enabled) {
            return;
        }

        FileConfiguration config = new YamlConfiguration();
        stats.forEach((uuid, playerStats) -> {
            String path = uuid.toString();
            config.set(path + ".peek_count", playerStats.getPeekCount());
            config.set(path + ".peeked_count", playerStats.getPeekedCount());
            config.set(path + ".total_duration", playerStats.getTotalDuration());
        });

        try {
            config.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save stats: " + e.getMessage());
        }
    }

    private void startAutoSave() {
        int saveInterval = plugin.getConfig().getInt("statistics.save-interval", 600);
        autoSaveTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> saveStats(),
                saveInterval * 50L, // 转换为毫秒
                saveInterval * 50L,
                TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        saveStats();
    }

    public void showStats(Player player) {
        if (!enabled) {
            plugin.getMessages().send(player, "stats-disabled");
            return;
        }

        PlayerStats stats = getPlayerStats(player);
        plugin.getMessages().send(player, "stats-self",
                "peek_count", String.valueOf(stats.getPeekCount()),
                "peeked_count", String.valueOf(stats.getPeekedCount()),
                "peek_duration", String.valueOf(stats.getTotalDuration() / 60));
    }

    public static class PlayerStats {

        private int peekCount;
        private int peekedCount;
        private long totalDuration;

        public void incrementPeekCount() {
            peekCount++;
        }

        public void incrementPeekedCount() {
            peekedCount++;
        }

        public void addPeekDuration(long seconds) {
            totalDuration += seconds;
        }

        // Getters and setters
        public int getPeekCount() {
            return peekCount;
        }

        public void setPeekCount(int count) {
            this.peekCount = count;
        }

        public int getPeekedCount() {
            return peekedCount;
        }

        public void setPeekedCount(int count) {
            this.peekedCount = count;
        }

        public long getTotalDuration() {
            return totalDuration;
        }

        public void setTotalDuration(long duration) {
            this.totalDuration = duration;
        }
    }
}
