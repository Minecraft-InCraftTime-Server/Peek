package ict.minesunshineone.peek.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.util.FoliaSchedulerUtil;

public class PeekTargetHandler {

    private final PeekPlugin plugin;

    private record RandomTarget(Player player, String name) {
    }

    public PeekTargetHandler(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    public void requestPeek(Player peeker, String targetName) {
        if (peeker == null) {
            return;
        }

        FoliaSchedulerUtil.runOnEntity(plugin, peeker, () -> requestPeekOnPeekerThread(peeker, targetName), null);
    }

    private void requestPeekOnPeekerThread(Player peeker, String targetName) {
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            plugin.getMessages().send(peeker, "player-not-found", "player", targetName);
            return;
        }

        if (peeker.equals(target)) {
            plugin.getMessages().send(peeker, "cannot-peek-self");
            return;
        }

        if (plugin.getStateHandler().getActivePeeks().containsKey(peeker.getUniqueId())) {
            plugin.getMessages().send(peeker, "already-peeking");
            return;
        }

        if (peeker.isDead()) {
            plugin.getMessages().send(peeker, "cannot-peek-while-dead");
            return;
        }

        if (plugin.getCooldownManager().isOnCooldown(peeker)) {
            plugin.getMessages().send(peeker, "cooldown-peek", "time",
                    String.valueOf(plugin.getCooldownManager().getRemainingCooldown(peeker)));
            return;
        }

        boolean bypassPrivacy = peeker.hasPermission("peek.bypass");
        boolean bypassRequestCooldown = peeker.hasPermission("peek.nocooldown");
        String peekerName = peeker.getName();
        FoliaSchedulerUtil.runOnEntity(plugin, target,
                () -> validateTargetAndStart(peeker, target, targetName, peekerName, bypassPrivacy,
                        bypassRequestCooldown),
                () -> sendToPeeker(peeker,
                        () -> plugin.getMessages().send(peeker, "player-not-found", "player", targetName)));
    }

    private void validateTargetAndStart(Player peeker, Player target, String targetName, String peekerName,
            boolean bypassPrivacy, boolean bypassRequestCooldown) {
        if (!target.isOnline()) {
            sendToPeeker(peeker,
                    () -> plugin.getMessages().send(peeker, "player-not-found", "player", targetName));
            return;
        }

        if (plugin.getStateHandler().getActivePeeks().containsKey(target.getUniqueId())) {
            sendToPeeker(peeker, () -> plugin.getMessages().send(peeker, "target-is-peeking"));
            return;
        }

        // 检查隐私模式，但有 bypass 权限的玩家可以无视
        if (plugin.getPrivacyManager().isPrivateMode(target) && !bypassPrivacy) {
            plugin.getPrivacyManager().sendPeekRequest(peeker, target, peekerName, target.getName(),
                    bypassRequestCooldown);
            return;
        }

        plugin.getStateHandler().startPeek(peeker, target, target.getName());
    }

    public void requestRandomPeek(Player peeker) {
        if (peeker == null) {
            return;
        }

        FoliaSchedulerUtil.runOnEntity(plugin, peeker, () -> requestRandomPeekOnPeekerThread(peeker), null);
    }

    private void requestRandomPeekOnPeekerThread(Player peeker) {
        if (plugin.getStateHandler().getActivePeeks().containsKey(peeker.getUniqueId())) {
            plugin.getMessages().send(peeker, "already-peeking");
            return;
        }

        if (peeker.isDead()) {
            plugin.getMessages().send(peeker, "cannot-peek-while-dead");
            return;
        }

        if (plugin.getCooldownManager().isOnCooldown(peeker)) {
            plugin.getMessages().send(peeker, "cooldown-peek", "time",
                    String.valueOf(plugin.getCooldownManager().getRemainingCooldown(peeker)));
            return;
        }

        List<Player> candidatesToCheck = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        candidatesToCheck.remove(peeker);

        if (candidatesToCheck.isEmpty()) {
            plugin.getMessages().send(peeker, "no-available-random-target");
            return;
        }

        List<RandomTarget> candidates = new java.util.concurrent.CopyOnWriteArrayList<>();
        AtomicInteger pending = new AtomicInteger(candidatesToCheck.size());

        for (Player candidate : candidatesToCheck) {
            FoliaSchedulerUtil.runOnEntity(plugin, candidate, () -> {
                if (candidate.isOnline()
                        && !candidate.isDead()
                        && !plugin.getPrivacyManager().isPrivateMode(candidate)
                        && !plugin.getStateHandler().getActivePeeks().containsKey(candidate.getUniqueId())) {
                    candidates.add(new RandomTarget(candidate, candidate.getName()));
                }
                finishRandomCandidateCheck(peeker, candidates, pending);
            }, () -> finishRandomCandidateCheck(peeker, candidates, pending));
        }
    }

    private void finishRandomCandidateCheck(Player peeker, List<RandomTarget> candidates, AtomicInteger pending) {
        if (pending.decrementAndGet() != 0) {
            return;
        }

        sendToPeeker(peeker, () -> {
            if (candidates.isEmpty()) {
                plugin.getMessages().send(peeker, "no-available-random-target");
                return;
            }

            RandomTarget selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            plugin.getMessages().send(peeker, "random-peek-selected", "player", selected.name());
            plugin.getStateHandler().startPeek(peeker, selected.player(), selected.name());
        });
    }

    /**
     * 检查是否应该静默 peek（不通知目标）
     * 用于有 bypass 权限的玩家
     */
    public boolean shouldSilentPeek(Player peeker) {
        return peeker.hasPermission("peek.bypass");
    }

    private void sendToPeeker(Player peeker, Runnable action) {
        FoliaSchedulerUtil.runOnEntity(plugin, peeker, action, null);
    }
}
