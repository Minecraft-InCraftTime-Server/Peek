package ict.minesunshineone.peek;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * 处理/peek命令的执行器类
 */
public class PeekCommand implements CommandExecutor, TabCompleter {

    private final PeekPlugin plugin;
    private final Map<Player, PeekData> peekingPlayers = new HashMap<>();  // 存储正在观察中的玩家数据

    public PeekCommand(PeekPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("插件实例不能为空");
        }
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String[] args) {
        long startTime = System.currentTimeMillis();

        try {
            if (!(sender instanceof Player)) {
                sendMessage(sender, "command-player-only");
                return true;
            }

            Player player = (Player) sender;

            // 检查权限
            if (!player.hasPermission("peek.use")) {
                sendMessage(player, "no-permission");
                return true;
            }

            // 检查参数
            if (args.length == 0) {
                sendMessage(player, "usage");
                return true;
            }

            // 处理退出命令
            if (args[0].equalsIgnoreCase("exit")) {
                return handleExit(player);
            }

            // 处理统计命令
            if (args[0].equalsIgnoreCase("stats")) {
                return handleStats(player);
            }

            // 处理观察命令
            return handlePeek(player, args[0]);
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("执行peek命令时发生错误: %s", e.getMessage()));
            sendMessage(sender, "command-error");
        } finally {
            if (plugin.isDebugEnabled()) {
                long duration = System.currentTimeMillis() - startTime;
                plugin.getLogger().info(String.format("命令执行耗时: %dms", duration));
            }
        }
        return true;
    }

    /**
     * 处理玩家开始观察的逻辑
     *
     * @param player 执行命令的玩家
     * @param targetName 目标玩家名
     * @return 是否执行成功
     */
    private boolean handlePeek(Player player, String targetName) {
        // 检查冷却时间
        if (!plugin.getCooldownManager().checkPeekCooldown(player)) {
            int remaining = plugin.getCooldownManager().getRemainingPeekCooldown(player);
            sendMessage(player, "cooldown-peek", "time", String.valueOf(remaining));
            player.playSound(player.getLocation(),
                    Sound.valueOf(plugin.getConfig().getString("sounds.cooldown", "ENTITY_VILLAGER_NO")),
                    1.0f, 1.0f);
            return true;
        }

        // 检查玩家是否已经在偷窥中
        if (peekingPlayers.containsKey(player)) {
            sendMessage(player, "already-peeking");
            return true;
        }

        // 获取目标玩家
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            sendMessage(player, "player-not-found", "player", targetName);
            return true;
        }

        // 不能偷窥自己
        if (target == player) {
            sendMessage(player, "cannot-peek-self");
            return true;
        }

        // 检查目标玩家权限
        if (plugin.isCheckTargetPermission() && !target.hasPermission("peek.target")) {
            sendMessage(player, "target-no-permission");
            return true;
        }

        // 检查观察者数量限制
        int maxObservers = plugin.getConfig().getInt("limits.max-observers", 5);
        long currentObservers = peekingPlayers.values().stream()
                .filter(data -> data.getTargetPlayer().equals(target))
                .count();

        if (currentObservers >= maxObservers) {
            sendMessage(player, "too-many-observers");
            return true;
        }

        // 记录观察开始
        if (plugin.getStatistics() != null) {
            plugin.getStatistics().recordPeekStart(player, target);
        }

        // 存储开始时间和数据
        long startTime = System.currentTimeMillis();
        PeekData peekData = new PeekData(
                player.getLocation().clone(),
                player.getGameMode(),
                target,
                startTime
        );
        peekingPlayers.put(player, peekData);

        // 在目标玩家所在区域执行传送
        plugin.getServer().getRegionScheduler().execute(plugin, target.getLocation(), () -> {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleportAsync(target.getLocation()).thenAccept(result -> {
                if (result) {
                    sendMessage(player, "peek-start", "player", target.getName());
                    sendMessage(target, "being-peeked", "player", player.getName());
                    playSound(target, "start-peek");
                    updateActionBar(target);
                } else {
                    peekingPlayers.remove(player);
                    sendMessage(player, "teleport-failed");
                }
            });
        });

        // 设置最大观察时间定时器
        if (plugin.getMaxPeekDuration() > 0) {
            long durationInMillis = plugin.getMaxPeekDuration() * 1000L;
            plugin.getServer().getAsyncScheduler().runDelayed(plugin, scheduledTask -> {
                if (peekingPlayers.containsKey(player)) {
                    plugin.getServer().getRegionScheduler().execute(plugin,
                            player.getLocation(), () -> {
                        handleExit(player);
                        sendMessage(player, "time-expired");
                    });
                }
            }, durationInMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        return true;
    }

    /**
     * 处理玩家退出观察的逻辑
     *
     * @param player 要退出观察的玩家
     * @return 是否执行成功
     */
    public boolean handleExit(Player player) {
        if (!peekingPlayers.containsKey(player)) {
            sendMessage(player, "not-peeking");
            return true;
        }

        PeekData data = peekingPlayers.get(player);
        data.setExiting(true);  // 设置退出标记
        Player target = data.getTargetPlayer();

        restorePlayerState(player, data);
        peekingPlayers.remove(player);

        sendMessage(player, "peek-end");
        if (target != null && target.isOnline()) {
            sendMessage(target, "peek-end-target", "player", player.getName());
            playSound(target, "end-peek");
        }

        updateActionBar(target);

        if (plugin.getStatistics() != null) {
            long duration = (System.currentTimeMillis() - data.getStartTime()) / 1000;
            plugin.getStatistics().recordPeekDuration(player, duration);
        }

        return true;
    }

    /**
     * 更新目标玩家的观察者数量显示
     */
    private void updateActionBar(Player target) {
        long count = peekingPlayers.values().stream()
                .filter(data -> data.getTargetPlayer().equals(target))
                .count();

        if (count > 0) {
            String message = plugin.getMessages().get("action-bar", "count", String.valueOf(count));
            if (message != null) {
                target.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacyAmpersand().deserialize(message));
            }
        }
    }

    /**
     * 获取当前所有正在观察的玩家
     *
     * @return 观察中的玩家Map
     */
    public Map<Player, PeekData> getPeekingPlayers() {
        return peekingPlayers;
    }

    /**
     * 处理查看统计信息的命令
     */
    private boolean handleStats(Player player) {
        if (plugin.getStatistics() == null) {
            sendMessage(player, "stats-disabled");
            return true;
        }

        Statistics.PlayerStats stats = plugin.getStatistics().getStats(player);
        sendMessage(player, "stats-self",
                "peek_count", String.valueOf(stats.getPeekCount()),
                "peeked_count", String.valueOf(stats.getPeekedCount()),
                "peek_duration", String.valueOf(stats.getPeekDuration() / 60)
        );
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("exit");
            completions.add("stats");
            // 添加在线玩家名称
            completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return null;
    }

    private void restorePlayerState(Player player, PeekData data) {
        plugin.getServer().getRegionScheduler().execute(plugin, player.getLocation(), () -> {
            player.teleportAsync(data.getOriginalLocation()).thenAccept(result -> {
                if (result) {
                    player.setGameMode(data.getOriginalGameMode());
                }
            });
        });
    }

    private void playSound(Player player, String soundKey) {
        if (player != null) {
            player.playSound(player.getLocation(),
                    Sound.valueOf(plugin.getConfig().getString("sounds." + soundKey, "BLOCK_NOTE_BLOCK_PLING")),
                    1.0f, 1.0f);
        }
    }

    private void sendMessage(CommandSender sender, String key, String... replacements) {
        if (sender == null || key == null) {
            return;
        }

        String message = plugin.getMessages().get(key, replacements);
        if (message == null) {
            return;
        }

        if (sender instanceof Player player) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(message));
        } else {
            sender.sendMessage(message);
        }
    }
}
