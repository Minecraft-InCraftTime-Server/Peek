package ict.minesunshineone.peek.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;

public class PeekCommand implements CommandExecutor, TabCompleter {

    private final PeekPlugin plugin;

    public PeekCommand(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getMessages().send(sender, "command-player-only");
            return true;
        }

        if (!player.hasPermission("peek.use")) {
            plugin.getMessages().send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            plugin.getMessages().send(player, "usage");
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "exit" ->
                handleExit(player);
            case "stats" ->
                handleStats(player);
            case "privacy" ->
                handlePrivacy(player);
            case "accept" ->
                handleAccept(player);
            case "deny" ->
                handleDeny(player);
            case "self" ->
                handleSelfPeek(player);
            case "random", "r" ->
                handleRandomPeek(player);
            default ->
                handlePeek(player, args[0]);
        };
    }

    private boolean handlePeek(Player peeker, String targetName) {
        if (!plugin.getTargetHandler().canPeek(peeker, targetName)) {
            return true;
        }

        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            plugin.getMessages().send(peeker, "player-not-found", "player", targetName);
            return true;
        }

        plugin.getStateHandler().startPeek(peeker, target);
        return true;
    }

    private boolean handleExit(Player player) {
        plugin.getStateHandler().endPeek(player);
        return true;
    }

    private boolean handleStats(Player player) {
        if (!player.hasPermission("peek.stats")) {
            plugin.getMessages().send(player, "no-permission");
            return true;
        }

        plugin.getStatisticsManager().showStats(player);
        return true;
    }

    private boolean handlePrivacy(Player player) {
        plugin.getPrivacyManager().togglePrivateMode(player);
        return true;
    }

    private boolean handleAccept(Player player) {
        plugin.getPrivacyManager().handleAccept(player);
        return true;
    }

    private boolean handleDeny(Player player) {
        plugin.getPrivacyManager().handleDeny(player);
        return true;
    }

    private boolean handleSelfPeek(Player player) {
        // 检查玩家是否有权限
        if (!player.hasPermission("peek.self")) {
            plugin.getMessages().send(player, "no-permission");
            return true;
        }

        // 检查玩家是否已经在peek状态
        if (plugin.getStateHandler().getActivePeeks().containsKey(player.getUniqueId())) {
            plugin.getMessages().send(player, "already-peeking");
            return true;
        }

        // 检查玩家是否死亡
        if (player.isDead()) {
            plugin.getMessages().send(player, "cannot-peek-while-dead");
            return true;
        }

        // 检查冷却时间
        if (plugin.getCooldownManager().isOnCooldown(player)) {
            plugin.getMessages().send(player, "cooldown-peek", "time",
                    String.valueOf(plugin.getCooldownManager().getRemainingCooldown(player)));
            return true;
        }

        // 开始self peek
        plugin.getStateHandler().startSelfPeek(player);
        return true;
    }

    private boolean handleRandomPeek(Player player) {
        // 检查玩家是否已经在peek状态
        if (plugin.getStateHandler().getActivePeeks().containsKey(player.getUniqueId())) {
            plugin.getMessages().send(player, "already-peeking");
            return true;
        }

        // 检查玩家是否死亡
        if (player.isDead()) {
            plugin.getMessages().send(player, "cannot-peek-while-dead");
            return true;
        }

        // 检查冷却时间
        if (plugin.getCooldownManager().isOnCooldown(player)) {
            plugin.getMessages().send(player, "cooldown-peek", "time",
                    String.valueOf(plugin.getCooldownManager().getRemainingCooldown(player)));
            return true;
        }

        // 获取所有可用的目标玩家（排除自己、私密模式玩家、死亡玩家）
        // 注意：允许 peek 正在 peek 别人的玩家
        List<Player> availableTargets = plugin.getServer().getOnlinePlayers().stream()
                .filter(p -> !p.equals(player)) // 排除自己
                .filter(p -> !plugin.getPrivacyManager().isPrivateMode(p)) // 排除私密模式玩家
                .filter(p -> !p.isDead()) // 排除死亡玩家
                .collect(Collectors.toList());

        if (availableTargets.isEmpty()) {
            plugin.getMessages().send(player, "no-available-random-target");
            return true;
        }

        // 随机选择一个目标
        Random random = new Random();
        Player target = availableTargets.get(random.nextInt(availableTargets.size()));

        // 发送随机选择提示
        plugin.getMessages().send(player, "random-peek-selected", "player", target.getName());

        // 开始 peek
        plugin.getStateHandler().startPeek(player, target);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("exit");
            completions.add("stats");
            completions.add("privacy");
            completions.add("accept");
            completions.add("deny");
            completions.add("self");
            completions.add("random");
            completions.add("r");

            if (sender.hasPermission("peek.use")) {
                plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> !name.equals(sender.getName()))
                        .forEach(completions::add);
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
