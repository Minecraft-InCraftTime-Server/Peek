package ict.minesunshineone.peek.handler;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;

/**
 * 负责玩家状态的保存和恢复
 * 包括传送、游戏模式、生命值、饥饿度、药水效果等
 */
public class PlayerStateRestorer {

    private final PeekPlugin plugin;

    public PlayerStateRestorer(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 恢复玩家状态（传送回原位并恢复游戏模式等）
     * @param peeker 观察者
     * @param data PeekData 数据
     */
    public void restorePlayerState(Player peeker, PeekData data) {
        plugin.getServer().getRegionScheduler().run(plugin, data.getOriginalLocation(), task -> {

            // 如果玩家在附身状态，先退出附身
            if (peeker.getGameMode() == GameMode.SPECTATOR && peeker.getSpectatorTarget() != null) {
                peeker.setSpectatorTarget(null);
            }

            // 在传送前先清除动量
            peeker.setVelocity(new Vector(0, 0, 0));

            peeker.teleportAsync(data.getOriginalLocation(), TeleportCause.PLUGIN).thenAccept(success -> {
                if (success) {
                    // 传送成功后再改变游戏模式
                    plugin.getServer().getRegionScheduler().run(plugin, data.getOriginalLocation(), modeTask -> {
                        applyRestoredState(peeker, data);
                    });
                } else {
                    handleTeleportFailure(peeker, data);
                }
            });
        });
    }

    /**
     * 处理传送失败的情况
     * @param peeker 观察者
     * @param data PeekData 数据
     */
    public void handleTeleportFailure(Player peeker, PeekData data) {
        plugin.getLogger().warning(String.format(
                "无法将玩家 %s 传送回原位置，正在尝试传送到重生点",
                peeker.getName()
        ));

        Location spawnLoc = resolveSpawnLocation(peeker);

        if (spawnLoc != null) {
            plugin.getServer().getRegionScheduler().run(plugin, spawnLoc, spawnTask -> {
                peeker.teleportAsync(spawnLoc, TeleportCause.PLUGIN).thenAccept(spawnSuccess -> {
                    if (spawnSuccess) {
                        plugin.getServer().getRegionScheduler().run(plugin, spawnLoc, modeTask -> {
                            applyRestoredState(peeker, data);
                        });
                    } else {
                        plugin.getLogger().severe(String.format(
                                "无法将玩家 %s 传送到任何安全位置",
                                peeker.getName()
                        ));
                        forceStateRestoreWithoutTeleport(peeker, data);
                    }
                });
            });
        } else {
            plugin.getLogger().severe(String.format(
                    "未找到可用的重生点，强制恢复玩家 %s 的状态",
                    peeker.getName()
            ));
            forceStateRestoreWithoutTeleport(peeker, data);
        }

        plugin.getMessages().send(peeker, "teleport-failed");
    }

    /**
     * 解析玩家的重生位置
     * @param peeker 玩家
     * @return 重生位置
     */
    public Location resolveSpawnLocation(Player peeker) {
        Location respawnLocation = peeker.getRespawnLocation();
        if (respawnLocation != null) {
            return respawnLocation;
        }

        if (peeker.getWorld() != null) {
            return peeker.getWorld().getSpawnLocation();
        }

        return plugin.getServer().getWorlds().isEmpty()
                ? null
                : plugin.getServer().getWorlds().get(0).getSpawnLocation();
    }

    /**
     * 强制恢复状态（不传送）
     * @param peeker 观察者
     * @param data PeekData 数据
     */
    public void forceStateRestoreWithoutTeleport(Player peeker, PeekData data) {
        plugin.getServer().getRegionScheduler().run(plugin, peeker.getLocation(), modeTask -> {
            applyRestoredState(peeker, data);
        });
    }

    /**
     * 应用恢复的状态
     * @param peeker 观察者
     * @param data PeekData 数据
     */
    public void applyRestoredState(Player peeker, PeekData data) {
        // 再次确保动量为0
        peeker.setVelocity(new Vector(0, 0, 0));

        // 确保观察者附身关系被清除
        if (peeker.getGameMode() == GameMode.SPECTATOR && peeker.getSpectatorTarget() != null) {
            peeker.setSpectatorTarget(null);
        }

        peeker.setGameMode(data.getOriginalGameMode());
        double maxHealth = getMaxHealth(peeker);
        peeker.setHealth(Math.min(data.getHealth(), maxHealth));
        peeker.setFoodLevel(data.getFoodLevel());
        peeker.setSaturation(data.getSaturation());

        // 清除现有效果并应用保存的效果
        peeker.getActivePotionEffects().forEach(effect
                -> peeker.removePotionEffect(effect.getType()));
        data.getPotionEffects().forEach(effect
                -> peeker.addPotionEffect(effect));
    }

    /**
     * 获取玩家的最大生命值
     * @param peeker 玩家
     * @return 最大生命值
     */
    private double getMaxHealth(Player peeker) {
        AttributeInstance instance = peeker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return instance != null ? instance.getValue() : 20.0D;
    }
}
