package ict.minesunshineone.peek.manager;

import java.io.File;
import java.io.IOException;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;

public class StateManager {

    private final PeekPlugin plugin;
    private final YamlConfiguration stateData;
    private final File stateFile;

    public StateManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "player_states.yml");
        this.stateData = YamlConfiguration.loadConfiguration(stateFile);
    }

    public void savePlayerState(Player player, PeekData data) {
        String path = player.getUniqueId().toString();
        stateData.set(path + ".location", data.getOriginalLocation());
        stateData.set(path + ".gamemode", data.getOriginalGameMode().name());
        stateData.set(path + ".target", data.getTargetPlayer().getUniqueId().toString());
        stateData.set(path + ".startTime", data.getStartTime());

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                stateData.save(stateFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save player state: " + e.getMessage());
            }
        });
    }

    public void restorePlayerState(Player player) {
        String path = player.getUniqueId().toString();
        if (!stateData.contains(path)) {
            return;
        }

        Location loc = (Location) stateData.get(path + ".location");
        GameMode gameMode = GameMode.valueOf(stateData.getString(path + ".gamemode"));

        plugin.getServer().getRegionScheduler().execute(plugin, loc, () -> {
            if (!player.isOnline()) {
                return;
            }

            player.teleportAsync(loc).thenAccept(success -> {
                if (success) {
                    player.setGameMode(gameMode);
                    clearPlayerState(player);
                }
            });
        });
    }

    public void clearPlayerState(Player player) {
        String path = player.getUniqueId().toString();
        stateData.set(path, null);

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                stateData.save(stateFile);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to clear player state: " + e.getMessage());
            }
        });
    }
}
