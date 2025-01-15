package ict.minesunshineone.peek.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;

import ict.minesunshineone.peek.PeekPlugin;

public class PeekInteractionListener implements Listener {

    private final PeekPlugin plugin;
    private final boolean blockContainerInteraction;

    public PeekInteractionListener(PeekPlugin plugin) {
        this.plugin = plugin;
        this.blockContainerInteraction = plugin.getConfig().getBoolean("limits.block-container-interaction", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!blockContainerInteraction) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (plugin.getStateHandler().getActivePeeks().containsKey(player.getUniqueId())
                && player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
        }
    }
}
