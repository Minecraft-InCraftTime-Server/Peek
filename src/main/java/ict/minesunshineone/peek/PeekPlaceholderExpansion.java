package ict.minesunshineone.peek;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PeekPlaceholderExpansion extends PlaceholderExpansion {

    private final PeekPlugin plugin;

    public PeekPlaceholderExpansion(PeekPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull
    String getIdentifier() {
        return "peek";
    }

    @Override
    public @NotNull
    String getAuthor() {
        return "MineSunshineOne";
    }

    @Override
    public @NotNull
    String getVersion() {
        return "1.0";
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        Statistics.PlayerStats stats = plugin.getStatistics().getStats(player);

        return switch (identifier) {
            case "peek_count" ->
                String.valueOf(stats.getPeekCount());
            case "peeked_count" ->
                String.valueOf(stats.getPeekedCount());
            case "peek_duration" ->
                String.format("%.1f", stats.getPeekDuration() / 60.0);
            default ->
                null;
        };
    }
}
