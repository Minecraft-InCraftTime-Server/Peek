package ict.minesunshineone.peek.manager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import ict.minesunshineone.peek.PeekPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PrivacyManager {

    private final PeekPlugin plugin;
    private final Map<UUID, Map<UUID, ScheduledTask>> pendingRequests = new HashMap<>();
    private final Map<String, Long> requestCooldowns = new HashMap<>();

    private final int requestTimeout;
    private final boolean cooldownEnabled;
    private final int cooldownDuration;
    
    // PersistentData Key for privacy mode
    private final NamespacedKey PRIVACY_MODE_KEY;

    public PrivacyManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.requestTimeout = plugin.getConfig().getInt("privacy.request-timeout", 30);
        this.cooldownEnabled = plugin.getConfig().getBoolean("privacy.cooldown.enabled", true);
        this.cooldownDuration = plugin.getConfig().getInt("privacy.cooldown.duration", 60);
        this.PRIVACY_MODE_KEY = new NamespacedKey(plugin, "privacy_mode");
        
        plugin.getLogger().info("隐私管理器已初始化，使用 PersistentData 存储隐私模式设置");
    }

    /**
     * 检查玩家是否启用了私人模式
     * 使用 PersistentData 存储，支持跨服同步
     */
    public boolean isPrivateMode(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        
        return player.getPersistentDataContainer()
                .getOrDefault(PRIVACY_MODE_KEY, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    /**
     * 切换玩家的私人模式状态
     * 使用 PersistentData 存储，支持跨服同步
     */
    public void togglePrivateMode(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        
        boolean currentMode = isPrivateMode(player);
        
        if (currentMode) {
            // 禁用私人模式
            player.getPersistentDataContainer().set(PRIVACY_MODE_KEY, PersistentDataType.BYTE, (byte) 0);
            plugin.getMessages().send(player, "privacy-mode-disabled");
            
            // 取消所有待处理的请求
            cancelAllPendingRequests(player);
        } else {
            // 启用私人模式
            player.getPersistentDataContainer().set(PRIVACY_MODE_KEY, PersistentDataType.BYTE, (byte) 1);
            plugin.getMessages().send(player, "privacy-mode-enabled");
        }
        
        plugin.getLogger().info(String.format("玩家 %s %s了私人模式", 
                player.getName(), currentMode ? "禁用" : "启用"));
    }

    /**
     * 取消玩家的所有待处理请求
     */
    private synchronized void cancelAllPendingRequests(Player player) {
        UUID playerUuid = player.getUniqueId();
        
        // 取消作为目标的所有请求
        Map<UUID, ScheduledTask> targetRequests = pendingRequests.get(playerUuid);
        if (targetRequests != null) {
            for (Map.Entry<UUID, ScheduledTask> entry : targetRequests.entrySet()) {
                entry.getValue().cancel();
                Player requester = plugin.getServer().getPlayer(entry.getKey());
                if (requester != null && requester.isOnline()) {
                    plugin.getMessages().send(requester, "request-cancel");
                }
            }
            pendingRequests.remove(playerUuid);
        }
        
        // 取消作为请求者的所有请求
        for (Map.Entry<UUID, Map<UUID, ScheduledTask>> entry : pendingRequests.entrySet()) {
            ScheduledTask task = entry.getValue().remove(playerUuid);
            if (task != null) {
                task.cancel();
                Player target = plugin.getServer().getPlayer(entry.getKey());
                if (target != null && target.isOnline()) {
                    plugin.getMessages().send(target, "request-cancel");
                }
            }
        }
    }

    public void sendPeekRequest(Player peeker, Player target) {
        // 添加请求前的检查
        if (!target.isOnline()) {
            plugin.getMessages().send(peeker, "target-offline");
            return;
        }

        if (plugin.getStateHandler().getActivePeeks().containsKey(target.getUniqueId())) {
            plugin.getMessages().send(peeker, "target-is-peeking");
            return;
        }

        UUID peekerUuid = peeker.getUniqueId();
        UUID targetUuid = target.getUniqueId();

        // 检查冷却
        if (isOnRequestCooldown(peeker, target)) {
            plugin.getMessages().send(peeker, "request-cooldown",
                    "time", String.valueOf(getRemainingRequestCooldown(peeker, target)));
            return;
        }

        // 检查是否已有待处理请求
        if (hasPendingRequest(peeker, target)) {
            plugin.getMessages().send(peeker, "request-already-pending");
            return;
        }

        // 发送请求
        plugin.getMessages().send(target, "peek-request", "player", peeker.getName());
        plugin.getMessages().send(peeker, "request-sent", "player", target.getName());

        // 播放声音
        playSound(target, "privacy.sounds.request");

        // 设置超时任务
        ScheduledTask timeoutTask = plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin,
                task -> {
                    if (removePendingRequest(peeker, target)) {
                        plugin.getMessages().send(peeker, "request-timeout");
                        plugin.getMessages().send(target, "request-timeout-target");
                        setRequestCooldown(peeker, target);
                    }
                },
                requestTimeout * 20L);

        // 保存请求
        synchronized (this) {
            pendingRequests.computeIfAbsent(targetUuid, k -> new HashMap<>())
                    .put(peekerUuid, timeoutTask);
        }
    }

    public void handleRequestResponse(Player target, Player peeker, boolean accepted) {
        if (!hasPendingRequest(peeker, target)) {
            plugin.getMessages().send(target, "no-pending-request");
            return;
        }

        removePendingRequest(peeker, target);

        if (accepted) {
            playSound(peeker, "privacy.sounds.accept");
            plugin.getStateHandler().startPeek(peeker, target);
        } else {
            playSound(peeker, "privacy.sounds.deny");
            plugin.getMessages().send(peeker, "request-denied");
            setRequestCooldown(peeker, target);
        }
    }

    private synchronized boolean hasPendingRequest(Player peeker, Player target) {
        Map<UUID, ScheduledTask> targetRequests = pendingRequests.get(target.getUniqueId());
        return targetRequests != null && targetRequests.containsKey(peeker.getUniqueId());
    }

    public synchronized boolean removePendingRequest(Player peeker, Player target) {
        Map<UUID, ScheduledTask> targetRequests = pendingRequests.get(target.getUniqueId());
        if (targetRequests != null) {
            ScheduledTask task = targetRequests.remove(peeker.getUniqueId());
            if (task != null) {
                task.cancel();
                if (targetRequests.isEmpty()) {
                    pendingRequests.remove(target.getUniqueId());
                }
                return true;
            }
        }
        return false;
    }

    private synchronized void setRequestCooldown(Player peeker, Player target) {
        if (cooldownEnabled) {
            String key = getCooldownKey(peeker.getUniqueId(), target.getUniqueId());
            requestCooldowns.put(key, System.currentTimeMillis());
        }
    }

    private synchronized boolean isOnRequestCooldown(Player peeker, Player target) {
        if (!cooldownEnabled || peeker.hasPermission("peek.nocooldown")) {
            return false;
        }

        String key = getCooldownKey(peeker.getUniqueId(), target.getUniqueId());
        Long lastRequest = requestCooldowns.get(key);
        if (lastRequest == null) {
            return false;
        }

        if (System.currentTimeMillis() - lastRequest >= cooldownDuration * 1000L) {
            // 冷却已过期，顺手清理该键，避免 requestCooldowns 无限增长
            requestCooldowns.remove(key);
            return false;
        }
        return true;
    }

    private synchronized int getRemainingRequestCooldown(Player peeker, Player target) {
        if (!cooldownEnabled || peeker.hasPermission("peek.nocooldown")) {
            return 0;
        }

        String key = getCooldownKey(peeker.getUniqueId(), target.getUniqueId());
        Long lastRequest = requestCooldowns.get(key);
        if (lastRequest == null) {
            return 0;
        }

        long remaining = (lastRequest + cooldownDuration * 1000L - System.currentTimeMillis()) / 1000L;
        return Math.max(0, (int) remaining);
    }

    private String getCooldownKey(UUID peeker, UUID target) {
        return peeker.toString() + "_" + target.toString();
    }

    private void playSound(Player player, String configPath) {
        String soundName = plugin.getConfig().getString(configPath);
        if (soundName != null) {
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(String.format("无效的声音名称: %s", soundName));
            }
        }
    }

    public synchronized void cancelAllRequests(Player player) {
        UUID playerUuid = player.getUniqueId();

        // 取消作为目标的请求
        Map<UUID, ScheduledTask> targetRequests = pendingRequests.remove(playerUuid);
        if (targetRequests != null) {
            targetRequests.values().forEach(ScheduledTask::cancel);
        }

        // 取消作为请求者的请求
        pendingRequests.values().forEach(requests -> {
            ScheduledTask task = requests.remove(playerUuid);
            if (task != null) {
                task.cancel();
            }
        });

        // 清理空的映射
        pendingRequests.values().removeIf(Map::isEmpty);

        // 清理该玩家相关的请求冷却记录，避免离线玩家的键长期堆积
        String uuidStr = playerUuid.toString();
        requestCooldowns.keySet().removeIf(key -> key.contains(uuidStr));
    }

    public synchronized void handleAccept(Player player) {
        // 获取最近的请求者
        UUID playerUuid = player.getUniqueId();
        Map<UUID, ScheduledTask> requests = pendingRequests.get(playerUuid);
        if (requests == null || requests.isEmpty()) {
            plugin.getMessages().send(player, "no-pending-request");
            return;
        }

        // 获取第一个请求者
        Map.Entry<UUID, ScheduledTask> entry = requests.entrySet().iterator().next();
        Player peeker = plugin.getServer().getPlayer(entry.getKey());
        if (peeker == null || !peeker.isOnline()) {
            removePendingRequest(peeker, player);
            plugin.getMessages().send(player, "request-expired");
            return;
        }

        handleRequestResponse(player, peeker, true);
    }

    public synchronized void handleDeny(Player player) {
        // 获取最近的请求者
        UUID playerUuid = player.getUniqueId();
        Map<UUID, ScheduledTask> requests = pendingRequests.get(playerUuid);
        if (requests == null || requests.isEmpty()) {
            plugin.getMessages().send(player, "no-pending-request");
            return;
        }

        // 获取第一个请求者
        Map.Entry<UUID, ScheduledTask> entry = requests.entrySet().iterator().next();
        Player peeker = plugin.getServer().getPlayer(entry.getKey());
        if (peeker != null && peeker.isOnline()) {
            handleRequestResponse(player, peeker, false);
        } else {
            removePendingRequest(peeker, player);
            plugin.getMessages().send(player, "request-expired");
        }
    }

    /**
     * 玩家死亡时，线程安全地取消其作为请求者发出的所有观察请求，并通知双方。
     * 直接在锁内操作，避免对外暴露内部可变映射导致的并发问题。
     */
    public synchronized void cancelRequestsByPeekerOnDeath(Player peeker) {
        UUID peekerUuid = peeker.getUniqueId();
        Iterator<Map.Entry<UUID, Map<UUID, ScheduledTask>>> it = pendingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Map<UUID, ScheduledTask>> entry = it.next();
            Map<UUID, ScheduledTask> requests = entry.getValue();
            ScheduledTask task = requests.remove(peekerUuid);
            if (task == null) {
                continue;
            }
            task.cancel();
            plugin.getMessages().send(peeker, "request-cancelled-death");
            Player target = plugin.getServer().getPlayer(entry.getKey());
            if (target != null && target.isOnline()) {
                plugin.getMessages().send(target, "request-cancelled-death-target");
            }
            if (requests.isEmpty()) {
                it.remove();
            }
        }
    }
}
