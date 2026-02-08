package ict.minesunshineone.peek.util;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * 玩家状态工具类
 * 封装骑乘状态清理等通用操作
 */
public final class PlayerStateUtil {

    private PlayerStateUtil() {
        // 工具类不允许实例化
    }

    /**
     * 强制退出骑乘/附身状态
     * 通过模拟shift并调用leaveVehicle确保彻底解除骑乘关系
     * 
     * @param player 玩家
     */
    public static void forceExitRidingState(Player player) {
        // 先模拟按下shift键
        player.setSneaking(true);

        // 如果在旁观者模式下附身了其他实体，先退出附身
        if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }

        // 如果玩家正在骑乘载具，让玩家下马
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }

        // 如果玩家身上有乘客，让乘客下来
        if (!player.getPassengers().isEmpty()) {
            player.eject();
        }

        // 恢复正常状态，避免永久潜行
        player.setSneaking(false);
    }

    /**
     * 准备进入旁观者模式前的清理工作
     * 包括唤醒、退出骑乘等
     * 
     * @param player 玩家
     */
    public static void prepareForSpectatorMode(Player player) {
        // 如果玩家在睡觉，先让他离开床
        if (player.isSleeping()) {
            player.wakeup(false);
        }

        // 清理骑乘状态
        forceExitRidingState(player);
    }
}
