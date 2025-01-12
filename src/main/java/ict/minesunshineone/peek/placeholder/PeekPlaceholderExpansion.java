package ict.minesunshineone.peek.placeholder;

import org.bukkit.entity.Player;

import ict.minesunshineone.peek.PeekPlugin;
import ict.minesunshineone.peek.manager.StatisticsManager.PlayerStats;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PeekPlaceholderExpansion extends PlaceholderExpansion {

    private final PeekPlugin plugin;

    public PeekPlaceholderExpansion(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "peek";
    }

    @Override
    public String getAuthor() {
        return "MineSunshineOne";
    }

    @Override
    public String getVersion() {
        return "${project.version}";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null || identifier == null) {
            return "";
        }

        try {
            PlayerStats stats = plugin.getStatisticsManager().getPlayerStats(player);

            return switch (identifier) {
                case "peek_count" ->
                    String.valueOf(stats.getPeekCount());
                case "peeked_count" ->
                    String.valueOf(stats.getPeekedCount());
                case "total_duration" ->
                    String.valueOf(stats.getTotalDuration());
                case "is_peeking" ->
                    String.valueOf(plugin.getStateHandler().getActivePeeks().containsKey(player));
                case "is_private" ->
                    String.valueOf(plugin.getPrivacyManager().isPrivateMode(player));
                default ->
                    null;
            };
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("处理变量时发生错误：%s", e.getMessage()));
            return "";
        }
    }
}
