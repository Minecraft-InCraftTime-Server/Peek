package ict.minesunshineone.peek;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

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
        // 添加命令执行时间统计
        long startTime = System.currentTimeMillis();

        try {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessages().get("command-player-only"));
                return true;
            }

            Player player = (Player) sender;

            // 检查权限
            if (!player.hasPermission("peek.use")) {
                player.sendMessage(plugin.getMessages().get("no-permission"));
                return true;
            }

            // 检查参数
            if (args.length == 0) {
                player.sendMessage(plugin.getMessages().get("usage"));
                return true;
            }

            // 处理退出命令
            if (args[0].equalsIgnoreCase("exit")) {
                return handleExit(player);
            }

            // 处理消息发送命令
            if (args[0].equalsIgnoreCase("msg")) {
                return handleMessage(player, args);
            }

            // 处理统计命令
            if (args[0].equalsIgnoreCase("stats")) {
                return handleStats(player);
            }

            // 处理观察命令
            return handlePeek(player, args[0]);
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("执行peek命令时发生错误: %s", e.getMessage()));
            sender.sendMessage("§c执行命令时发生错误，请联系管理员查看日志。");
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
            player.sendMessage(plugin.getMessages().get("cooldown-peek", "time", String.valueOf(remaining)));
            player.playSound(player.getLocation(),
                    Sound.valueOf(plugin.getConfig().getString("sounds.cooldown", "ENTITY_VILLAGER_NO")),
                    1.0f, 1.0f);
            return true;
        }

        // 检查玩家是否已经在偷窥中
        if (peekingPlayers.containsKey(player)) {
            player.sendMessage(plugin.getMessages().get("already-peeking"));
            return true;
        }

        // 获取目标玩家
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(plugin.getMessages().get("player-not-found", "player", targetName));
            return true;
        }

        // 不能偷窥自己
        if (target == player) {
            player.sendMessage(plugin.getMessages().get("cannot-peek-self"));
            return true;
        }

        // 记录观察开始
        if (plugin.getStatistics() != null) {
            plugin.getStatistics().recordPeekStart(player, target);
        }

        // 存储开始时间
        long startTime = System.currentTimeMillis();
        PeekData peekData = new PeekData(
                player.getLocation().clone(),
                player.getGameMode(),
                target,
                startTime
        );
        peekingPlayers.put(player, peekData);

        // 切换到观察模式并传送
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(target.getLocation());
        player.sendMessage(plugin.getMessages().get("peek-start", "player", target.getName()));

        // 向目标玩家发送提示
        target.sendMessage(plugin.getMessages().get("being-peeked", "player", player.getName()));
        target.playSound(target.getLocation(),
                Sound.valueOf(plugin.getConfig().getString("sounds.start-peek", "BLOCK_NOTE_BLOCK_PLING")),
                1.0f, 1.0f);

        // 更新目标玩家的观察者数量显示
        updateActionBar(target);

        // 如果设置了最大观察时间，启动定时器
        int maxDuration = plugin.getMaxPeekDuration();
        if (maxDuration > 0) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (peekingPlayers.containsKey(player)) {
                        handleExit(player);
                        player.sendMessage(plugin.getMessages().get("time-expired"));
                    }
                }
            }.runTaskLater(plugin, maxDuration * 20L);
        }

        // 检查目标玩家权限
        if (plugin.isCheckTargetPermission() && !target.hasPermission("peek.target")) {
            player.sendMessage(plugin.getMessages().get("target-no-permission"));
            return true;
        }

        // 检查观察者数量限制
        int maxObservers = plugin.getConfig().getInt("limits.max-observers", 5);
        long currentObservers = peekingPlayers.values().stream()
                .filter(data -> data.getTargetPlayer().equals(target))
                .count();

        if (currentObservers >= maxObservers) {
            player.sendMessage(plugin.getMessages().get("too-many-observers"));
            return true;
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
        PeekData data = peekingPlayers.remove(player);
        if (data == null) {
            player.sendMessage(plugin.getMessages().get("not-peeking"));
            return true;
        }

        // 记录观察时长
        if (plugin.getStatistics() != null) {
            long duration = (System.currentTimeMillis() - data.getStartTime()) / 1000;
            plugin.getStatistics().recordPeekDuration(player, duration);
        }

        Player target = data.getTargetPlayer();
        // 恢复始状态
        player.setGameMode(data.getOriginalGameMode());
        player.teleport(data.getOriginalLocation());
        player.sendMessage(plugin.getMessages().get("peek-end"));

        // 播放结束观察的声音
        if (target.isOnline()) {
            target.playSound(target.getLocation(),
                    Sound.valueOf(plugin.getConfig().getString("sounds.end-peek", "BLOCK_NOTE_BLOCK_BASS")),
                    1.0f, 1.0f);
            // 更新目标玩家的观察者数量显示
            updateActionBar(target);
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
                target.sendActionBar(Component.text(message));
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
     * 处理玩家发送请消息
     */
    private boolean handleMessage(Player player, String[] args) {
        // 检查权限
        if (!player.hasPermission("peek.msg")) {
            player.sendMessage(plugin.getMessages().get("no-permission"));
            return true;
        }

        // 检查冷却时间
        if (!plugin.getCooldownManager().checkMsgCooldown(player)) {
            int remaining = plugin.getCooldownManager().getRemainingMsgCooldown(player);
            player.sendMessage(plugin.getMessages().get("cooldown-msg", "time", String.valueOf(remaining)));
            player.playSound(player.getLocation(),
                    Sound.valueOf(plugin.getConfig().getString("sounds.cooldown", "ENTITY_VILLAGER_NO")),
                    1.0f, 1.0f);
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(plugin.getMessages().get("msg-no-message"));
            return true;
        }

        // 拼接消息内容
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        // 创建可点击的消息
        TextComponent text = new TextComponent(
                "§e" + player.getName() + " §e邀请你观察: §f" + message + " §7[点击观察]"
        );
        text.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/peek " + player.getName()));
        text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text("§7点击观察 " + player.getName())));

        // 广播消息并播放声音
        plugin.getServer().getOnlinePlayers().forEach(p -> {
            p.sendMessage(Component.text()
                    .append(Component.text("§e" + player.getName()))
                    .append(Component.text(" §e邀请你观察: "))
                    .append(Component.text("§f" + message))
                    .append(Component.text(" §7[点击观察]")
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/peek " + player.getName()))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("§7点击观察 " + player.getName()))))
                    .build());
            if (p != player) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            }
        });
        return true;
    }

    /**
     * 处理查看统计信息的命令
     */
    private boolean handleStats(Player player) {
        if (plugin.getStatistics() == null) {
            player.sendMessage("§c统计功能未启用！");
            return true;
        }

        Statistics.PlayerStats stats = plugin.getStatistics().getStats(player);
        player.sendMessage(plugin.getMessages().get("stats-self",
                "peek_count", String.valueOf(stats.getPeekCount()),
                "peeked_count", String.valueOf(stats.getPeekedCount()),
                "peek_duration", String.valueOf(stats.getPeekDuration() / 60)
        ));
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
            completions.add("msg");
            completions.add("stats");  // 添加统计命令补全
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
}
