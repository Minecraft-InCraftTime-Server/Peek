package ict.minesunshineone.peek;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class PrivacyManager {

    private final PeekPlugin plugin;
    private final File privacyFile;
    private final YamlConfiguration privacyConfig;
    private final Set<UUID> privateModePlayers = new HashSet<>();
    private final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    public PrivacyManager(PeekPlugin plugin) {
        this.plugin = plugin;
        this.privacyFile = new File(plugin.getDataFolder(), "privacy.yml");
        this.privacyConfig = YamlConfiguration.loadConfiguration(privacyFile);
        loadPrivacySettings();

        // Schedule cleanup task
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin,
                task -> cleanupExpiredRequests(),
                30, 30, TimeUnit.SECONDS);
    }

    private void loadPrivacySettings() {
        List<String> uuids = privacyConfig.getStringList("private_mode_players");
        uuids.forEach(uuid -> privateModePlayers.add(UUID.fromString(uuid)));
    }

    public void savePrivacySettings() {
        List<String> uuids = privateModePlayers.stream()
                .map(UUID::toString)
                .toList();
        privacyConfig.set("private_mode_players", uuids);
        try {
            privacyConfig.save(privacyFile);
        } catch (IOException e) {
            plugin.getLogger().warning(String.format("无法保存私人模式设置: %s", e.getMessage()));
        }
    }

    public boolean isPrivateMode(Player player) {
        return privateModePlayers.contains(player.getUniqueId());
    }

    public void togglePrivateMode(Player player) {
        if (!player.hasPermission("peek.privacy")) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (privateModePlayers.contains(uuid)) {
            privateModePlayers.remove(uuid);
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("privacy-mode-disabled")));
        } else {
            privateModePlayers.add(uuid);
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("privacy-mode-enabled")));
        }
        savePrivacySettings();
    }

    public void sendPeekRequest(Player requester, Player target) {
        if (!plugin.getCooldownManager().checkPeekCooldown(requester)) {
            int remaining = plugin.getCooldownManager().getRemainingPeekCooldown(requester);
            requester.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("cooldown", "time", String.valueOf(remaining))));
            return;
        }
        UUID targetUUID = target.getUniqueId();
        if (pendingRequests.containsKey(targetUUID)) {
            requester.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("request-already-pending")));
            return;
        }

        // 清理过期请求
        cleanupExpiredRequests();

        PendingRequest request = new PendingRequest(requester, target);
        pendingRequests.put(targetUUID, request);

        // 发送带按钮的请求消息给目标玩家
        Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(
                plugin.getMessages().get("peek-request", "player", requester.getName()))
                .append(Component.text(" "))
                .append(Component.text("【同意】")
                        .color(TextColor.color(0x55FF55))
                        .clickEvent(ClickEvent.runCommand("/peek accept"))
                        .hoverEvent(HoverEvent.showText(Component.text("点击同意"))))
                .append(Component.text(" "))
                .append(Component.text("【拒绝】")
                        .color(TextColor.color(0xFF5555))
                        .clickEvent(ClickEvent.runCommand("/peek deny"))
                        .hoverEvent(HoverEvent.showText(Component.text("点击拒绝"))));

        target.sendMessage(message);
        // 给请求者发送消息
        requester.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(plugin.getMessages().get("request-sent", "player", target.getName())));

        playSound(target, "request");
        playSound(requester, "request");

        // 设置超时
        int timeoutSeconds = plugin.getConfig().getInt("privacy.request-timeout", 30);
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, (task) -> {
            if (pendingRequests.remove(targetUUID, request)) {
                plugin.getServer().getRegionScheduler().execute(plugin, target.getLocation(), () -> {
                    requester.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(plugin.getMessages().get("request-timeout")));
                    target.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(plugin.getMessages().get("request-timeout-target")));
                    if (plugin.getConfig().getBoolean("privacy.cooldown.enabled", true)) {
                        plugin.getCooldownManager().setCooldownAfterPeek(requester);
                    }
                });
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    public void handleAccept(Player target) {
        PendingRequest request = pendingRequests.remove(target.getUniqueId());
        if (request != null && request.requester().isOnline()) {
            forcePeek(request.requester(), target);
            playSound(target, "accept");
            playSound(request.requester(), "accept");
        } else {
            target.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("no-pending-request")));
        }
    }

    private void forcePeek(Player peeker, Player target) {
        UUID targetUUID = target.getUniqueId();
        boolean wasPrivate = privateModePlayers.contains(targetUUID);
        if (wasPrivate) {
            privateModePlayers.remove(targetUUID);
        }

        try {
            plugin.getPeekCommand().handlePeek(peeker, target.getName());
        } finally {
            if (wasPrivate) {
                privateModePlayers.add(targetUUID);
            }
        }
    }

    public void handleDeny(Player target) {
        PendingRequest request = pendingRequests.remove(target.getUniqueId());
        if (request != null && request.requester().isOnline()) {
            request.requester().sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("request-denied")));
            plugin.getCooldownManager().setCooldownAfterPeek(request.requester());
            playSound(target, "deny");
            playSound(request.requester(), "deny");
        } else {
            target.sendMessage(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(plugin.getMessages().get("no-pending-request")));
        }
    }

    private void cleanupExpiredRequests() {
        pendingRequests.entrySet().removeIf(entry -> {
            Player requester = entry.getValue().requester();
            Player target = entry.getValue().target();
            return !requester.isOnline() || !target.isOnline();
        });
    }

    private void playSound(Player player, String configPath) {
        try {
            String soundName = plugin.getConfig().getString("privacy.sounds." + configPath);
            if (soundName != null) {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(String.format("无效的声音配置: %s", e.getMessage()));
        }
    }

    private record PendingRequest(Player requester, Player target, long timestamp) {

        PendingRequest(Player requester, Player target) {
            this(requester, target, System.currentTimeMillis());
        }
    }
}
