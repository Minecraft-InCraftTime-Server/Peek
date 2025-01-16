package ict.minesunshineone.peek.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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

        // 保存原有状态数据
        config.set("location", data.getOriginalLocation());
        config.set("gamemode", data.getOriginalGameMode().name());
        config.set("target", data.getTargetUUID().toString());
        config.set("startTime", data.getStartTime());

        // 保存新增状态数据
        config.set("health", data.getHealth());
        config.set("food_level", data.getFoodLevel());
        config.set("saturation", data.getSaturation());

        // 保存药水效果
        List<Map<String, Object>> effects = new ArrayList<>();
        for (PotionEffect effect : data.getPotionEffects()) {
            Map<String, Object> effectMap = new HashMap<>();
            effectMap.put("type", effect.getType().getName());
            effectMap.put("duration", effect.getDuration());
            effectMap.put("amplifier", effect.getAmplifier());
            effectMap.put("ambient", effect.isAmbient());
            effectMap.put("particles", effect.hasParticles());
            effectMap.put("icon", effect.hasIcon());
            effects.add(effectMap);
        }
        config.set("potion_effects", effects);

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
            long startTime = config.getLong("startTime");

            // 加载新增状态数据
            double health = config.getDouble("health", 20.0);
            int foodLevel = config.getInt("food_level", 20);
            float saturation = (float) config.getDouble("saturation", 5.0);

            // 加载药水效果
            List<PotionEffect> effects = new ArrayList<>();
            List<Map<?, ?>> effectsList = config.getMapList("potion_effects");
            for (Map<?, ?> effectMap : effectsList) {
                PotionEffectType type = PotionEffectType.getByName((String) effectMap.get("type"));
                if (type != null) {
                    effects.add(new PotionEffect(
                            type,
                            ((Number) effectMap.get("duration")).intValue(),
                            ((Number) effectMap.get("amplifier")).intValue(),
                            (Boolean) effectMap.get("ambient"),
                            (Boolean) effectMap.get("particles"),
                            (Boolean) effectMap.get("icon")
                    ));
                }
            }

            return new PeekData(location, gameMode, targetUUID, startTime, health, foodLevel, saturation, effects);
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("无法加载玩家 %s 的状态: %s", player.getName(), e.getMessage()));
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
