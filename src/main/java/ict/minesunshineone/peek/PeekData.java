package ict.minesunshineone.peek;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 存储玩家观察模式的相关数据 包括原始位置、游戏模式和目标玩家
 */
public class PeekData {

    private final Location originalLocation;  // 玩家开始观察前的位置
    private final GameMode originalGameMode;  // 玩家开始观察前的游戏模式
    private final Player targetPlayer;        // 被观察的目标玩家
    private final long startTime;

    public PeekData(Location originalLocation, GameMode originalGameMode, Player targetPlayer, long startTime) {
        this.originalLocation = originalLocation;
        this.originalGameMode = originalGameMode;
        this.targetPlayer = targetPlayer;
        this.startTime = startTime;
    }

    /**
     * 获取玩家开始观察前的位置
     */
    public Location getOriginalLocation() {
        return originalLocation;
    }

    /**
     * 获取玩家开始观察前的游戏模式
     */
    public GameMode getOriginalGameMode() {
        return originalGameMode;
    }

    /**
     * 获取被观察的目标玩家
     */
    public Player getTargetPlayer() {
        return targetPlayer;
    }

    public long getStartTime() {
        return startTime;
    }
}
