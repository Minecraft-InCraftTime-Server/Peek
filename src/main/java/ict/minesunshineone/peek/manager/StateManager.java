package ict.minesunshineone.peek.manager;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;

public class StateManager {

    private final PeekPlugin plugin;
    private final File statesDir;

    public StateManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.statesDir = new File(plugin.getDataFolder(), "states");
        if (!statesDir.exists()) {
            statesDir.mkdirs();
        }
    }

    public void savePlayerState(Player player, PeekData data) {
        File stateFile = new File(statesDir, player.getUniqueId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        // 保存状态数据
        config.set("location", data.getOriginalLocation());
        config.set("gamemode", data.getOriginalGameMode().name());
        config.set("target", data.getTargetPlayer().getUniqueId().toString());
        config.set("startTime", data.getStartTime());

        try {
            config.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("无法保存玩家 %s 的状态", player.getName()));
        }
    }

    public PeekData getPlayerState(Player player) {
        File stateFile = new File(statesDir, player.getUniqueId() + ".yml");
        if (!stateFile.exists()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        try {
            Location location = (Location) config.get("location");
            GameMode gameMode = GameMode.valueOf(config.getString("gamemode"));
            UUID targetUUID = UUID.fromString(config.getString("target"));
            Player target = plugin.getServer().getPlayer(targetUUID);
            long startTime = config.getLong("startTime");

            return new PeekData(location, gameMode, target, startTime);
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("无法加载玩家 %s 的状态", player.getName()));
            return null;
        }
    }

    public void clearPlayerState(Player player) {
        File stateFile = new File(statesDir, player.getUniqueId() + ".yml");
        if (stateFile.exists()) {
            stateFile.delete();
        }
    }
}
