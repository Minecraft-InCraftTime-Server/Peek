package ict.minesunshineone.peek.handler;

import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.util.Vector;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.data.PeekData;
import ict.minesunshineone.peek.util.PlayerStateUtil;

/**
 * 负责玩家状态的保存和恢复
 * 包括传送、游戏模式、生命值、饥饿度、药水效果等
 */
public class PlayerStateRestorer {
    private static final Vector ZERO_VECTOR = new Vector(0, 0, 0);

    private final PeekPlugin plugin;

    public PlayerStateRestorer(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 恢复玩家状态（传送回原位并恢复游戏模式等）
     * 
     * @param peeker 观察者
     * @param data   PeekData 数据
     */
    public void restorePlayerState(Player peeker, PeekData data) {
        final Runnable onFailed = () -> handleTeleportFailure(peeker, data);

        final boolean scheduled = peeker.getScheduler().execute(plugin, () -> {

            // 强制清理任何骑乘/附身状态，防止卡在旁观者模式
            PlayerStateUtil.forceExitRidingState(peeker);

            // 在传送前先清除动量
            peeker.setVelocity(ZERO_VECTOR);

            peeker.teleportAsync(data.getOriginalLocation(), TeleportCause.PLUGIN).thenAccept(success -> {
                if (success) {
                    // teleportAsync 回调线程不确定，使用实体调度器确保线程安全
                    peeker.getScheduler().run(plugin,
                            scheduledTask -> applyRestoredState(peeker, data),
                            () -> plugin.getLogger().warning(
                                    String.format("玩家 %s 在传送恢复后离线", peeker.getName())));
                } else {
                    onFailed.run();
                }
            });
        }, onFailed, 1L);

        if (!scheduled) {
            onFailed.run();
        }
    }

    /**
     * 处理传送失败的情况
     * 
     * @param peeker 观察者
     * @param data   PeekData 数据
     */
    public void handleTeleportFailure(Player peeker, PeekData data) {
        plugin.getLogger().warning(String.format(
                "无法将玩家 %s 传送回原位置，正在尝试传送到重生点",
                peeker.getName()));

        final Location spawnLoc = resolveSpawnLocation(peeker);

        final Runnable onFailed = () -> {
            plugin.getLogger().severe(String.format(
                    "未找到可用的重生点，强制恢复玩家 %s 的状态",
                    peeker.getName()));
            forceStateRestoreWithoutTeleport(peeker, data);
        };

        if (spawnLoc != null) {
            final boolean scheduled = peeker.getScheduler().execute(plugin, () -> {
                peeker.teleportAsync(spawnLoc, TeleportCause.PLUGIN).thenAccept(spawnSuccess -> {
                    if (spawnSuccess) {
                        // teleportAsync 回调线程不确定，使用实体调度器确保线程安全
                        peeker.getScheduler().run(plugin,
                                scheduledTask -> applyRestoredState(peeker, data),
                                () -> plugin.getLogger().warning(
                                        String.format("玩家 %s 在重生点传送恢复后离线", peeker.getName())));
                    } else {
                        onFailed.run();
                    }
                });
            }, onFailed, 1L);

            if (!scheduled) {
                onFailed.run();
            }
        } else {
            onFailed.run();
        }

        plugin.getMessages().send(peeker, "teleport-failed");
    }

    /**
     * 解析玩家的重生位置
     * 
     * @param peeker 玩家
     * @return 重生位置
     */
    public Location resolveSpawnLocation(Player peeker) {
        Location respawnLocation = peeker.getRespawnLocation();

        if (respawnLocation != null) {
            return respawnLocation;
        }

        return peeker.getWorld().getSpawnLocation();

    }

    /**
     * 强制恢复状态（不传送）
     * 
     * @param peeker 观察者
     * @param data   PeekData 数据
     */
    public void forceStateRestoreWithoutTeleport(Player peeker, PeekData data) {
        // 可能从 teleportAsync 回调（线程不确定）调用
        // 使用实体调度器确保在正确的区域线程上执行
        if (peeker.isOnline()) {
            peeker.getScheduler().run(plugin,
                    scheduledTask -> applyRestoredState(peeker, data),
                    () -> plugin.getLogger().warning(
                            String.format("玩家 %s 在强制恢复状态时离线", peeker.getName())));
        } else {
            plugin.getLogger().warning(
                    String.format("玩家 %s 已离线，无法强制恢复状态", peeker.getName()));
        }
    }

    /**
     * 应用恢复的状态
     * 
     * @param peeker 观察者
     * @param data   PeekData 数据
     */
    public void applyRestoredState(Player peeker, PeekData data) {
        // 再次确保动量为0
        peeker.setVelocity(new Vector(0, 0, 0));

        // 再次强制清理骑乘/附身状态，确保万无一失
        PlayerStateUtil.forceExitRidingState(peeker);

        peeker.setGameMode(data.getOriginalGameMode());
        double maxHealth = getMaxHealth(peeker);
        peeker.setHealth(Math.min(data.getHealth(), maxHealth));
        peeker.setFoodLevel(data.getFoodLevel());
        peeker.setSaturation(data.getSaturation());

        // 清除现有效果并应用保存的效果
        peeker.getActivePotionEffects().forEach(effect -> peeker.removePotionEffect(effect.getType()));
        data.getPotionEffects().forEach(effect -> peeker.addPotionEffect(effect));
    }

    /**
     * 获取玩家的最大生命值
     * 
     * @param peeker 玩家
     * @return 最大生命值
     */
    private double getMaxHealth(Player peeker) {
        AttributeInstance instance = peeker.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return instance != null ? instance.getValue() : 20.0D;
    }
}
