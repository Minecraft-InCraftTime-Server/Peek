package ict.minesunshineone.peek.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class PrivacyManager {

    private final PeekPlugin plugin;
    private final Set<UUID> privateModeUsers = new HashSet<>();
    private final Map<UUID, Map<UUID, ScheduledTask>> pendingRequests = new HashMap<>();
    private final Map<String, Long> requestCooldowns = new HashMap<>();

    private final int requestTimeout;
    private final boolean cooldownEnabled;
    private final int cooldownDuration;

    private final File privacyFile;
    private final YamlConfiguration privacyConfig;

    public PrivacyManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.requestTimeout = plugin.getConfig().getInt("privacy.request-timeout", 30);
        this.cooldownEnabled = plugin.getConfig().getBoolean("privacy.cooldown.enabled", true);
        this.cooldownDuration = plugin.getConfig().getInt("privacy.cooldown.duration", 120);
        this.privacyFile = new File(plugin.getDataFolder(), "privacy.yml");
        this.privacyConfig = YamlConfiguration.loadConfiguration(privacyFile);
        loadPrivacyStates();
    }

    private void loadPrivacyStates() {
        List<String> uuids = privacyConfig.getStringList("private_mode_players");
        privateModeUsers.clear();
        for (String uuid : uuids) {
            try {
                privateModeUsers.add(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning(String.format("无效的UUID格式: %s", uuid));
            }
        }
    }

    public void savePrivacyStates() {
        List<String> uuids = privateModeUsers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        privacyConfig.set("private_mode_players", uuids);
        try {
            privacyConfig.save(privacyFile);
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("无法保存私人模式状态: %s", e.getMessage()));
        }
    }

    public boolean isPrivateMode(Player player) {
        return privateModeUsers.contains(player.getUniqueId());
    }

    public void togglePrivateMode(Player player) {
        UUID uuid = player.getUniqueId();
        if (privateModeUsers.contains(uuid)) {
            privateModeUsers.remove(uuid);
            plugin.getMessages().send(player, "privacy-mode-disabled");
        } else {
            privateModeUsers.add(uuid);
            plugin.getMessages().send(player, "privacy-mode-enabled");
        }
        savePrivacyStates();
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
        ScheduledTask timeoutTask = plugin.getServer().getRegionScheduler().runDelayed(plugin,
                target.getLocation(),
                task -> {
                    if (removePendingRequest(peeker, target)) {
                        plugin.getMessages().send(peeker, "request-timeout");
                        plugin.getMessages().send(target, "request-timeout-target");
                        setRequestCooldown(peeker, target);
                    }
                },
                requestTimeout * 20L);

        // 保存请求
        pendingRequests.computeIfAbsent(targetUuid, k -> new HashMap<>())
                .put(peekerUuid, timeoutTask);
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

    private boolean hasPendingRequest(Player peeker, Player target) {
        Map<UUID, ScheduledTask> targetRequests = pendingRequests.get(target.getUniqueId());
        return targetRequests != null && targetRequests.containsKey(peeker.getUniqueId());
    }

    private boolean removePendingRequest(Player peeker, Player target) {
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

    private void setRequestCooldown(Player peeker, Player target) {
        if (cooldownEnabled) {
            String key = getCooldownKey(peeker.getUniqueId(), target.getUniqueId());
            requestCooldowns.put(key, System.currentTimeMillis());
        }
    }

    private boolean isOnRequestCooldown(Player peeker, Player target) {
        if (!cooldownEnabled || peeker.hasPermission("peek.nocooldown")) {
            return false;
        }

        String key = getCooldownKey(peeker.getUniqueId(), target.getUniqueId());
        Long lastRequest = requestCooldowns.get(key);
        if (lastRequest == null) {
            return false;
        }

        return System.currentTimeMillis() - lastRequest < cooldownDuration * 1000L;
    }

    private int getRemainingRequestCooldown(Player peeker, Player target) {
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

    public void cancelAllRequests(Player player) {
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
    }

    public void handleAccept(Player player) {
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

    public void handleDeny(Player player) {
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
}
